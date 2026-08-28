package weather.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoAirQualityApi;
import wangdaye.com.geometricweather.weather.apis.OpenMeteoApi;
import wangdaye.com.geometricweather.weather.services.OpenMeteoWeatherService;

/**
 * Open-Meteo answers everything in one call, so its orchestration is the whole contract a caller
 * depends on: a usable response becomes weather, anything else becomes a reported failure rather
 * than an exception escaping the IO thread, and a cancelled request goes quiet.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class OpenMeteoWeatherServiceTest {

    private static final String FORECAST = "/v1/forecast";
    private static final String AIR_QUALITY = "/v1/air-quality";

    private ProviderServer mServer;
    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
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
        mServer = new ProviderServer("openmeteo")
                .serving(FORECAST, "forecast.json")
                .serving(AIR_QUALITY, "air_beijing.json");
    }

    @After
    public void tearDown() throws IOException {
        TimeZone.setDefault(mDefaultTimeZone);
        mServer.shutdown();
    }

    private OpenMeteoWeatherService service() {
        return new OpenMeteoWeatherService(
                mServer.api(OpenMeteoApi.class),
                mServer.api(OpenMeteoAirQualityApi.class));
    }

    private ProviderServer.WeatherOutcome request() {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service().requestWeather(mContext, mLocation, outcome);
        return outcome;
    }

    @Test
    public void aHealthyResponseProducesWeather() throws InterruptedException {
        Weather weather = request().awaitWeather();

        assertNotNull(weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(3 * 24, weather.getHourlyForecast().size());
        // pm2.5 120.1 μg/m³ from the air quality fixture, through the service and converter.
        assertEquals(157, weather.getCurrent().getAirQuality().getAqiIndex().intValue());
        assertEquals("forecast and air quality answer in parallel", 2, mServer.requestCount());
    }

    /** The air quality API rejects forecast_days > 7 outright instead of clamping. */
    @Test
    public void theAirQualityHorizonStaysWithinWhatTheEndpointAccepts() throws InterruptedException {
        request().awaitWeather();

        String path = mServer.requestedPath(AIR_QUALITY);
        assertNotNull(path);
        assertTrue("air quality horizon rejected above 7: " + path,
                path.contains("forecast_days=7"));
    }

    /** Air quality is the optional call: its outage must not sink the refresh. */
    @Test
    public void anAirQualityOutageDoesNotSinkTheRefresh() throws InterruptedException {
        mServer.failing(AIR_QUALITY, 503);

        Weather weather = request().awaitWeather();

        assertNotNull(weather);
        assertFalse(weather.getCurrent().getAirQuality().isValidIndex());
    }

    /**
     * past_days must stay 0. The converter never splits a past day off, so asking for one puts
     * yesterday at dailyForecast[0] and its 24 hours at the head of hourlyForecast — and the app
     * reads index 0 as "today"/"now" everywhere, including the history row it writes to the DB.
     */
    @Test
    public void noPastDaysAreEverRequested() throws InterruptedException {
        request().awaitWeather();

        String path = mServer.requestedPath(FORECAST);
        assertNotNull(path);
        assertTrue("past days leak into index 0: " + path, path.contains("past_days=0"));
        assertTrue("the 16-day horizon is why this source leads: " + path,
                path.contains("forecast_days=16"));
    }

    @Test
    public void anOutageIsReportedAsFailure() throws InterruptedException {
        mServer.failing(FORECAST, 503);

        request().awaitFailure();
    }

    /** Gson throws from inside the IO coroutine, where nothing above the service would catch it. */
    @Test
    public void aMalformedPayloadDegradesToFailure() throws InterruptedException {
        mServer.replying(FORECAST, "<html>gateway error</html>");

        request().awaitFailure();
    }

    @Test
    public void cancellingStopsTheResultFromArriving() throws InterruptedException {
        mServer.slow(1500);

        OpenMeteoWeatherService service = service();
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service.requestWeather(mContext, mLocation, outcome);
        service.cancel();

        assertTrue("a cancelled request must not report an outcome", outcome.awaitSilence(5000));
    }
}
