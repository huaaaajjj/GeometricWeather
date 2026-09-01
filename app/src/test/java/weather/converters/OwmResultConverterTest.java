package weather.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Astro;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode;
import wangdaye.com.geometricweather.weather.converters.OwmResultConverter;
import wangdaye.com.geometricweather.weather.json.owm.OwmAirPollutionResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmCurrentResult;
import wangdaye.com.geometricweather.weather.json.owm.OwmForecastResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * OpenWeather is the only source whose daily forecast is *derived* rather than reported: the free
 * 2.5 endpoints give 3-hour steps, and the converter buckets them into days itself. That bucketing
 * is what produced the empty-daily crash fixed in the 3.5.x line, so it is what these tests pin.
 *
 * Fixtures are real data/2.5 responses for the Tianjin 南开 point (metric units, as the service
 * requests), with the forecast trimmed from 40 entries to the first 10 — which is exactly two
 * complete Asia/Shanghai days, so the bucket boundaries stay meaningful.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class OwmResultConverterTest {

    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        // The converter buckets by the response's own offset now, but the display-side formatting
        // these tests read (getWeek, getShortDate) still goes through the JVM default. Pin it.
        mDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        mLocation = new Location(
                "54517_tj",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.OWM,
                false, false, true
        );
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mDefaultTimeZone);
    }

    private <T> T load(String name, Class<T> clazz) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("owm/" + name);
        assertNotNull("fixture missing: " + name, in);
        return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), clazz);
    }

    private Weather convertFixture() {
        WeatherService.WeatherResultWrapper wrapper = OwmResultConverter.convert(
                mContext, mLocation,
                load("current.json", OwmCurrentResult.class),
                load("forecast.json", OwmForecastResult.class),
                load("air_pollution.json", OwmAirPollutionResult.class));
        return wrapper.getResult();
    }

    /**
     * Days are bucketed by the *response's* offset, not the device's. The two used to be the same
     * line of code — a default-zone Calendar — so every foreign place had its days cut at the
     * phone's midnight and dated an hour or more off.
     */
    @Test
    public void daysAreBucketedByTheResponseOffsetNotTheDeviceZone() {
        List<Daily> atPlusEight = dailyForOffset(8 * 3600);
        List<Daily> atPlusNine = dailyForOffset(9 * 3600);

        // The fixture's second step is 23:00 on the 11th at +08:00 and 00:00 on the 12th at +09:00,
        // so the same ten steps are two days in one zone and three in the other.
        assertEquals(2, atPlusEight.size());
        assertEquals("the 23:00 step is already the next day one zone east", 3, atPlusNine.size());
        for (Daily daily : atPlusEight) {
            assertEquals("a day must start at midnight where the place is (+08:00)",
                    0, (daily.getTime() / 1000 + 8 * 3600) % 86400);
        }
        for (Daily daily : atPlusNine) {
            assertEquals("a day must start at midnight where the place is (+09:00)",
                    0, (daily.getTime() / 1000 + 9 * 3600) % 86400);
        }
        // Midnight one zone east is an hour earlier in absolute terms, so the boundary has to move.
        assertNotEquals(atPlusEight.get(0).getTime(), atPlusNine.get(0).getTime());
    }

    private List<Daily> dailyForOffset(int offsetSeconds) {
        OwmCurrentResult current = load("current.json", OwmCurrentResult.class);
        current.timezone = offsetSeconds;
        Weather weather = OwmResultConverter.convert(
                mContext, mLocation, current,
                load("forecast.json", OwmForecastResult.class), null).getResult();
        assertNotNull(weather);
        return weather.getDailyForecast();
    }

    @Test
    public void baseCityIdComesFromLocation() {
        Weather weather = convertFixture();

        assertNotNull(weather);
        assertEquals("54517_tj", weather.getBase().getCityId());
    }

    @Test
    public void currentComesFromTheCurrentEndpoint() {
        Weather weather = convertFixture();
        assertNotNull(weather);

        // Unlike the other converters this one rounds rather than truncates: 29.25 -> 29,
        // 32.15 -> 32.
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertEquals(32, weather.getCurrent().getTemperature().getRealFeelTemperature().intValue());
        assertEquals(64f, weather.getCurrent().getRelativeHumidity(), 0.01f);
        assertEquals(1005f, weather.getCurrent().getPressure(), 0.01f);
        // OWM's metric units are m/s and metres; the model is km/h and km.
        assertEquals(5.11f * 3.6f, weather.getCurrent().getWind().getSpeed(), 0.01f);
        assertEquals(10f, weather.getCurrent().getVisibility(), 0.01f);
        // air_pollution is a separate call whose result used to be accepted and discarded.
        assertTrue(weather.getCurrent().getAirQuality().isValid());
        // Upstream main.aqi is 2, but that is a 1..5 category — the model's index is a 0..500
        // China AQI, so it is computed from the concentrations instead: pm2.5 11.12 -> 16,
        // pm10 20.04 -> 20, worst pollutant wins. Writing the category straight through used to
        // put every reading in the <=50 band, i.e. green no matter how bad the air was.
        assertEquals(20, weather.getCurrent().getAirQuality().getAqiIndex().intValue());
        assertEquals("Fresh air", weather.getCurrent().getAirQuality().getAqiText());
        assertEquals(11.12f, weather.getCurrent().getAirQuality().getPM25(), 0.01f);
        assertEquals("小雨", weather.getCurrent().getWeatherText());
        assertEquals(WeatherCode.RAIN, weather.getCurrent().getWeatherCode());
        // Base timestamps come from the observation's dt, not from the clock.
        assertEquals(1786446932000L, weather.getBase().getPublishTime());
    }

    /**
     * The 3-hour steps must collapse into whole local days. The fixture's 10 entries span
     * 2026-08-11 20:00 through 2026-08-12 23:00 Asia/Shanghai, i.e. a 2-entry tail day followed by
     * a full 8-entry day; getting the boundary wrong shows up as 1 or 3 buckets here.
     */
    @Test
    public void threeHourStepsBucketIntoLocalDays() {
        Weather weather = convertFixture();
        assertNotNull(weather);

        assertEquals(2, weather.getDailyForecast().size());
        assertEquals(10, weather.getHourlyForecast().size());

        Daily first = weather.getDailyForecast().get(0);
        assertEquals("2026-08-11", format(first.getDate(), "yyyy-MM-dd"));
        // Both of day 1's entries fall at 20:00 and 23:00 local, so there is no daytime sample at
        // all and the day half has to fall back to the min/max midpoint instead of dividing by 0.
        assertEquals(28, first.day().getTemperature().getTemperature());
        assertEquals(28, first.night().getTemperature().getTemperature());

        Daily second = weather.getDailyForecast().get(1);
        assertEquals("2026-08-12", format(second.getDate(), "yyyy-MM-dd"));
        // Day = mean of the 08/11/14/17h samples (24.64, 23.91, 23.71, 23.3) -> 23.89 -> 24.
        assertEquals(24, second.day().getTemperature().getTemperature());
        // Night = mean of the 02/05/20/23h samples (26.17, 25.24, 22.58, 22.6) -> 24.15 -> 24.
        assertEquals(24, second.night().getTemperature().getTemperature());
        assertEquals(WeatherCode.RAIN, second.day().getWeatherCode());
        assertEquals("小雨", second.day().getWeatherText());

        // Day starts at local midnight, not at the first sample's time.
        assertEquals("2026-08-12 00:00", format(second.getDate(), "yyyy-MM-dd HH:mm"));
    }

    /**
     * A partial day with no sample on one side of the 6..18 split falls back to the (min+max)/2
     * midpoint, and day 1 of the fixture is exactly that case. The running max used to be seeded
     * with Double.MIN_VALUE — the smallest *positive* double, not the most negative one — so an
     * all-sub-zero day never moved it off ~0 and the midpoint came out far too warm. A summer
     * fixture cannot show this, hence the shift below freezing.
     */
    @Test
    public void subZeroDayKeepsItsRealMaximum() {
        OwmForecastResult forecast = load("forecast.json", OwmForecastResult.class);
        for (OwmForecastResult.ListBean entry : forecast.list) {
            entry.main.temp -= 40;
            entry.main.temp_min -= 40;
            entry.main.temp_max -= 40;
        }

        WeatherService.WeatherResultWrapper wrapper = OwmResultConverter.convert(
                mContext, mLocation, load("current.json", OwmCurrentResult.class), forecast, null);
        Weather weather = wrapper.getResult();
        assertNotNull(weather);

        // Day 1 holds only the 20:00 and 23:00 local steps: min -12.29, max -11.86, midpoint
        // -12.075 -> -12. With the old seed the max stayed at ~0 and this read -6.
        assertEquals(-12,
                weather.getDailyForecast().get(0).day().getTemperature().getTemperature());
        // The night half averages its own samples, so it lands on -12 either way.
        assertEquals(-12,
                weather.getDailyForecast().get(0).night().getTemperature().getTemperature());
    }

    @Test
    public void hourlyKeepsProviderOrderAndValues() {
        Weather weather = convertFixture();
        assertNotNull(weather);

        assertEquals("2026-08-11 20:00",
                format(weather.getHourlyForecast().get(0).getDate(), "yyyy-MM-dd HH:mm"));
        assertEquals(28, weather.getHourlyForecast().get(0).getTemperature().getTemperature());
        // rain.3h is the only precipitation figure the free endpoint gives, and its JSON key is
        // "3h" — a name no Java field can carry, so it only binds via @SerializedName.
        assertEquals(0.3f,
                weather.getHourlyForecast().get(0).getPrecipitation().getTotal(), 0.01f);
        // pop is a 0..1 probability upstream and a percentage in the model.
        assertEquals(21f,
                weather.getHourlyForecast().get(0).getPrecipitationProbability().getTotal(), 0.01f);
        // sys.pod says "n" for this 20:00 local step; it used to be hardcoded to daytime.
        assertFalse(weather.getHourlyForecast().get(0).isDaylight());
        assertTrue(weather.getHourlyForecast().get(4).isDaylight());
        assertEquals("2026-08-12 23:00",
                format(weather.getHourlyForecast().get(9).getDate(), "yyyy-MM-dd HH:mm"));
        assertEquals(WeatherCode.CLOUDY, weather.getHourlyForecast().get(1).getWeatherCode());
    }

    /**
     * An empty forecast must not fail loudly here — but it does produce an empty daily list, which
     * is exactly the state that used to crash DetailsAdapter/LocationModel at .get(0). The guards
     * in WeatherHelper.requestWeatherSuccess and DatabaseHelper.readWeather are what stop it, so
     * this test pins the shape those guards expect rather than a converter-side rejection.
     */
    @Test
    public void emptyForecastYieldsEmptyDailyList() {
        OwmForecastResult forecast = load("forecast.json", OwmForecastResult.class);
        forecast.list.clear();

        WeatherService.WeatherResultWrapper wrapper = OwmResultConverter.convert(
                mContext, mLocation, load("current.json", OwmCurrentResult.class), forecast, null);

        assertNotNull(wrapper.getResult());
        assertTrue(wrapper.getResult().getDailyForecast().isEmpty());
        assertTrue(wrapper.getResult().getHourlyForecast().isEmpty());
    }

    /**
     * No current observation means no Weather at all: the wrapper's result must be null so the
     * service reports failure and the cached weather survives.
     */
    @Test
    public void missingCurrentIsRejected() {
        OwmForecastResult forecast = load("forecast.json", OwmForecastResult.class);

        assertNull(OwmResultConverter.convert(mContext, mLocation, null, forecast, null).getResult());

        OwmCurrentResult current = load("current.json", OwmCurrentResult.class);
        current.weather.clear();
        assertNull(OwmResultConverter.convert(mContext, mLocation, current, forecast, null).getResult());
    }

    /**
     * The observation's sys.sunrise/sunset used to be dropped on the floor: with every astro
     * empty, isDaylight() fell back to a hardcoded 06:00–18:00 and mispainted the theme, widgets
     * and notifications at high latitudes. They belong to the location's *current* day — 06:20 /
     * 19:13 +08:00 in the fixture — so they must land on that day's bucket (not on whichever day
     * comes first) and the later days stay empty, the free forecast carrying no astro at all.
     */
    @Test
    public void currentDayCarriesTheSunTimesFromTheCurrentEndpoint() {
        Weather weather = convertFixture();
        assertNotNull(weather);

        Astro firstSun = weather.getDailyForecast().get(0).sun();
        assertEquals(1786396828000L, firstSun.getRiseDate().getTime());
        assertEquals(1786446782000L, firstSun.getSetDate().getTime());

        assertNull(weather.getDailyForecast().get(1).sun().getRiseDate());

        // A zero timestamp is no timestamp: degrade to the empty astro, never to the epoch.
        OwmCurrentResult current = load("current.json", OwmCurrentResult.class);
        current.sys.sunrise = 0;
        Weather withoutSun = OwmResultConverter.convert(
                mContext, mLocation, current,
                load("forecast.json", OwmForecastResult.class), null).getResult();
        assertNotNull(withoutSun);
        assertNull(withoutSun.getDailyForecast().get(0).sun().getRiseDate());
    }

    private String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }
}
