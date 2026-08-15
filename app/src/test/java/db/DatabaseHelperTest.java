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

    // ---- harness ----

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
