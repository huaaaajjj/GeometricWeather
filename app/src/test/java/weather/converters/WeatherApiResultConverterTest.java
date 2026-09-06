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
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.weather.converters.WeatherApiResultConverter;
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult;

/**
 * WeatherAPI is the default source since 3.5.2, so every new install converts through here first.
 *
 * The fixture is a real api.weatherapi.com/v1/forecast.json response for the Tianjin 南开 point
 * (days=3, aqi=yes, alerts=yes — exactly what WeatherApiWeatherService asks for), trimmed to 6 of
 * each day's 24 hour entries. It happens to reproduce the 3.4.13 bug in the wild: the provider
 * attached a 霸州市 (Hebei) rainstorm warning to a Tianjin request.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class WeatherApiResultConverterTest {

    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        // The converter parses the provider's local-time strings with a default-zone
        // SimpleDateFormat; pin the JVM default so parsed instants are reproducible.
        mDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        mLocation = new Location(
                "54517_tj",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.WEATHERAPI,
                false, false, true
        );
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mDefaultTimeZone);
    }

    private WeatherApiResult load(String name) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("weatherapi/" + name);
        assertNotNull("fixture missing: " + name, in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), WeatherApiResult.class);
    }

    /**
     * The 3.4.14 fix. Base.cityId used to be written from result.location.name — which is whatever
     * the provider decides to call the point ("Wangdingdi" here, "Tianjin" on another day). Rows
     * are read and deleted by Location.getCityId(), so drifting off it meant the WeatherAPI cache
     * was never hit and the weather table grew without bound.
     */
    @Test
    public void baseCityIdComesFromLocationNotProvider() {
        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, load("forecast.json"));

        assertNotNull(weather);
        assertEquals("54517_tj", weather.getBase().getCityId());
        // Guard the specific wrong value, not just the right one.
        assertFalse("Wangdingdi".equals(weather.getBase().getCityId()));
    }

    @Test
    public void fullResponseConvertsCompletely() {
        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, load("forecast.json"));
        assertNotNull(weather);

        assertEquals(3, weather.getDailyForecast().size());
        // Hours are concatenated across the day buckets, 6 per day in the trimmed fixture.
        assertEquals(18, weather.getHourlyForecast().size());

        // Doubles truncate rather than round: 29.1 -> 29, 33.0 -> 33.
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(33, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(70f, weather.getCurrent().getRelativeHumidity(), 0.01f);
        assertEquals(1005f, weather.getCurrent().getPressure(), 0.01f);
        assertEquals(9f, weather.getCurrent().getVisibility(), 0.01f);
        assertEquals(89, weather.getCurrent().getCloudCover().intValue());
        // WeatherAPI reports wind in km/h already (wind_kph 12.2 next to wind_mph 7.6): the
        // converter must take the kph field verbatim and not scale it.
        assertEquals(12.2f, weather.getCurrent().getWind().getSpeed(), 0.01f);
        assertEquals("SSW", weather.getCurrent().getWind().getDirection());
        assertEquals("雨", weather.getCurrent().getWeatherText());
        assertEquals(WeatherCode.RAIN, weather.getCurrent().getWeatherCode());

        // aqi=yes. Upstream us-epa-index is 2, but that is a 1..6 category, not an AQI; the model's
        // index is a 0..500 China AQI computed from the concentrations (pm2.5 29.0 -> 41,
        // pm10 32.6 -> 33, worst pollutant wins).
        assertTrue(weather.getCurrent().getAirQuality().isValid());
        assertEquals(41, weather.getCurrent().getAirQuality().getAqiIndex().intValue());
        assertEquals("Fresh air", weather.getCurrent().getAirQuality().getAqiText());
        assertEquals(29f, weather.getCurrent().getAirQuality().getPM25(), 0.01f);

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("2026-08-11", format(first.getDate(), "yyyy-MM-dd"));
        assertEquals(33, first.day().getTemperature().getTemperature());
        assertEquals(25, first.night().getTemperature().getTemperature());
        assertEquals(61f, first.day().getPrecipitationProbability().getTotal(), 0.01f);
        assertEquals(8, first.getUV().getIndex().intValue());
        assertEquals(WeatherCode.CLEAR, first.day().getWeatherCode());
        // Day and night share the provider's single daily condition, differing only in temperature.
        assertEquals(first.day().getWeatherText(), first.night().getWeatherText());

        // astro sunrise "05:21 AM" / sunset "07:12 PM" — the 12-hour clock must not read PM as AM.
        assertNotNull(first.sun().getRiseDate());
        assertNotNull(first.sun().getSetDate());
        assertEquals("05:21", format(first.sun().getRiseDate(), "HH:mm"));
        assertEquals("19:12", format(first.sun().getSetDate(), "HH:mm"));
        assertEquals(0, first.getMoonPhase().getAngle().intValue()); // "New Moon"

        Hourly firstHour = weather.getHourlyForecast().get(0);
        assertEquals("2026-08-11 00:00", format(firstHour.getDate(), "yyyy-MM-dd HH:mm"));
        assertEquals(26, firstHour.getTemperature().getTemperature());
        assertEquals("ESE", firstHour.getWind().getDirection());
        // is_day = 0 at midnight; inverting this flips every night icon to its daytime variant.
        assertFalse(firstHour.isDaylight());
    }

    /**
     * The 3.4.13 bug: WeatherAPI returned a 霸州市 (Hebei) warning for a Tianjin point. This is that
     * exact response, so the alert list must come back empty.
     */
    @Test
    public void alertForAnotherAdminAreaIsDropped() {
        WeatherApiResult result = load("forecast.json");
        assertEquals("fixture should carry the mis-attached alert",
                1, result.alerts.alert.size());
        assertEquals("霸州市", result.alerts.alert.get(0).areas);

        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, result);

        assertNotNull(weather);
        assertTrue("an alert for another city must not surface on this location",
                weather.getAlertList().isEmpty());
    }

    /**
     * The other half of the filter: a warning that does name this location must survive. Without
     * this, "drop foreign alerts" could be implemented as "drop all alerts" and still look fixed.
     */
    @Test
    public void alertForThisAdminAreaIsKept() {
        WeatherApiResult result = load("forecast.json");
        // Same payload, re-attributed to this location's city (province/city stem is 天津).
        result.alerts.alert.get(0).areas = "天津市";
        result.alerts.alert.get(0).headline = "天津市气象台更新暴雨红色预警[I级/特别严重]";

        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, result);

        assertNotNull(weather);
        assertEquals(1, weather.getAlertList().size());
        Alert alert = weather.getAlertList().get(0);
        assertEquals("暴雨", alert.getType());
        assertEquals("天津市气象台更新暴雨红色预警[I级/特别严重]", alert.getDescription());
        assertTrue(alert.getContent().startsWith("霸州市气象台2026年08月11日"));
        // effective "2026-08-11T14:26:23+08:00" — the pattern only spans minutes, so the seconds
        // and offset tail are ignored rather than failing the parse.
        assertEquals("2026-08-11 14:26", format(alert.getDate(), "yyyy-MM-dd HH:mm"));
    }

    /**
     * Sun times used to be parsed from the bare "05:21 AM" clock, landing on 1970-01-01. That made
     * Weather.isDaylight() compare "now" against an epoch-day sunset and return false forever — on
     * the default source, so the main screen, every widget and the live wallpaper sat in night
     * colours around the clock. The instant must carry the forecast day's own date.
     */
    @Test
    public void sunTimesCarryTheirOwnDate() {
        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, load("forecast.json"));
        assertNotNull(weather);

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("2026-08-11 05:21", format(first.sun().getRiseDate(), "yyyy-MM-dd HH:mm"));
        assertEquals("2026-08-11 19:12", format(first.sun().getSetDate(), "yyyy-MM-dd HH:mm"));
        assertEquals("2026-08-12 05:22",
                format(weather.getDailyForecast().get(1).sun().getRiseDate(), "yyyy-MM-dd HH:mm"));
        // Moon times ride the same helper; moonrise 02:38 AM must not read as 2:38 PM.
        assertEquals("2026-08-11 02:38", format(first.moon().getRiseDate(), "yyyy-MM-dd HH:mm"));

        // The consequence, stated directly: a timestamp between the two must read as daylight.
        assertTrue(first.sun().getRiseDate().getTime() < first.sun().getSetDate().getTime());
        assertTrue(first.sun().getSetDate().getTime() > 1_700_000_000_000L);
    }

    /**
     * A 200 response carrying only "current" must still convert instead of tripping a @NonNull
     * assertion; the converter's outer catch would otherwise turn that crash into a silent
     * "no data", which is how this class of regression stays invisible.
     */
    @Test
    public void sparseResponseDoesNotBlowUp() {
        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, load("sparse.json"));

        assertNotNull("a sparse but valid 200 response should still convert", weather);
        assertEquals("54517_tj", weather.getBase().getCityId());
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertTrue(weather.getDailyForecast().isEmpty());
        assertTrue(weather.getHourlyForecast().isEmpty());
        assertTrue(weather.getAlertList().isEmpty());
    }

    /**
     * A response with no condition block used to leave Current.weatherCode null: the model declares
     * it @NonNull (unenforced in Java, so the old converter did leave it null) and every icon and
     * colour consumer dereferences it — a crash waiting on a sparse payload from the default
     * source. Missing now reads as the same CLEAR default an unmapped code gets.
     */
    @Test
    public void missingConditionDoesNotYieldNullWeatherCode() {
        WeatherApiResult result = load("sparse.json");
        result.current.condition = null;

        Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, result);

        assertNotNull(weather);
        assertEquals(WeatherCode.CLEAR, weather.getCurrent().getWeatherCode());
        assertEquals("未知", weather.getCurrent().getWeatherText());
    }

    @Test
    public void missingCurrentIsRejected() {
        WeatherApiResult result = load("forecast.json");
        result.current = null;
        assertNull(WeatherApiResultConverter.convert(mContext, mLocation, result));

        assertNull(WeatherApiResultConverter.convert(mContext, mLocation, null));
    }

    /**
     * condition.text used to pass through verbatim, so any block this source led read English.
     * Text must come from condition.code in the shared Chinese vocabulary, and text and icon must
     * stay in the same family (the 3.6.11 lesson): 1066/1069/1072 used to map to RAIN or the CLEAR
     * default while meaning snow/sleet. The fixture's text field is left at its English original on
     * purpose — it must not be read any more.
     */
    @Test
    public void conditionCodeDrivesTextAndIconTogether() {
        Integer[] codes = {1000, 1003, 1009, 1012, 1018, 1021, 1027, 1030, 1063, 1066, 1069, 1072,
                1135, 1153, 1183, 1195, 1198, 1213, 1237, 1240, 1246, 1255, 1261, 1276, 9999};
        String[] texts = {"晴", "多云", "阴", "霾", "扬沙", "沙尘暴", "强沙尘暴", "雾", "雨", "雪",
                "雨夹雪", "冻雨", "雾", "毛毛雨", "小雨", "大雨", "冻雨", "小雪", "冰雹", "阵雨",
                "大阵雨", "阵雪", "冰雹", "雷阵雨", "未知"};
        WeatherCode[] icons = {WeatherCode.CLEAR, WeatherCode.CLOUDY, WeatherCode.CLOUDY,
                WeatherCode.HAZE, WeatherCode.WIND, WeatherCode.WIND, WeatherCode.WIND,
                WeatherCode.FOG, WeatherCode.RAIN, WeatherCode.SNOW, WeatherCode.SLEET,
                WeatherCode.SLEET, WeatherCode.FOG, WeatherCode.RAIN, WeatherCode.RAIN,
                WeatherCode.RAIN, WeatherCode.SLEET, WeatherCode.SNOW, WeatherCode.HAIL,
                WeatherCode.RAIN, WeatherCode.RAIN, WeatherCode.SNOW, WeatherCode.HAIL,
                WeatherCode.THUNDERSTORM, WeatherCode.CLEAR};

        for (int i = 0; i < codes.length; i++) {
            WeatherApiResult result = load("forecast.json");
            result.current.condition.code = codes[i];
            Weather weather = WeatherApiResultConverter.convert(mContext, mLocation, result);
            assertNotNull("convert failed for code " + codes[i], weather);
            assertEquals("text of code " + codes[i], texts[i],
                    weather.getCurrent().getWeatherText());
            assertEquals("icon of code " + codes[i], icons[i],
                    weather.getCurrent().getWeatherCode());
        }
    }

    private String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }
}
