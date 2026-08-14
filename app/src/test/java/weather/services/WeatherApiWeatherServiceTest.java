package weather.services;

import static org.junit.Assert.assertEquals;
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
import wangdaye.com.geometricweather.weather.apis.WeatherApiApi;
import wangdaye.com.geometricweather.weather.services.WeatherApiWeatherService;

/**
 * WeatherAPI is the default source since 3.5.2, so every new install lands here first and its
 * failure modes are the ones users meet. One call answers everything; what matters is that a bad
 * answer is reported as a failure (which keeps the cached weather) rather than escaping as an
 * exception, and that cancelling actually stops the result.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class WeatherApiWeatherServiceTest {

    private static final String FORECAST = "/v1/forecast.json";

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
                "54517_tj",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.WEATHERAPI,
                false, false, true
        );
        mServer = new ProviderServer("weatherapi").serving(FORECAST, "forecast.json");
    }

    @After
    public void tearDown() throws IOException {
        TimeZone.setDefault(mDefaultTimeZone);
        mServer.shutdown();
    }

    private WeatherApiWeatherService service() {
        return new WeatherApiWeatherService(mServer.api(WeatherApiApi.class));
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
        // The cache key must come from the stored location, not the name the API echoes back; the
        // two drifted apart once and left this source re-fetching on every cold start.
        assertEquals("54517_tj", weather.getBase().getCityId());
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(18, weather.getHourlyForecast().size());
        assertEquals(1, mServer.requestCount());
    }

    /** The query must be the coordinates, not a place name — WeatherAPI resolves the city itself. */
    @Test
    public void theRequestIsKeyedByCoordinates() throws InterruptedException {
        request().awaitWeather();

        String path = mServer.requestedPath(FORECAST);
        assertNotNull(path);
        assertTrue("expected the coordinates in the query, got: " + path,
                path.contains("q=" + mLocation.getLatitude() + "%2C" + mLocation.getLongitude()));
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

        WeatherApiWeatherService service = service();
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service.requestWeather(mContext, mLocation, outcome);
        service.cancel();

        assertTrue("a cancelled request must not report an outcome", outcome.awaitSilence(5000));
    }
}
