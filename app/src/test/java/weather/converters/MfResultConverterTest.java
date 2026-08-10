package weather.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Daily;
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.weather.converters.MfResultConverter;
import wangdaye.com.geometricweather.weather.json.mf.MfCurrentResult;
import wangdaye.com.geometricweather.weather.json.mf.MfEphemerisResult;
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result;
import wangdaye.com.geometricweather.weather.json.mf.MfRainResult;
import wangdaye.com.geometricweather.weather.json.mf.MfWarningsResult;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * Guards the Météo France converter against the failure that made the whole source read as "down":
 * MF moved to GeoJSON v2 (every field under "properties", renamed), the DTOs still described the
 * flat v1 shape, so Gson produced an all-null object, the converter threw, and the outer catch
 * turned that into a silent "no data".
 *
 * Fixtures are verbatim captures from webservice.meteofrance.com (Paris), trimmed in length only.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class MfResultConverterTest {

    private Context mContext;
    private Location mLocation;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocation = new Location(
                "75056",
                48.8566f, 2.3522f,
                TimeZone.getTimeZone("Europe/Paris"),
                "FR - France", "75", "Paris", "",
                null,
                WeatherSource.MF,
                false, false, false
        );
    }

    /** Mirrors the UTC date handling ApiModule installs for the MF Retrofit instance. */
    private static Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, type, ctx) -> {
                    String text = json.getAsString();
                    for (String pattern : new String[]{
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
                        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                        format.setLenient(false);
                        try {
                            return format.parse(text);
                        } catch (ParseException ignored) {
                            // try the next pattern
                        }
                    }
                    return null;
                })
                .create();
    }

    private <T> T load(String name, Class<T> clazz) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("mf/" + name);
        assertNotNull("fixture missing: " + name, in);
        return gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), clazz);
    }

    private Weather convert(MfForecastV2Result forecast, MfCurrentResult current,
                            MfRainResult rain, MfWarningsResult warnings) {
        WeatherService.WeatherResultWrapper wrapper = MfResultConverter.convert(
                mContext, mLocation, current, forecast,
                load("ephemeris.json", MfEphemerisResult.class), rain, warnings, null);
        return wrapper.getResult();
    }

    private Weather convertAll() {
        return convert(
                load("forecast.json", MfForecastV2Result.class),
                load("observation.json", MfCurrentResult.class),
                load("rain.json", MfRainResult.class),
                load("warning.json", MfWarningsResult.class));
    }

    /**
     * The v1 DTOs deserialized to an all-null object against the v2 payload. Every assertion here
     * would have been null/zero before the DTOs were reshaped.
     */
    @Test
    public void v2PayloadConvertsCompletely() {
        Weather weather = convertAll();
        assertNotNull("the v2 GeoJSON payload must convert", weather);

        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());

        List<Daily> daily = weather.getDailyForecast();
        assertEquals(4, daily.size());
        List<Hourly> hourly = weather.getHourlyForecast();
        assertEquals(26, hourly.size());

        // properties.gridded: T=29.3, wind_speed=2 m/s -> km/h, description "Sunny".
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertEquals("Sunny", weather.getCurrent().getWeatherText());
        assertEquals(2 * 3.6f, weather.getCurrent().getWind().getSpeed(), 0.01f);

        // daily_forecast[0]: T_min 20.6 / T_max 32.1, uv_index 6.
        assertEquals(32, daily.get(0).day().getTemperature().getTemperature());
        assertEquals(6, daily.get(0).getUV().getIndex().intValue());
        assertEquals("Ciel clair", daily.get(0).day().getWeatherText());

        // sunrise/sunset land on the day they belong to, and give a plausible summer day length.
        assertNotNull(daily.get(0).sun().getRiseDate());
        assertNotNull(daily.get(0).sun().getSetDate());
        assertTrue(daily.get(0).getHoursOfSun() > 14 && daily.get(0).getHoursOfSun() < 15);
    }

    /**
     * The shared Gson date format drops the trailing ".000Z" and reads the value as device-local,
     * which shifts every timestamp by the device's UTC offset. Pin the first hour to its true
     * instant so a regression to the shared format fails here rather than on a user's screen.
     */
    @Test
    public void timestampsAreParsedAsUtc() {
        Weather weather = convertAll();
        assertNotNull(weather);

        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));

        // forecast[0].time is "2026-08-10T09:00:00.000Z".
        assertEquals("2026-08-10T09:00:00",
                utc.format(weather.getHourlyForecast().get(0).getDate()));
        // update_time is "2026-08-10T11:45:00.000Z". Base carries it as the *publish* date; the
        // update date is device time ("when we refreshed"), as in every other converter.
        assertEquals("2026-08-10T11:45:00",
                utc.format(weather.getBase().getPublishDate()));
    }

    /** rain_intensity 1 means "no rain"; the steps are 5 minutes apart, counted from the first. */
    @Test
    public void minutelyComesFromRainIntensity() {
        Weather weather = convertAll();
        assertNotNull(weather);

        assertEquals(9, weather.getMinutelyForecast().size());
        assertEquals(0, weather.getMinutelyForecast().get(0).getMinuteInterval());
        assertEquals(5, weather.getMinutelyForecast().get(1).getMinuteInterval());
        assertEquals("No rain", weather.getMinutelyForecast().get(0).getWeatherText());
    }

    /** phenomenons_items with a colour above 1 (green) become alerts; green ones are dropped. */
    @Test
    public void warningsAboveGreenBecomeAlerts() {
        Weather weather = convertAll();
        assertNotNull(weather);

        // The fixture carries one yellow (colour id 2, phenomenon 6 = Canicule) among green ones.
        assertEquals(1, weather.getAlertList().size());
        assertEquals("Canicule", weather.getAlertList().get(0).getType());
    }

    /**
     * Optional calls (observation, rain, warnings) are allowed to fail without sinking the whole
     * refresh — the forecast alone must still produce a Weather.
     */
    @Test
    public void forecastAloneStillConverts() {
        Weather weather = convert(
                load("forecast.json", MfForecastV2Result.class), null, null, null);

        assertNotNull("a missing observation/rain/warning must not fail the refresh", weather);
        assertFalse(weather.getDailyForecast().isEmpty());
        assertTrue(weather.getMinutelyForecast().isEmpty());
        assertTrue(weather.getAlertList().isEmpty());
    }

    /**
     * A forecast with no usable day must be reported as a failure rather than as an empty list:
     * 76 call sites across the UI assume getDailyForecast().get(0) exists.
     */
    @Test
    public void emptyDailyIsRejected() {
        MfForecastV2Result forecast = load("forecast.json", MfForecastV2Result.class);
        forecast.properties.dailyForecast = null;

        assertNull(convert(forecast, load("observation.json", MfCurrentResult.class), null, null));
    }
}
