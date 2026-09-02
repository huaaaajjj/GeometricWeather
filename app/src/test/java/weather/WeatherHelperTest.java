package weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
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
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BooleanSupplier;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.db.GeometricWeatherDatabase;
import wangdaye.com.geometricweather.weather.WeatherHelper;
import wangdaye.com.geometricweather.weather.WeatherServiceSet;
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * {@link WeatherHelper} is where every source's result funnels together, and it carries two
 * promises that nothing below it can keep on its own:
 *
 * 1. An empty daily list is not usable weather. Roughly 76 call sites across the UI, widgets and
 *    notifications read {@code getDailyForecast().get(0)} unguarded, and this is one of only two
 *    places that stops such a list from ever reaching them — by reporting failure, which keeps the
 *    previously cached weather instead of replacing it with something that crashes on display.
 * 2. Callbacks arrive on the main thread. Callers set LiveData from them, and LiveData.setValue()
 *    throws when it is called from a background thread — a crash this project already shipped once,
 *    when the RxJava-to-coroutines migration dropped an observeOn(mainThread).
 *
 * The services are faked here: what is under test is the funnel, not any provider.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class WeatherHelperTest {

    private Context mContext;
    private Location mLocation;
    private FakeService mService;
    private WeatherHelper mHelper;

    @Before
    public void setUp() throws Exception {
        resetDatabaseSingletons();

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

        mService = new FakeService();
        WeatherServiceSet serviceSet = mock(WeatherServiceSet.class);
        when(serviceSet.get(any())).thenReturn(mService);
        when(serviceSet.getAll()).thenReturn(new WeatherService[]{mService});
        mHelper = new WeatherHelper(serviceSet);

        // The weather table is a process-wide singleton across test methods; start each one clean.
        // Room refuses main-thread access, and under Robolectric the test thread *is* the main
        // thread — the same rule the app itself has to obey.
        offMainThread(() -> DatabaseHelper.getInstance(mContext).deleteWeather(mLocation));
    }

    /**
     * Both the helper and the Room database are process singletons, while Robolectric rebuilds the
     * app's data directory between test methods — so a connection opened by one test is left
     * pointing at a file the next test no longer has, and using it takes the whole test JVM down
     * (every method passes alone; running the class crashes the executor). Close and drop them so
     * each method opens its own.
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

    /** Runs a database call somewhere Room will accept it, and waits for it. */
    private static void offMainThread(Runnable block) {
        Thread thread = new Thread(block);
        thread.start();
        try {
            thread.join(20_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for a database call");
        }
    }

    // ---- the empty-daily guard ----

    /**
     * The promise the other 76 call sites rest on. A source that answers with no daily entries must
     * be treated as a failed refresh, and the listener must be handed back what was already cached.
     */
    @Test
    public void anEmptyDailyListIsAFailedRefreshAndKeepsTheCache() {
        Weather cached = fixtureWeather();
        assertFalse("the fixture must have daily entries to be worth caching",
                cached.getDailyForecast().isEmpty());
        offMainThread(() -> DatabaseHelper.getInstance(mContext).writeWeather(mLocation, cached));

        mService.weatherToReturn = withoutDailyForecast(cached);
        RecordingListener listener = request();

        assertTrue("an empty daily list must read as a failed refresh", listener.failed);
        assertNotNull("the cached weather must survive the failure", listener.result.getWeather());
        assertFalse("the listener must get the cache, not the empty list",
                listener.result.getWeather().getDailyForecast().isEmpty());
    }

    /** The ordinary path: a usable answer is reported as success and becomes the new cache. */
    @Test
    public void aUsableAnswerSucceedsAndIsCached() {
        mService.weatherToReturn = fixtureWeather();
        RecordingListener listener = request();

        assertTrue(listener.succeeded);
        assertNotNull(listener.result.getWeather());
        assertEquals(3, listener.result.getWeather().getDailyForecast().size());

        Weather[] stored = new Weather[1];
        offMainThread(() -> stored[0] = DatabaseHelper.getInstance(mContext).readWeather(mLocation));
        assertNotNull("a successful refresh must be written through to the cache", stored[0]);
        assertEquals(3, stored[0].getDailyForecast().size());
    }

    /** A source that fails outright behaves the same way: report failure, hand back the cache. */
    @Test
    public void anOutrightFailureAlsoKeepsTheCache() {
        Weather cached = fixtureWeather();
        offMainThread(() -> DatabaseHelper.getInstance(mContext).writeWeather(mLocation, cached));

        mService.fail = true;
        RecordingListener listener = request();

        assertTrue(listener.failed);
        assertNotNull(listener.result.getWeather());
        assertEquals(3, listener.result.getWeather().getDailyForecast().size());
    }

    /**
     * The third promise this funnel carries: every day and hour comes back knowing which zone it
     * belongs to. Widgets, notifications and a dozen view holders format weekday names and hour
     * labels off these models without ever holding the location, so a Tokyo forecast on a +08:00
     * phone was labelled an hour early everywhere until it was filled in here.
     */
    @Test
    public void daysAndHoursComeBackCarryingThePlacesZone() {
        Location tokyo = new Location(
                "tokyo",
                35.6895f, 139.6917f,
                TimeZone.getTimeZone("Asia/Tokyo"),
                "日本", "東京都", "東京", "",
                null,
                WeatherSource.OPEN_METEO,
                false, false, false
        );
        mService.weatherToReturn = fixtureWeather();

        RecordingListener listener = new RecordingListener();
        mHelper.requestWeather(mContext, tokyo, listener);
        awaitOnMainLooper(() -> listener.done, 20_000);

        assertTrue(listener.succeeded);
        Weather weather = listener.result.getWeather();
        assertNotNull(weather);
        assertEquals(TimeZone.getTimeZone("Asia/Tokyo"),
                weather.getDailyForecast().get(0).getTimeZone());
        assertEquals(TimeZone.getTimeZone("Asia/Tokyo"),
                weather.getHourlyForecast().get(0).getTimeZone());
    }

    // ---- the threading promise ----

    /**
     * Callers set LiveData in these callbacks. The service invokes its own callback from the IO
     * thread it fanned out onto, so the hop back has to happen here.
     */
    @Test
    public void callbacksArriveOnTheMainThread() {
        mService.weatherToReturn = fixtureWeather();
        RecordingListener success = request();
        assertTrue(success.succeeded);
        assertTrue("success must be delivered on the main thread", success.onMainThread);

        mService.fail = true;
        RecordingListener failure = request();
        assertTrue(failure.failed);
        assertTrue("failure must be delivered on the main thread", failure.onMainThread);
    }

    // ---- harness ----

    private RecordingListener request() {
        RecordingListener listener = new RecordingListener();
        mHelper.requestWeather(mContext, mLocation, listener);
        awaitOnMainLooper(() -> listener.done, 20_000);
        return listener;
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

    private Weather fixtureWeather() {
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("openmeteo/forecast.json");
        assertNotNull("fixture missing: openmeteo/forecast.json", in);
        OpenMeteoResult result = new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), OpenMeteoResult.class);
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, result);
        assertNotNull(weather);
        return weather;
    }

    /** The same weather with its daily list emptied — what a degraded source actually produces. */
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

    /** Stands in for any provider: answers from an IO thread, exactly as the real services do. */
    private static final class FakeService extends WeatherService {

        volatile Weather weatherToReturn;
        volatile boolean fail;

        @Override
        public void requestWeather(Context context, Location location,
                                   @NonNull RequestWeatherCallback callback) {
            AsyncHelper.runOnIO(() -> {
                if (fail) {
                    callback.requestWeatherFailed(location);
                } else {
                    callback.requestWeatherSuccess(Location.copy(location, weatherToReturn));
                }
            });
        }

        @Override
        public void requestLocation(Context context, Location location,
                                    @NonNull RequestLocationCallback callback) {
            callback.requestLocationSuccess(location.getCityId(), new ArrayList<>());
        }

        @Override
        public void cancel() {
        }
    }

    private static final class RecordingListener implements WeatherHelper.OnRequestWeatherListener {

        volatile boolean succeeded;
        volatile boolean failed;
        volatile boolean onMainThread;
        volatile boolean done;
        volatile Location result;

        @Override
        public void requestWeatherSuccess(@NonNull Location requestLocation) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            result = requestLocation;
            succeeded = true;
            done = true;
        }

        @Override
        public void requestWeatherFailed(@NonNull Location requestLocation) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            result = requestLocation;
            failed = true;
            done = true;
        }
    }
}
