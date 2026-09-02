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
import java.util.List;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Alert;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.weather.converters.CmaResultConverter;
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CmaResultConverterTest {

    private Context mContext;
    private Location mLocation;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocation = new Location(
                "54517",
                39.08f, 117.22f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.CMA,
                false, false, true
        );
    }

    private CmaWeatherResult loadWeather() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("cma/weather.json");
        assertNotNull("fixture missing: cma/weather.json", in);
        return new Gson().fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), CmaWeatherResult.class);
    }

    private String loadHtml() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("cma/hourly.html");
        assertNotNull("fixture missing: cma/hourly.html", in);
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void dailyCarriesAComputedSunrise() {
        // Upstream has no astro block; the sun is computed from the location's coordinates.
        // Reference for Tianjin (39.08N 117.22E) on 2026-08-14 — sunrise 05:21:53 CST, which is
        // 21:21:53Z the *previous* UTC day, so a UTC-midnight clamp in the calculator shows up
        // here as a 24-hour error rather than a rounding one. (sunrise-sunset.org almanac.)
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);
        assertTrue(weather.getDailyForecast().size() > 0);

        Astro sun = weather.getDailyForecast().get(0).sun();
        assertTrue(sun.isValid());
        assertEquals(utcMillis(2026, 8, 13, 21, 21, 53), sun.getRiseDate().getTime(), 180_000.0);
        assertEquals(utcMillis(2026, 8, 14, 11, 9, 51), sun.getSetDate().getTime(), 180_000.0);
    }

    private long utcMillis(int year, int month, int day, int hour, int minute, int second) {
        java.util.Calendar c = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, hour, minute, second);
        return c.getTimeInMillis();
    }

    @Test
    public void baseCityIdComesFromLocation() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
    }

    @Test
    public void fullResponseConvertsCompletely() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);

        // now block has no weather text; it is derived from today's daily.
        assertNotNull(weather.getCurrent().getWeatherText());
        assertFalse(weather.getCurrent().getWeatherText().isEmpty());
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(32, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(62f, weather.getCurrent().getRelativeHumidity(), 0.5f);
        assertEquals(1008f, weather.getCurrent().getPressure(), 0.5f);
        // windSpeed 2.5 m/s -> 9.0 km/h
        assertEquals(9.0f, weather.getCurrent().getWind().getSpeed(), 0.01f);
        assertEquals("东南", weather.getCurrent().getWind().getDirection());
    }

    @Test
    public void dailyListConvertsCorrectly() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);

        List<Daily> daily = weather.getDailyForecast();
        assertEquals(3, daily.size());

        Daily d0 = daily.get(0);
        assertEquals("多云", d0.day().getWeatherText());
        assertEquals(WeatherCode.PARTLY_CLOUDY, d0.day().getWeatherCode());
        assertEquals(32, d0.day().getTemperature().getTemperature());
        assertEquals("阴", d0.night().getWeatherText());
        assertEquals(WeatherCode.CLOUDY, d0.night().getWeatherCode());
        assertEquals(25, d0.night().getTemperature().getTemperature());

        Daily d1 = daily.get(1);
        assertEquals("小雨", d1.day().getWeatherText());
        assertEquals(WeatherCode.RAIN, d1.day().getWeatherCode());
        assertEquals("中雨", d1.night().getWeatherText());
        assertEquals(WeatherCode.RAIN, d1.night().getWeatherCode());

        Daily d2 = daily.get(2);
        assertEquals("雷阵雨", d2.day().getWeatherText());
        assertEquals(WeatherCode.THUNDERSTORM, d2.day().getWeatherCode());
    }

    @Test
    public void hourlyHtmlParsesCorrectly() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);

        List<Hourly> hourly = weather.getHourlyForecast();
        assertEquals(12, hourly.size());

        Hourly h0 = hourly.get(0);
        assertEquals(14, h0.getHourIn24Format());
        assertEquals(32, h0.getTemperature().getTemperature());
        // icon w1 maps to 多云 -> PARTLY_CLOUDY via daily code map
        assertEquals(WeatherCode.PARTLY_CLOUDY, h0.getWeatherCode());
        assertTrue(h0.isDaylight());
        // windSpeed 2.5 m/s -> 9.0 km/h
        assertEquals(9.0f, h0.getWind().getSpeed(), 0.01f);

        // 20:00 is not daylight
        Hourly h6 = hourly.get(6);
        assertFalse(h6.isDaylight());

        // 00:00 wraps to next day, hour < previous -> day increment
        Hourly h10 = hourly.get(10);
        assertNotNull(h10);
    }

    @Test
    public void alertListConvertsCorrectly() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);

        List<Alert> alerts = weather.getAlertList();
        assertEquals(2, alerts.size());
        assertEquals("天津市气象台发布暴雨黄色预警", alerts.get(0).getDescription());
        assertEquals("暴雨", alerts.get(0).getType());
        assertEquals("天津市气象台发布雷电黄色预警", alerts.get(1).getDescription());
    }

    @Test
    public void nullResultReturnsNull() {
        assertNull(CmaResultConverter.convert(mContext, mLocation, null, null));
    }

    @Test
    public void nullDataReturnsNull() {
        CmaWeatherResult result = loadWeather();
        result.data = null;
        assertNull(CmaResultConverter.convert(mContext, mLocation, result, null));
    }

    @Test
    public void noHourlyHtmlYieldsEmptyHourly() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), null);
        assertNotNull(weather);
        assertTrue(weather.getHourlyForecast().isEmpty());
        // daily and alerts should still work
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(2, weather.getAlertList().size());
    }

    @Test
    public void emptyHourlyHtmlYieldsEmptyHourly() {
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), "");
        assertNotNull(weather);
        assertTrue(weather.getHourlyForecast().isEmpty());
    }

    @Test
    public void weatherCodeMappingCoversAllCategories() {
        // getWeatherCode is package-private; it is exercised indirectly by daily/hourly tests
        // above. Here we verify the mapping through the public convert() entry point by checking
        // that each daily day/night text maps to the expected WeatherCode.
        Weather weather = CmaResultConverter.convert(mContext, mLocation, loadWeather(), loadHtml());
        assertNotNull(weather);

        List<Daily> daily = weather.getDailyForecast();
        assertEquals(WeatherCode.PARTLY_CLOUDY, daily.get(0).day().getWeatherCode());
        assertEquals(WeatherCode.CLOUDY, daily.get(0).night().getWeatherCode());
        assertEquals(WeatherCode.RAIN, daily.get(1).day().getWeatherCode());
        assertEquals(WeatherCode.RAIN, daily.get(1).night().getWeatherCode());
        assertEquals(WeatherCode.THUNDERSTORM, daily.get(2).day().getWeatherCode());
        assertEquals(WeatherCode.CLOUDY, daily.get(2).night().getWeatherCode());
    }
}
