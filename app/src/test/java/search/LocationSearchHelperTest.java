package search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.db.GeometricWeatherDatabase;
import wangdaye.com.geometricweather.search.LocationSearchHelper;
import wangdaye.com.geometricweather.settings.SettingsManager;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoGeocodingApi;

/**
 * Place search is the app's, not a weather source's. Two things have to hold, and neither is
 * visible from the network layer alone:
 *
 * 1. **Every result carries the source selected in the settings.** Search used to be run by the
 *    weather sources themselves, so a result was stamped with whichever provider happened to find
 *    it — and since a location is keyed {@code cityId & weatherSource}, adding it stranded that
 *    place on a source the rest of the app was not using.
 * 2. **A Chinese query is answered from the bundled city table first.** Measured, not assumed: the
 *    geocoder finds nothing at all for 舒城 and ranks 长沙 behind three same-named villages when it
 *    is queried in Chinese, while the table holds every prefecture and district with exact
 *    coordinates. The geocoder still covers everything else — and a Chinese query the table misses.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class LocationSearchHelperTest {

    private Context mContext;
    private MockWebServer mServer;
    private LocationSearchHelper mHelper;

    @Before
    public void setUp() throws Exception {
        resetDatabaseSingletons();

        mContext = ApplicationProvider.getApplicationContext();

        mServer = new MockWebServer();
        mServer.start();

        OpenMeteoGeocodingApi api = new Retrofit.Builder()
                .baseUrl(mServer.url("/").toString())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenMeteoGeocodingApi.class);
        mHelper = new LocationSearchHelper(api);
    }

    @After
    public void tearDown() throws IOException {
        mServer.shutdown();
    }

    /**
     * The whole point of the change: the search no longer decides what supplies the weather.
     * A result must come back on the source the settings name, whichever tier produced it.
     */
    @Test
    public void everyResultCarriesTheSelectedSource() {
        SettingsManager.getInstance(mContext).setWeatherSource(WeatherSource.XIAOMI);
        enqueue("openmeteo/geocoding_changsha.json");

        Recording recording = search("Changsha");

        assertTrue(recording.succeeded);
        assertFalse(recording.results.isEmpty());
        for (Location location : recording.results) {
            assertEquals("a search result must not pick its own source",
                    WeatherSource.XIAOMI, location.getWeatherSource());
        }
    }

    /** Tier one. 舒城 is in the table and absent from the geocoder, so the order is load-bearing. */
    @Test
    public void aChineseQueryIsAnsweredFromTheBundledTableWithoutAskingTheGeocoder() {
        SettingsManager.getInstance(mContext).setWeatherSource(WeatherSource.COMPOSITE);

        Recording recording = search("舒城");

        assertTrue(recording.succeeded);
        assertEquals(0, mServer.getRequestCount());

        Location shucheng = recording.results.get(0);
        assertEquals("101221507", shucheng.getCityId());
        assertEquals("舒城", shucheng.getDistrict());
        assertEquals(WeatherSource.COMPOSITE, shucheng.getWeatherSource());
        assertEquals(31.462849f, shucheng.getLatitude(), 0.0001f);
        assertEquals(116.94409f, shucheng.getLongitude(), 0.0001f);
    }

    /** Tier two catches what the table does not hold — the table is China-only. */
    @Test
    public void aChineseQueryTheTableDoesNotKnowFallsThroughToTheGeocoder() {
        enqueue("openmeteo/geocoding_changsha.json");

        Recording recording = search("巴黎");

        assertTrue(recording.succeeded);
        assertEquals("the geocoder must be asked when the table has nothing",
                1, mServer.getRequestCount());
    }

    /** Names, coordinates and the real time zone survive the trip. */
    @Test
    public void aGeocodedPlaceKeepsItsNameCoordinatesAndZone() {
        enqueue("openmeteo/geocoding_changsha.json");

        Recording recording = search("Changsha");

        Location first = recording.results.get(0);
        assertEquals("长沙市", first.getCityName(mContext));
        assertEquals("湖南", first.getProvince());
        assertEquals(28.19874f, first.getLatitude(), 0.0001f);
        assertEquals(112.97087f, first.getLongitude(), 0.0001f);
        assertEquals("Asia/Shanghai", first.getTimeZone().getID());
        assertTrue(first.isChina());

        // The third entry is a village whose prefecture carries a different name, so the place's
        // own name takes the district slot and is what the list shows.
        Location village = recording.results.get(2);
        assertEquals("长沙", village.getCityName(mContext));
        assertEquals("重庆市", village.getCity());
    }

    /** A miss comes back as {"generationtime_ms": ...} with no results key at all. */
    @Test
    public void aQueryThatMatchesNothingIsAFailedSearch() {
        enqueue("openmeteo/geocoding_empty.json");

        Recording recording = search("Zzzzznotaplace");

        assertTrue(recording.failed);
        assertTrue(recording.results.isEmpty());
    }

    /**
     * The caller sets LiveData from these callbacks, and LiveData.setValue() throws off the main
     * thread — a crash this project has already shipped once on a sibling path.
     */
    @Test
    public void callbacksArriveOnTheMainThread() {
        enqueue("openmeteo/geocoding_changsha.json");
        assertTrue(search("Changsha").onMainThread);

        enqueue("openmeteo/geocoding_empty.json");
        assertTrue(search("Zzzzznotaplace").onMainThread);
    }

    // ---- harness ----

    private Recording search(String query) {
        Recording recording = new Recording();
        mHelper.search(mContext, query, recording);
        awaitOnMainLooper(() -> recording.done, 20_000);
        return recording;
    }

    private void enqueue(String fixture) {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody(read(fixture)));
    }

    private String read(String fixture) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fixture)) {
            assertNotNull("fixture missing: " + fixture, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The callbacks land on the main looper, which under Robolectric is the very thread running the
     * test — blocking on a latch here would deadlock, so drive the looper while waiting instead.
     */
    private static void awaitOnMainLooper(BooleanSupplier done, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (done.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("no callback arrived within " + timeoutMillis + "ms");
    }

    /**
     * Both helpers are process singletons while Robolectric rebuilds the app's data directory
     * between test methods, so a connection opened by one test points at a file the next no longer
     * has — and using it takes the whole test JVM down. Close and drop them so each method opens
     * its own.
     */
    private static void resetDatabaseSingletons() throws Exception {
        Field database = GeometricWeatherDatabase.class.getDeclaredField("sInstance");
        database.setAccessible(true);
        GeometricWeatherDatabase open = (GeometricWeatherDatabase) database.get(null);
        if (open != null) {
            open.close();
        }
        database.set(null, null);

        Field helper = DatabaseHelper.class.getDeclaredField("sInstance");
        helper.setAccessible(true);
        helper.set(null, null);
    }

    private static final class Recording implements LocationSearchHelper.Callback {

        volatile boolean succeeded;
        volatile boolean failed;
        volatile boolean onMainThread;
        volatile boolean done;
        volatile List<Location> results = java.util.Collections.emptyList();

        @Override
        public void searchSucceeded(@NonNull String query, @NonNull List<Location> locationList) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            results = locationList;
            succeeded = true;
            done = true;
        }

        @Override
        public void searchFailed(@NonNull String query) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            failed = true;
            done = true;
        }
    }
}
