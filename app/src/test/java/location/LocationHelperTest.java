package location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BooleanSupplier;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper;
import wangdaye.com.geometricweather.db.DatabaseHelper;
import wangdaye.com.geometricweather.db.GeometricWeatherDatabase;
import wangdaye.com.geometricweather.location.LocationHelper;
import wangdaye.com.geometricweather.location.services.LocationService;
import wangdaye.com.geometricweather.weather.WeatherServiceSet;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * Locating happens in two stages: a location service supplies coordinates (and, for the Baidu and
 * AMap SDKs, an address), then the active weather source turns those into a location it can
 * actually serve weather for. What matters at this seam is what happens when either stage comes up
 * empty — the app must still end up with *some* location, because a current-position entry with
 * nothing in it is what leaves a user staring at a blank screen.
 *
 * That is the Beijing fallback, and it is deliberately kept: the 3.3.7–3.3.13 line replaced it and
 * had to be rolled back wholesale for locating regressions, so it is pinned here rather than left
 * to be "cleaned up" later.
 *
 * The real location services pull in the Baidu and AMap SDKs and cannot run off a device, so they
 * are handed in as fakes through the testing constructor.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class LocationHelperTest {

    private Context mContext;
    private FakeLocationService mLocationService;
    private FakeWeatherService mWeatherService;
    private LocationHelper mHelper;

    @Before
    public void setUp() throws Exception {
        resetDatabaseSingletons();

        mContext = ApplicationProvider.getApplicationContext();
        // The helper refuses to locate without one of these, which is its own (separate) branch.
        shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext())
                .grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        mLocationService = new FakeLocationService();
        mWeatherService = new FakeWeatherService();

        WeatherServiceSet serviceSet = mock(WeatherServiceSet.class);
        when(serviceSet.get(any())).thenReturn(mWeatherService);
        when(serviceSet.getAll()).thenReturn(new WeatherService[]{mWeatherService});

        // Every slot is the same fake, so the test does not depend on which provider is configured.
        mHelper = new LocationHelper(
                new LocationService[]{
                        mLocationService, mLocationService, mLocationService, mLocationService
                },
                serviceSet
        );
    }

    /**
     * Both the helper and the Room database are process singletons while Robolectric rebuilds the
     * app's data directory between test methods; a connection carried over points at a file that no
     * longer exists, which takes the test JVM down rather than failing an assertion.
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

    // ---- the ordinary path ----

    /** Coordinates in, a source-resolved location out, persisted so the next cold start has it. */
    @Test
    public void aResolvedLocationIsPersistedAndReported() {
        mLocationService.result = new LocationService.Result(
                39.113019f, 117.150738f, "中国", "天津市", "天津市", "南开区");
        mWeatherService.resolved = Collections.singletonList(tianjin());

        Recording listener = request(Location.buildLocal());

        assertTrue(listener.succeeded);
        assertEquals("54517_tj", listener.result.getCityId());
        assertTrue("a resolved current position must be marked as one",
                listener.result.isCurrentPosition());

        List<Location> stored = new ArrayList<>();
        offMainThread(() -> stored.addAll(
                DatabaseHelper.getInstance(mContext).readLocationList()));
        assertFalse("the resolved location must be written through", stored.isEmpty());
    }

    // ---- the fallback that keeps the app usable ----

    /**
     * Nothing located and nothing usable to fall back on: rather than leaving an empty current
     * position, the app adopts the default location so there is something to show.
     */
    @Test
    public void anEmptyResultWithNothingUsableFallsBackToTheDefault() {
        mLocationService.result = null;

        Recording listener = request(Location.buildLocal());

        assertTrue("failing to locate must still report, not hang", listener.failed);
        assertNotNull(listener.result);
        assertEquals("the documented default is Beijing", "101924", listener.result.getCityId());

        List<Location> stored = new ArrayList<>();
        offMainThread(() -> stored.addAll(
                DatabaseHelper.getInstance(mContext).readLocationList()));
        assertFalse("the fallback must be persisted, not just reported", stored.isEmpty());
    }

    /**
     * The opposite case: locating failed but the caller already had a usable location. That one is
     * kept — overwriting a city the user has been looking at with the default would be worse than
     * a stale reading.
     */
    @Test
    public void aUsableLocationSurvivesAFailedLocate() {
        mLocationService.result = null;

        Recording listener = request(tianjin());

        assertTrue(listener.failed);
        assertEquals("the caller's own location must come back untouched",
                "54517_tj", listener.result.getCityId());
    }

    /** The weather source knowing nothing about the coordinates is a failed locate, not a crash. */
    @Test
    public void aSourceThatResolvesNothingIsAFailedLocate() {
        mLocationService.result = new LocationService.Result(39.9f, 116.4f);
        mWeatherService.resolved = new ArrayList<>();

        Recording listener = request(tianjin());

        assertTrue(listener.failed);
        assertEquals("54517_tj", listener.result.getCityId());
    }

    // ---- threading ----

    /**
     * The same promise WeatherHelper makes: callers update LiveData from these callbacks, and the
     * weather source answers from the IO thread it fanned out onto, so the hop back belongs here.
     */
    @Test
    public void callbacksArriveOnTheMainThread() {
        mLocationService.result = new LocationService.Result(39.9f, 116.4f);
        mWeatherService.resolved = Collections.singletonList(tianjin());

        Recording success = request(Location.buildLocal());
        assertTrue(success.succeeded);
        assertTrue("success must be delivered on the main thread", success.onMainThread);

        mWeatherService.resolved = new ArrayList<>();
        Recording failure = request(tianjin());
        assertTrue(failure.failed);
        assertTrue("failure must be delivered on the main thread too", failure.onMainThread);
    }

    // ---- harness ----

    private Recording request(Location start) {
        Recording listener = new Recording();
        mHelper.requestLocation(mContext, start, false, listener);
        awaitOnMainLooper(() -> listener.done, 20_000);
        return listener;
    }

    private static Location tianjin() {
        return new Location(
                "54517_tj",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.WEATHERAPI,
                true, false, true
        );
    }

    /** Callbacks land on the main looper, which under Robolectric is the thread running the test. */
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

    /** Room refuses main-thread access, and under Robolectric the test thread is the main thread. */
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

    private static final class FakeLocationService extends LocationService {

        volatile Result result;

        @Override
        public void requestLocation(@NonNull Context context, @NonNull LocationCallback callback) {
            callback.onCompleted(result);
        }

        @Override
        public void cancel() {
        }

        @NonNull
        @Override
        public String[] getPermissions() {
            return new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    /** Answers from an IO thread, the way every real weather service does. */
    private static final class FakeWeatherService extends WeatherService {

        volatile List<Location> resolved = new ArrayList<>();

        @Override
        public void requestWeather(Context context, Location location,
                                   @NonNull RequestWeatherCallback callback) {
        }

        @NonNull
        @Override
        public List<Location> requestLocation(Context context, String query) {
            return resolved;
        }

        @Override
        public void requestLocation(Context context, Location location,
                                    @NonNull RequestLocationCallback callback) {
            AsyncHelper.runOnIO(() -> {
                if (resolved.isEmpty()) {
                    callback.requestLocationFailed(location.getFormattedId());
                } else {
                    callback.requestLocationSuccess(location.getFormattedId(), resolved);
                }
            });
        }

        @Override
        public void cancel() {
        }
    }

    private static final class Recording implements LocationHelper.OnRequestLocationListener {

        volatile boolean succeeded;
        volatile boolean failed;
        volatile boolean onMainThread;
        volatile boolean done;
        volatile Location result;

        @Override
        public void requestLocationSuccess(Location requestLocation) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            result = requestLocation;
            succeeded = true;
            done = true;
        }

        @Override
        public void requestLocationFailed(Location requestLocation) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
            result = requestLocation;
            failed = true;
            done = true;
        }
    }
}
