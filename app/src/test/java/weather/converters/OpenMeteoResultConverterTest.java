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

import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Pollen;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoAirQualityResult;
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult;

/**
 * Open-Meteo is the keyless fallback source, so it is the one users land on when every keyed
 * provider is down — a silent conversion failure here has no backstop.
 *
 * Fixture is a real api.open-meteo.com/v1/forecast response for Beijing, requested with the exact
 * current/hourly/daily field lists {@code OpenMeteoWeatherService} sends, only with
 * forecast_days trimmed to 3 to keep it readable.
 *
 * Robolectric is needed because CommonConverter.getWindLevel resolves strings from resources.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class OpenMeteoResultConverterTest {

    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        // The converter parses Open-Meteo's local-time strings with a default-zone
        // SimpleDateFormat, matching the timezone the service asks the API for. Pin the JVM
        // default so the parsed instants are reproducible on any machine.
        mDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

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

    @org.junit.After
    public void tearDown() {
        TimeZone.setDefault(mDefaultTimeZone);
    }

    private OpenMeteoResult load(String name) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("openmeteo/" + name);
        assertNotNull("fixture missing: " + name, in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), OpenMeteoResult.class);
    }

    private OpenMeteoAirQualityResult loadAir(String name) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("openmeteo/" + name);
        assertNotNull("fixture missing: " + name, in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8),
                OpenMeteoAirQualityResult.class);
    }

    /**
     * The 3.4.13 / 3.4.14 bug in its general form: writing Base.cityId from anything other than
     * Location.getCityId() breaks the cache key that readWeather/deleteWeather look rows up by.
     */
    @Test
    public void baseCityIdComesFromLocation() {
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, load("forecast.json"));

        assertNotNull(weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
    }

    @Test
    public void fullResponseConvertsCompletely() {
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, load("forecast.json"));
        assertNotNull(weather);

        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(3 * 24, weather.getHourlyForecast().size());

        // Doubles are truncated, not rounded: 29.7 -> 29, 34.0 -> 34.
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(34, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(70f, weather.getCurrent().getRelativeHumidity(), 0.01f);
        assertEquals(1005.3f, weather.getCurrent().getPressure(), 0.01f);
        assertEquals(22, weather.getCurrent().getCloudCover().intValue());
        // Open-Meteo already reports km/h, so the speed must pass through unscaled.
        assertEquals(9.9f, weather.getCurrent().getWind().getSpeed(), 0.01f);
        assertEquals("S", weather.getCurrent().getWind().getDirection());
        assertEquals("晴", weather.getCurrent().getWeatherText());

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("2026-08-11", format(first.getDate()));
        assertEquals(32, first.day().getTemperature().getTemperature());
        assertEquals(25, first.night().getTemperature().getTemperature());
        // WMO 55 (dense drizzle) is rain, not clear — the default branch of the code map returns
        // CLEAR, so an unmapped code would silently read as sunshine.
        assertEquals(WeatherCode.RAIN, first.day().getWeatherCode());
        assertEquals("毛毛雨", first.day().getWeatherText());
        assertEquals(92f, first.day().getPrecipitationProbability().getTotal(), 0.01f);
        assertEquals(7, first.getUV().getIndex().intValue());

        // uv_index_max 1.35 truncates to 1, which is what the UV level thresholds read.
        assertEquals(1, weather.getDailyForecast().get(1).getUV().getIndex().intValue());

        // sunrise/sunset are requested by the service and were being dropped; without them
        // Weather.isDaylight() falls back to a clock-only guess instead of the real sun times.
        assertEquals("2026-08-11 05:22", format(first.sun().getRiseDate(), "yyyy-MM-dd HH:mm"));
        assertEquals("2026-08-11 19:17", format(first.sun().getSetDate(), "yyyy-MM-dd HH:mm"));

        Hourly firstHour = weather.getHourlyForecast().get(0);
        assertEquals("2026-08-11T00:00", format(firstHour.getDate(), "yyyy-MM-dd'T'HH:mm"));
        assertEquals(27, firstHour.getTemperature().getTemperature());
        // is_day = 0 at midnight; getting this backwards flips every night icon to a daytime one.
        assertFalse(firstHour.isDaylight());
        assertTrue(weather.getHourlyForecast().get(12).isDaylight());
    }

    /**
     * A 200 response that carries only "current" must still convert instead of tripping a @NonNull
     * assertion — the converter's outer catch would turn that crash into a silent "no data".
     *
     * Note the empty daily list: unlike MfResultConverter this converter does not reject it, so an
     * Open-Meteo response with no daily block relies entirely on the guards in
     * WeatherHelper.requestWeatherSuccess and DatabaseHelper.readWeather to keep the 76 unguarded
     * getDailyForecast().get(0) call sites from throwing.
     */
    @Test
    public void sparseResponseDoesNotBlowUp() {
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, load("sparse.json"));

        assertNotNull("a sparse but valid 200 response should still convert", weather);
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        // No weather_code in the payload must not leave Current.weatherCode null: the model
        // declares it @NonNull (unenforced in Java, so the old converter did leave it null) and
        // every icon/colour consumer dereferences it. Missing reads as the CLEAR default.
        assertEquals(WeatherCode.CLEAR, weather.getCurrent().getWeatherCode());
        assertTrue(weather.getDailyForecast().isEmpty());
        assertTrue(weather.getHourlyForecast().isEmpty());
        assertTrue(weather.getAlertList().isEmpty());
    }

    @Test
    public void nullResultIsRejected() {
        assertNull(OpenMeteoResultConverter.convert(mContext, mLocation, null));
    }

    /**
     * The text table used to be the provider's English WMO wording, read verbatim wherever this
     * source led a block. Every documented code must come out of the one Chinese table in the same
     * vocabulary as the OWM/caiyun tables, and an undocumented code degrades by its WMO tens digit
     * (60 -> 雨) rather than printing English or a bare 未知.
     */
    @Test
    public void weatherTextFollowsTheChineseTableWhateverTheCode() {
        OpenMeteoResult result = load("forecast.json");

        Integer[] codes = {0, 1, 2, 3, 45, 55, 56, 61, 63, 65, 66, 71, 75, 77, 80, 85, 95, 96, 62, 10};
        String[] texts = {"晴", "晴", "多云", "阴", "雾", "毛毛雨", "冻雨", "小雨", "中雨", "大雨",
                "冻雨", "小雪", "大雪", "小雪", "阵雨", "阵雪", "雷阵雨", "雷阵雨伴有冰雹", "雨", "未知"};

        for (int i = 0; i < codes.length; i++) {
            result.current.weatherCode = codes[i];
            Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, result);
            assertNotNull("convert failed for code " + codes[i], weather);
            assertEquals("text of code " + codes[i], texts[i],
                    weather.getCurrent().getWeatherText());
        }
    }

    /**
     * The AQI must be derived from concentrations via the HJ 633-2012 formula — the air quality
     * endpoint ships no 0-500 index of its own, only european_aqi/us_aqi grade numbers that must
     * never be filed as one. Beijing's pm2.5 of 120.1 μg/m³ is IAQI 157 (against 87 for pm10).
     */
    @Test
    public void currentAirQualityIsDerivedFromConcentrations() {
        Weather weather = OpenMeteoResultConverter.convert(
                mContext, mLocation, load("forecast.json"), loadAir("air_beijing.json"));

        assertNotNull(weather);
        assertEquals(157, weather.getCurrent().getAirQuality().getAqiIndex().intValue());
        assertEquals(mContext.getString(R.string.aqi_4),
                weather.getCurrent().getAirQuality().getAqiText());
        // The API reports CO in μg/m³; the model's default CO unit is mg/m³.
        assertEquals(2.692f, weather.getCurrent().getAirQuality().getCO(), 0.001f);
        assertEquals(94.5f, weather.getCurrent().getAirQuality().getNO2(), 0.01f);
    }

    /**
     * Pollen folds onto the daily list by the date stamped in the requested timezone. The Paris
     * fixture was captured in August with real grass and ragweed concentrations while the three
     * tree species read a flat zero. Only its date labels were shifted to align with
     * forecast.json; the values are the captured ones.
     */
    @Test
    public void dailyPollenIsAggregatedFromTheHourlyMaxima() {
        Weather weather = OpenMeteoResultConverter.convert(
                mContext, mLocation, load("forecast.json"), loadAir("air_paris.json"));

        assertNotNull(weather);
        Pollen first = weather.getDailyForecast().get(0).getPollen();
        // The model stores the index as an integer, so the captured 1.4 grains/m³ rounds to 1.
        assertEquals(1, first.getGrassIndex().intValue());
        // 1.4 grains/m³ sits below grass's first Atmo France threshold of 3, so level 0.
        assertEquals(0, first.getGrassLevel().intValue());
        assertEquals(mContext.getString(R.string.pollen_level_none), first.getGrassDescription());
        assertEquals(5, first.getRagweedIndex().intValue());
        assertEquals(1, first.getRagweedLevel().intValue());
        assertEquals(mContext.getString(R.string.pollen_level_low), first.getRagweedDescription());
        // All three tree species at zero concentration: the slot keeps the zero rather than
        // pretending there is no reading, and the card-level isValid() ignores it.
        assertEquals(0, first.getTreeIndex().intValue());
        assertEquals(mContext.getString(R.string.pollen_level_none), first.getTreeDescription());

        assertEquals(6, weather.getDailyForecast().get(1).getPollen().getRagweedIndex().intValue());
    }

    /** Beijing is outside the pollen coverage: the API answers 200 with null columns, which
     * must fold into a pollen object no card considers valid — not a crash, not a half-fill. */
    @Test
    public void outOfCoveragePollenLeavesTheCardInvalid() {
        Weather weather = OpenMeteoResultConverter.convert(
                mContext, mLocation, load("forecast.json"), loadAir("air_beijing.json"));

        assertNotNull(weather);
        assertFalse(weather.getDailyForecast().get(0).getPollen().isValid());
    }

    /** The air quality call is optional: a forecast without it converts exactly as before. */
    @Test
    public void missingAirQualityLeavesTheWeatherUntouched() {
        Weather weather = OpenMeteoResultConverter.convert(mContext, mLocation, load("forecast.json"));

        assertNotNull(weather);
        assertNull(weather.getCurrent().getAirQuality().getAqiIndex());
        assertFalse(weather.getDailyForecast().get(0).getPollen().isValid());
    }

    private String format(java.util.Date date) {
        return format(date, "yyyy-MM-dd");
    }

    private String format(java.util.Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }
}
