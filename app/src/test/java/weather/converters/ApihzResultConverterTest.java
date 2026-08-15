package weather.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.weather.converters.ApihzResultConverter;
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult;

/**
 * Guards the APIHZ (中国天气网) converter against the two failure modes this repo keeps hitting:
 * a provider field being null blowing up a @NonNull assertion, and the cache key drifting off
 * Location.getCityId().
 *
 * Robolectric is needed because the converter reaches TextUtils and resolves wind-level strings
 * from resources; both are no-ops under a plain JVM runner.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class ApihzResultConverterTest {

    private Context mContext;
    private Location mLocation;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocation = new Location(
                "天津南开",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.APIHZ,
                false, false, true
        );
    }

    private ApihzWeatherResult load(String name) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("apihz/" + name);
        assertNotNull("fixture missing: " + name, in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), ApihzWeatherResult.class);
    }

    /**
     * The 3.4.13 / 3.4.14 bug: the converter wrote Base.cityId from the provider's own place name,
     * while readWeather/deleteWeather looked it up by Location.getCityId(). The keys never matched,
     * so the cache was never read and the weather table grew without bound.
     */
    @Test
    public void baseCityIdComesFromLocationNotProvider() {
        Weather weather = ApihzResultConverter.convert(mContext, mLocation, load("tqyb_full.json"));

        assertNotNull(weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
    }

    @Test
    public void fullResponseConvertsCompletely() {
        Weather weather = ApihzResultConverter.convert(mContext, mLocation, load("tqyb_full.json"));
        assertNotNull(weather);

        // Day 1 is flat on the root, days 2-7 are nested objects.
        List<Daily> daily = weather.getDailyForecast();
        assertEquals(7, daily.size());

        // hour1 (8 periods) + hour2 (4 periods), concatenated across the day buckets.
        List<Hourly> hourly = weather.getHourlyForecast();
        assertEquals(12, hourly.size());

        assertEquals(31, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(34, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(54f, weather.getCurrent().getRelativeHumidity(), 0.5f);
        // nowinfo.windSpeed is m/s and must be published as km/h.
        assertEquals(3.2f * 3.6f, weather.getCurrent().getWind().getSpeed(), 0.01f);

        assertEquals(1, weather.getAlertList().size());
        assertEquals("天津市气象台发布高温橙色预警", weather.getAlertList().get(0).getDescription());

        // Every day is dated from the fixture now, day 1 included, so the sun times are
        // deterministic rather than depending on when the test runs.
        assertNotNull(daily.get(1).sun());
        assertNotNull(daily.get(1).sun().getRiseDate());
        assertNotNull(daily.get(1).sun().getSetDate());

        // wd1/wd2 arrive as strings on the root but as JSON numbers in the nested days.
        assertEquals(33, daily.get(0).day().getTemperature().getTemperature());
        assertEquals(25, daily.get(0).night().getTemperature().getTemperature());
        assertEquals(32, daily.get(1).day().getTemperature().getTemperature());
    }

    /**
     * Day 1 arrives flat on the root with no date of its own, so it has to be dated from something.
     * Taking the device's "today" is wrong: just after local midnight the API can still be serving
     * yesterday as day 1, and dating that "today" makes it collide with day 2 — two entries claim
     * the same day, the first holding stale numbers. Observed on a real device at 00:51, where the
     * daily strip read "今日 8-15 / 今日 8-15 / 周日 8-16" with the first column carrying the
     * previous day's forecast. Everything that reads dailyForecast[0] as today — the main header,
     * every widget, the notifications — follows that first entry.
     *
     * Deriving it from day 2 instead makes the whole strip self-consistent and clock-independent.
     */
    @Test
    public void dayOneIsDatedFromTheResponseNotTheDeviceClock() {
        Weather weather = ApihzResultConverter.convert(mContext, mLocation, load("tqyb_full.json"));
        assertNotNull(weather);

        List<Daily> daily = weather.getDailyForecast();
        // The fixture's day 2 is 2026/08/07, so day 1 is the day before it.
        assertEquals("2026-08-06", formatCn(daily.get(0).getDate()));
        assertEquals("2026-08-07", formatCn(daily.get(1).getDate()));

        // The property that matters regardless of fixture: consecutive days, never a duplicate.
        for (int i = 1; i < daily.size(); i++) {
            assertEquals("day " + i + " must follow day " + (i - 1),
                    ONE_DAY_MILLIS,
                    daily.get(i).getDate().getTime() - daily.get(i - 1).getDate().getTime());
        }
    }

    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private static String formatCn(java.util.Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(date);
    }

    /**
     * A 200 response carrying almost nothing must still produce a Weather rather than tripping a
     * @NonNull assertion — the converter's outer catch would otherwise silently turn a crash into
     * "no data", which is how these regressions stayed invisible.
     */
    @Test
    public void sparseResponseDoesNotBlowUp() {
        Weather weather = ApihzResultConverter.convert(mContext, mLocation, load("tqyb_sparse.json"));

        assertNotNull("a sparse but valid 200 response should still convert", weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
        assertTrue(weather.getHourlyForecast().isEmpty());
        assertTrue(weather.getAlertList().isEmpty());
        // Day 1 is anchored to today even with no forecast payload.
        assertFalse(weather.getDailyForecast().isEmpty());
    }

    @Test
    public void nonOkCodeIsRejected() {
        ApihzWeatherResult result = load("tqyb_full.json");
        result.code = 400;
        assertNull(ApihzResultConverter.convert(mContext, mLocation, result));

        assertNull(ApihzResultConverter.convert(mContext, mLocation, null));
    }
}
