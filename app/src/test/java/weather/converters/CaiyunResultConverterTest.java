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
        // cloud_rate 0.5 -> the detail gauge reads percent; without the mapping the multi-source
        // merge fills this from another provider while the card still credits this one.
        assertEquals(50, weather.getCurrent().getCloudCover().intValue());
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
     * The Weather constructor asserts @NonNull on the minutely list; passing null there was a
     * crash. The trial token returns no minutely block, so it must be an empty list, never null.
     */
    @Test
    public void minutelyListIsEmptyNotNull() {
        Weather weather = convert(load());
        assertNotNull(weather);

        assertNotNull(weather.getMinutelyForecast());
        assertTrue(weather.getMinutelyForecast().isEmpty());
    }

    /**
     * The trial token's ?alert=true used to be a white request: the DTO had no alert field, so the
     * converter always returned an empty list. With the alert field wired up, a response carrying
     * alert.content must produce real Alert objects.
     */
    @Test
    public void alertListIsPopulatedFromResponse() {
        Weather weather = convert(load());
        assertNotNull(weather);

        assertNotNull(weather.getAlertList());
        assertEquals(1, weather.getAlertList().size());
        assertEquals("天津市气象台发布暴雨黄色预警",
                weather.getAlertList().get(0).getDescription());
        // pubtimestamp 1786446000 -> 1786446000000 ms
        assertEquals(1786446000000L, weather.getAlertList().get(0).getTime());
    }

    /**
     * Missing alert block must still produce an empty list, never null (Weather constructor
     * asserts @NonNull).
     */
    @Test
    public void missingAlertYieldsEmptyList() {
        CaiYunWeatherResult result = load();
        result.result.alert = null;

        Weather weather = convert(result);
        assertNotNull(weather);
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
        result.result.realtime.cloudrate = null;

        Weather weather = convert(result);

        assertNotNull("a response with only realtime should still convert", weather);
        assertEquals(31, weather.getCurrent().getTemperature().getTemperature());
        // The field is boxed precisely so an absent cloud_rate reads as null (no gauge),
        // never as a fabricated 0%.
        assertNull(weather.getCurrent().getCloudCover());
        assertTrue(weather.getDailyForecast().isEmpty());
        assertTrue(weather.getHourlyForecast().isEmpty());
    }

    /**
     * Outside its coverage CaiYun answers with a whole block of zeros instead of with missing fields
     * — aqi.chn 0, all six concentrations 0, description.chn 缺数据. New York, Oslo and Sydney all
     * read exactly like this, and 東京 did on the day this was found. An AQI of 0 renders as 优, so
     * the absent reading was shown as pristine air; worse, in the multi-source merge a zero is a
     * non-null field, so it beat Open-Meteo's real concentrations for that place. Both the "now"
     * reading and the per-day one have to degrade to no reading at all.
     */
    @Test
    public void anAllZeroAirQualityIsNoReadingRatherThanPristineAir() {
        CaiYunWeatherResult result = load();
        CaiYunWeatherResult.AirQualityBean now = result.result.realtime.air_quality;
        now.aqi.chn = 0;
        now.aqi.usa = 0;
        now.pm25 = 0;
        now.pm10 = 0;
        now.o3 = 0;
        now.so2 = 0;
        now.no2 = 0;
        now.co = 0;
        now.description.chn = "缺数据";
        for (CaiYunWeatherResult.AqiDailyBean day : result.result.daily.air_quality.aqi) {
            day.avg.chn = 0;
        }
        for (CaiYunWeatherResult.Pm25DailyBean day : result.result.daily.air_quality.pm25) {
            day.avg = 0;
        }

        Weather weather = convert(result);
        assertNotNull(weather);

        // Nothing rather than "0 / 优" — MainAdapter hides the card on !isValid(), and the merge
        // falls through to whichever provider does have a reading.
        assertFalse(weather.getCurrent().getAirQuality().isValid());
        assertNull(weather.getCurrent().getAirQuality().getAqiIndex());
        assertNull(weather.getCurrent().getAirQuality().getAqiText());
        assertFalse(weather.getDailyForecast().get(0).getAirQuality().isValid());

        // A real reading still is one: the untouched fixture keeps its 25 / 9 µg for the same point.
        assertTrue(convert(load()).getCurrent().getAirQuality().isValid());
    }

    /**
     * A malformed wind entry must degrade to the direction-less placeholder, not to a Wind built
     * from nulls — three of Wind's four slots are @NonNull, and null-filled Winds are exactly what
     * crashed the v2.6 migration. Only the affected day degrades; its neighbours keep their data.
     */
    @Test
    public void nullWindEntryDegradesToAnEmptyWind() {
        CaiYunWeatherResult result = load();
        result.result.daily.wind.set(0, null);

        Weather weather = convert(result);
        assertNotNull(weather);

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("", first.day().getWind().getDirection());
        assertEquals("", first.day().getWind().getLevel());
        assertNotNull(first.day().getWind().getDegree());
        assertNull(first.day().getWind().getSpeed());

        // Day 1 is untouched: direction 58.83 deg -> ENE on the 16-point compass.
        assertEquals("ENE", weather.getDailyForecast().get(1).day().getWind().getDirection());
    }

    /**
     * v2.6 grades 霾 into LIGHT_/MODERATE_/HEAVY_HAZE and the text table only knew the bare "HAZE",
     * so on a hazy day 南开区's header read "未知, 体感 30°" while the icon and the animated
     * background were correctly hazy — WeatherCode.getInstance matches by substring, the table
     * matched exactly. One helper feeds the now / daily / hourly blocks, so all three read 未知 at
     * once and all three have to be named here.
     */
    @Test
    public void hazeLevelsAreNamedRatherThanUnknown() {
        CaiYunWeatherResult result = load();
        result.result.realtime.skycon = "LIGHT_HAZE";
        result.result.daily.skycon.get(0).value = "MODERATE_HAZE";
        result.result.daily.skycon_20h_32h.get(0).value = "HEAVY_HAZE";
        result.result.hourly.skycon.get(0).value = "LIGHT_HAZE";

        Weather weather = convert(result);
        assertNotNull(weather);

        assertEquals("轻度雾霾", weather.getCurrent().getWeatherText());
        assertEquals(WeatherCode.HAZE, weather.getCurrent().getWeatherCode());

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("中度雾霾", first.day().getWeatherText());
        assertEquals("重度雾霾", first.night().getWeatherText());
        assertEquals(WeatherCode.HAZE, first.day().getWeatherCode());

        assertEquals("轻度雾霾", weather.getHourlyForecast().get(0).getWeatherText());
        assertEquals(WeatherCode.HAZE, weather.getHourlyForecast().get(0).getWeatherCode());
    }

    /**
     * The graded HAZE values were not a one-off: an unlisted skycon still carries its family in its
     * name, and the icon is already derived that way. Naming the family keeps text and icon in step;
     * only a genuinely alien value is left as 未知.
     */
    @Test
    public void anUnlistedSkyconFallsBackToItsFamily() {
        assertEquals("霾", currentTextFor("EXTREME_HAZE"));
        assertEquals("雨", currentTextFor("EXTREME_RAIN"));
        assertEquals("雪", currentTextFor("EXTREME_SNOW"));
        // "PARTLY_CLOUDY_DAWN" contains "CLOUDY" too: the narrower family has to win.
        assertEquals("多云", currentTextFor("PARTLY_CLOUDY_DAWN"));
        assertEquals("阴", currentTextFor("VERY_CLOUDY"));
        assertEquals("晴", currentTextFor("CLEAR_DAWN"));
        assertEquals("未知", currentTextFor("SPACE_WEATHER"));
        assertEquals("未知", currentTextFor(""));

        // A daily entry may carry a null value outright; realtime cannot, the converter defaults it.
        CaiYunWeatherResult result = load();
        result.result.daily.skycon.get(0).value = null;
        assertEquals("未知", convert(result).getDailyForecast().get(0).day().getWeatherText());
    }

    private String currentTextFor(String skycon) {
        CaiYunWeatherResult result = load();
        result.result.realtime.skycon = skycon;
        Weather weather = convert(result);
        assertNotNull(weather);
        return weather.getCurrent().getWeatherText();
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
