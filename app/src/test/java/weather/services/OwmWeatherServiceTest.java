package weather.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import wangdaye.com.geometricweather.weather.apis.OwmApi;
import wangdaye.com.geometricweather.weather.services.OwmWeatherService;

/**
 * Covers the OpenWeather *orchestration* rather than the conversion: the service fans three calls
 * out onto IO threads, joins them on a latch, and only then decides success or failure. Nothing
 * tested that decision before, yet it is where source-level outages come from — a required call
 * that silently degrades yields a half-built Weather, and an optional call wrongly treated as
 * required takes the source down whenever the provider hiccups.
 *
 * The payloads are the same verbatim data/2.5 captures the converter test uses.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class OwmWeatherServiceTest {

    private static final String CURRENT = "/data/2.5/weather";
    private static final String FORECAST = "/data/2.5/forecast";
    private static final String AIR = "/data/2.5/air_pollution";

    private ProviderServer mServer;
    private Context mContext;
    private Location mLocation;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();

        // The converter buckets 3-hour entries into days with a default-zone Calendar, so the JVM
        // default decides where one day ends. Pin it to the fixture's own zone, as the converter
        // test does, otherwise the daily count moves with the machine running the build.
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

        mServer = new ProviderServer("owm")
                .serving(CURRENT, "current.json")
                .serving(FORECAST, "forecast.json")
                .serving(AIR, "air_pollution.json");
    }

    @After
    public void tearDown() throws IOException {
        TimeZone.setDefault(mDefaultTimeZone);
        mServer.shutdown();
    }

    private ProviderServer.WeatherOutcome request() {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        new OwmWeatherService(mServer.api(OwmApi.class))
                .requestWeather(mContext, mLocation, outcome);
        return outcome;
    }

    /**
     * All three calls land, so the joined result must carry data from all three — the air quality
     * assertion is the load-bearing one: the service fetches air_pollution on its own thread, and
     * an earlier revision dropped that result on the floor and passed null to the converter.
     */
    @Test
    public void everyEndpointHealthyProducesAJoinedWeather() throws InterruptedException {
        Weather weather = request().awaitWeather();

        assertNotNull("three healthy endpoints must produce a Weather", weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
        assertEquals(2, weather.getDailyForecast().size());
        assertEquals(10, weather.getHourlyForecast().size());
        assertEquals(20, weather.getCurrent().getAirQuality().getAqiIndex().intValue());

        assertEquals("one call per endpoint, no retries", 3, mServer.requestCount());
    }

    /**
     * air_pollution is deliberately optional: OpenWeather serves it from a separate product that
     * fails independently of the forecast. Losing it must cost the AQI reading only — promoting it
     * to required would black the source out on a partial outage.
     */
    @Test
    public void airPollutionIsOptionalAndOnlyItsAqiIsLost() throws InterruptedException {
        mServer.failing(AIR, 500);

        Weather weather = request().awaitWeather();

        assertNotNull("a dead air_pollution endpoint must not take the source down", weather);
        assertEquals(2, weather.getDailyForecast().size());
        assertNull("without that call there is no AQI to report",
                weather.getCurrent().getAirQuality().getAqiIndex());
    }

    /** Current conditions are required: without them there is nothing to show as "now". */
    @Test
    public void currentConditionsAreRequired() throws InterruptedException {
        mServer.failing(CURRENT, 500);

        request().awaitFailure();
    }

    /** The forecast is required: it is the only source of the daily and hourly lists. */
    @Test
    public void forecastIsRequired() throws InterruptedException {
        mServer.failing(FORECAST, 500);

        request().awaitFailure();
    }

    /**
     * A 200 carrying something that is not the expected shape must degrade to "failed" rather than
     * escaping as an exception: Gson throws inside the IO coroutine, where nothing above the
     * service would catch it.
     */
    @Test
    public void malformedRequiredPayloadDegradesToFailure() throws InterruptedException {
        mServer.replying(FORECAST, "<html>gateway error</html>");

        request().awaitFailure();
    }

    /**
     * A cancelled request must not deliver a result. Callers cancel when the user leaves the screen
     * or switches city, and they expect the callback to stop coming — under RxJava, disposing the
     * subscription guaranteed that.
     */
    @Test
    public void cancellingStopsTheResultFromArriving() throws InterruptedException {
        mServer.slow(1500);

        OwmWeatherService service = new OwmWeatherService(mServer.api(OwmApi.class));
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service.requestWeather(mContext, mLocation, outcome);
        service.cancel();

        assertTrue("a cancelled request must not report an outcome",
                outcome.awaitSilence(5000));
    }
}
