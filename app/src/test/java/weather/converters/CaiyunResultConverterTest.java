package weather.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.weather.converters.CaiyunResultConverter;
import wangdaye.com.geometricweather.weather.json.caiyun.CaiYunWeatherResult;

/**
 * CaiYun's v2.6 migration produced three crashes in a row (Wind built from nulls, minutely/alert
 * passed as null into @NonNull constructor slots, and daily.air_quality arriving as an object where
 * the DTO expected an array). All three failed the same way: an exception inside the converter,
 * swallowed by its own catch, surfacing as "no data" rather than a stack trace. These tests pin
 * each of them.
 *
 * Fixture is a real api.caiyunapp.com/v2.6 response for the Tianjin 南开 point with alert=true,
 * hourly arrays trimmed from 48 entries to 6.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class CaiyunResultConverterTest {

    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        mLocation = new Location(
                "54517_tj",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.CAIYUN,
                false, false, true
        );
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mDefaultTimeZone);
    }

    private CaiYunWeatherResult load() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("caiyun/weather.json");
        assertNotNull("fixture missing", in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), CaiYunWeatherResult.class);
    }

    private Weather convert(CaiYunWeatherResult result) {
        return CaiyunResultConverter.convert(mContext, mLocation, result).getResult();
    }

    @Test
    public void baseCityIdComesFromLocation() {
        Weather weather = convert(load());

        assertNotNull(weather);
        assertEquals("54517_tj", weather.getBase().getCityId());
        // Timestamps come from the provider's server_time, not the device clock.
        assertEquals(1786446926000L, weather.getBase().getPublishTime());
    }

    @Test
    public void fullResponseConvertsCompletely() {
        Weather weather = convert(load());
        assertNotNull(weather);

        // The bundled token is a trial one: daily is capped at 3 days and there is no minutely
        // block. Both are product constraints worth failing on if they ever change silently.
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(6, weather.getHourlyForecast().size());

        // Values round rather than truncate here: 30.67 -> 31, 34.4 -> 34.
        assertEquals(31, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(34, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(18.39f, weather.getCurrent().getVisibility(), 0.01f);
        // CaiYun sends humidity as a 0..1 fraction and pressure in pascals; the model wants
        // percent and hPa, so 0.76 -> 76 and 100430.02 Pa -> 1004.3 hPa.
        assertEquals(76f, weather.getCurrent().getRelativeHumidity(), 0.5f);
        assertEquals(1004.3f, weather.getCurrent().getPressure(), 0.1f);
        assertEquals("多云", weather.getCurrent().getWeatherText());
        assertEquals(WeatherCode.PARTLY_CLOUDY, weather.getCurrent().getWeatherCode());
        // direction 135.18 deg -> SE on the 16-point compass.
        assertEquals("SE", weather.getCurrent().getWind().getDirection());
        assertEquals(7.97f, weather.getCurrent().getWind().getSpeed(), 0.01f);
        // forecast_keypoint is what the main screen shows under the temperature.
        assertEquals("阴，明天上午10点钟后转小雨，其后阴",
                weather.getCurrent().getHourlyForecast());

        assertTrue(weather.getCurrent().getAirQuality().isValid());
        assertEquals(25, weather.getCurrent().getAirQuality().getAqiIndex().intValue());
        assertEquals(9f, weather.getCurrent().getAirQuality().getPM25(), 0.01f);

        Daily first = weather.getDailyForecast().get(0);
        // "2026-08-11T00:00+08:00" — the pattern stops at minutes, so the offset tail is ignored
        // and the explicit Asia/Shanghai zone is what places the instant.
        assertEquals("2026-08-11 00:00", format(first.getDate(), "yyyy-MM-dd HH:mm"));
        assertEquals(32, first.day().getTemperature().getTemperature());
        assertEquals(26, first.night().getTemperature().getTemperature());
        // Day takes skycon, night takes skycon_20h_32h — they differ in this fixture, so a mix-up
        // is visible rather than masked by identical values.
        assertEquals("多云", first.day().getWeatherText());
        assertEquals("阴", first.night().getWeatherText());
        assertEquals(WeatherCode.CLOUDY, first.night().getWeatherCode());
        assertEquals("SE", first.day().getWind().getDirection());
        assertEquals(5.23f, first.day().getWind().getSpeed(), 0.01f);

        Hourly firstHour = weather.getHourlyForecast().get(0);
        assertEquals("2026-08-11 19:00", format(firstHour.getDate(), "yyyy-MM-dd HH:mm"));
        assertEquals(31, firstHour.getTemperature().getTemperature());
        // Daylight is derived from the skycon suffix; PARTLY_CLOUDY_NIGHT must not read as day.
        assertFalse(firstHour.isDaylight());
    }

    /**
     * Sun times used to be parsed from the bare "05:20" clock, landing on 1970-01-01, which made
     * Weather.isDaylight() compare "now" against an epoch-day sunset and answer "night" forever.
     * The instant must carry the day's own date, in the payload's own timezone.
     */
    @Test
    public void sunTimesCarryTheirOwnDate() {
        Weather weather = convert(load());
        assertNotNull(weather);

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("2026-08-11 05:20", format(first.sun().getRiseDate(), "yyyy-MM-dd HH:mm"));
        assertEquals("2026-08-11 19:12", format(first.sun().getSetDate(), "yyyy-MM-dd HH:mm"));
        assertTrue(first.sun().getSetDate().getTime() > 1_700_000_000_000L);
    }

    /**
     * v2.6 returns daily.air_quality as an object ({aqi:[...], pm25:[...]}), not the array the v1
     * DTO expected — decoding it wrongly threw inside the converter and the source went dark. The
     * per-day AQI coming through proves the object shape still decodes.
     */
    @Test
    public void dailyAirQualityObjectShapeStillDecodes() {
        Weather weather = convert(load());
        assertNotNull(weather);

        // aqi[0].avg.chn in the fixture.
        assertEquals(31, weather.getDailyForecast().get(0).getAirQuality().getAqiIndex().intValue());
        assertEquals(29, weather.getDailyForecast().get(1).getAirQuality().getAqiIndex().intValue());
    }

    /**
     * The Weather constructor asserts @NonNull on the minutely and alert lists; passing null there
     * was a crash. The trial token returns neither block, so these must be empty lists, never null.
     */
    @Test
    public void minutelyAndAlertListsAreEmptyNotNull() {
        Weather weather = convert(load());
        assertNotNull(weather);

        assertNotNull(weather.getMinutelyForecast());
        assertTrue(weather.getMinutelyForecast().isEmpty());
        assertNotNull(weather.getAlertList());
        assertTrue(weather.getAlertList().isEmpty());
    }

    /**
     * Missing sub-blocks must degrade, not explode: wind and air_quality absent from realtime, and
     * the whole daily block absent, still has to produce a Weather rather than a swallowed
     * exception.
     */
    @Test
    public void sparseRealtimeDoesNotBlowUp() {
        CaiYunWeatherResult result = load();
        result.result.daily = null;
        result.result.hourly = null;
        result.result.realtime.air_quality = null;

        Weather weather = convert(result);

        assertNotNull("a response with only realtime should still convert", weather);
        assertEquals(31, weather.getCurrent().getTemperature().getTemperature());
        assertTrue(weather.getDailyForecast().isEmpty());
        assertTrue(weather.getHourlyForecast().isEmpty());
    }

    @Test
    public void missingRealtimeIsRejected() {
        CaiYunWeatherResult result = load();
        result.result.realtime = null;
        assertNull(convert(result));

        result = load();
        result.result = null;
        assertNull(convert(result));

        assertNull(convert(null));
    }

    private String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }
}
