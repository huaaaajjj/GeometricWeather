package db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.ChineseCity;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.db.GeometricWeatherDatabase;
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

/**
 * The weather cache assumes "at least one daily entry" — an assumption held up by WeatherHelper and
 * readWeather, not by the writer. That made the write path the sharpest edge in the codebase: it
 * derived the history row from {@code dailyForecast.get(0)} with no guard, so a weather that slipped
 * past those two chokepoints did not degrade, it took the process down. (Removing the WeatherHelper
 * guard to check that its test was load-bearing killed the whole test JVM, which is how this
 * surfaced.) Storing no history for a weather that has none is the graceful answer.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class DatabaseHelperTest {

    private Context mContext;
    private Location mLocation;

    @Before
    public void setUp() throws Exception {
        // Both singletons outlive a test method while Robolectric rebuilds the app's data directory
        // between them, so a connection carried over points at a file that no longer exists.
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

        mContext = ApplicationProvider.getApplicationContext();
        mLocation = new Location(
                "beijing",
                39.9042f, 116.4074f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "北京市", "北京市", "",
                null,
                WeatherSource.OPEN_METEO,
                false, false, true
        );
    }

    /** A weather with daily entries stores normally, history row included. */
    @Test
    public void aUsableWeatherRoundTrips() {
        Weather weather = fixtureWeather();

        Weather[] stored = new Weather[1];
        offMainThread(() -> {
            DatabaseHelper.getInstance(mContext).writeWeather(mLocation, weather);
            stored[0] = DatabaseHelper.getInstance(mContext).readWeather(mLocation);
        });

        assertNotNull(stored[0]);
        assertEquals(3, stored[0].getDailyForecast().size());
    }

    /**
     * The regression: writing a weather with no daily entries used to index into an empty list and
     * kill the process. It must simply store no history row.
     */
    /**
     * A cached read is the other funnel that has to hand the place's zone to the days and hours it
     * rebuilds: widgets and notifications draw from here, and none of them holds the location when
     * they format a weekday name or an hour label.
     */
    @Test
    public void aCachedReadCarriesThePlacesZone() {
        Location tokyo = new Location(
                "tokyo",
                35.6895f, 139.6917f,
                TimeZone.getTimeZone("Asia/Tokyo"),
                "日本", "東京都", "東京", "",
                null,
                WeatherSource.OPEN_METEO,
                false, false, false
        );
        Weather weather = fixtureWeather(tokyo);

        Weather[] stored = new Weather[1];
        offMainThread(() -> {
            DatabaseHelper.getInstance(mContext).writeWeather(tokyo, weather);
            stored[0] = DatabaseHelper.getInstance(mContext).readWeather(tokyo);
        });

        assertNotNull(stored[0]);
        assertEquals(TimeZone.getTimeZone("Asia/Tokyo"),
                stored[0].getDailyForecast().get(0).getTimeZone());
        assertEquals(TimeZone.getTimeZone("Asia/Tokyo"),
                stored[0].getHourlyForecast().get(0).getTimeZone());
    }

    @Test
    public void aWeatherWithoutDailyEntriesStoresWithoutCrashing() {
        Weather weather = withoutDailyForecast(fixtureWeather());

        Throwable[] thrown = new Throwable[1];
        offMainThread(() -> {
            try {
                DatabaseHelper.getInstance(mContext).writeWeather(mLocation, weather);
            } catch (Throwable t) {
                thrown[0] = t;
            }
        });

        assertNull("writing an empty daily list must not blow up: " + thrown[0], thrown[0]);
    }

    /**
     * CaiYun does not keep the reverse-geocoded name: it replaces the current position with a row
     * from the bundled city table, coordinates included. The lookup's OR chain reads as a ladder
     * from exact to loose, but a WHERE clause has no priority — with LIMIT 1 and no ORDER BY the
     * first row scanned won, and a prefecture's own row always precedes its districts, so every
     * district collapsed into its prefecture (舒城 → 六安, ~50 km away).
     */
    @Test
    public void aDistrictResolvesToTheDistrictAndNotItsPrefecture() {
        ChineseCity[] found = new ChineseCity[4];
        int[] loaded = new int[1];
        offMainThread(() -> {
            DatabaseHelper db = DatabaseHelper.getInstance(mContext);
            db.ensureChineseCityList(mContext);
            loaded[0] = db.countChineseCity();
            found[0] = db.readChineseCity("安徽", "六安", "舒城");
            found[1] = db.readChineseCity("天津", "天津", "南开");
            found[2] = db.readChineseCity("广东", "深圳", "南山");
            // No district to match: falling back to the prefecture is the right answer here.
            found[3] = db.readChineseCity("安徽", "六安", "");
        });

        // Without this the whole test would pass vacuously on an empty table.
        assertEquals("the bundled city table must actually load", 3216, loaded[0]);
        assertNotNull(found[0]);
        assertEquals("101221507", found[0].getCityId());
        assertEquals("舒城", found[0].getDistrict());
        // The coordinates travel with the row, so picking the wrong one moves the forecast.
        assertEquals(31.46, Double.parseDouble(found[0].getLatitude()), 0.01);
        assertEquals(116.94, Double.parseDouble(found[0].getLongitude()), 0.01);

        assertNotNull(found[1]);
        assertEquals("101031500", found[1].getCityId());
        assertNotNull(found[2]);
        assertEquals("101280604", found[2].getCityId());
        assertNotNull(found[3]);
        assertEquals("101221501", found[3].getCityId());
    }

    // ---- harness ----

    /** Room refuses main-thread access, and under Robolectric the test thread is the main thread. */
    private static void offMainThread(Runnable block) {
        Throwable[] thrown = new Throwable[1];
        Thread thread = new Thread(block);
        // A silently dead worker leaves every result null, which reads like a logic failure rather
        // than the crash it was; the same goes for a join that times out.
        thread.setUncaughtExceptionHandler((t, e) -> thrown[0] = e);
        thread.start();
        try {
            thread.join(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for a database call");
        }
        if (thrown[0] != null) {
            throw new AssertionError("the database call threw", thrown[0]);
        }
        if (thread.isAlive()) {
            fail("the database call did not finish in 60s");
        }
    }

    private Weather fixtureWeather() {
        return fixtureWeather(mLocation);
    }

    /** The rows are keyed by the weather's own cityId, so it has to be built for that location. */
    private Weather fixtureWeather(Location location) {
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("openmeteo/forecast.json");
        assertNotNull("fixture missing: openmeteo/forecast.json", in);
        OpenMeteoResult result = new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), OpenMeteoResult.class);
        Weather weather = OpenMeteoResultConverter.convert(mContext, location, result);
        assertNotNull(weather);
        return weather;
    }

    private static Weather withoutDailyForecast(Weather weather) {
        return new Weather(
                weather.getBase(),
                weather.getCurrent(),
                null,
                new ArrayList<>(),
                weather.getHourlyForecast(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
