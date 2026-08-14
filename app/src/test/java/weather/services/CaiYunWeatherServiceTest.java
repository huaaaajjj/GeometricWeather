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
import wangdaye.com.geometricweather.weather.apis.CaiYunApi;
import wangdaye.com.geometricweather.weather.services.CaiYunWeatherService;

/**
 * CaiYun answers everything in one call, keyed by coordinates in the URL *path* rather than a query
 * string — a shape that is easy to get wrong when the endpoint is touched, and which the v2.6
 * migration did get wrong once. Location search is not covered here: it reads the bundled Chinese
 * city table out of Room rather than going to the network.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class CaiYunWeatherServiceTest {

    private static final String WEATHER = "/v2.6/";

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
                WeatherSource.CAIYUN,
                false, false, true
        );
        mServer = new ProviderServer("caiyun").serving(WEATHER, "weather.json");
    }

    @After
    public void tearDown() throws IOException {
        TimeZone.setDefault(mDefaultTimeZone);
        mServer.shutdown();
    }

    private CaiYunWeatherService service() {
        return new CaiYunWeatherService(mServer.api(CaiYunApi.class));
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
        // The trial token caps the daily forecast at three days.
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(6, weather.getHourlyForecast().size());
        assertEquals(1, mServer.requestCount());
    }

    /**
     * v2.6 takes the position as "{lon},{lat}" in the path — longitude first — and asks for alerts
     * by query. Swapping the pair silently returns weather for somewhere else entirely.
     */
    @Test
    public void thePositionIsSentLongitudeFirstInThePath() throws InterruptedException {
        request().awaitWeather();

        String path = mServer.requestedPath(WEATHER);
        assertNotNull(path);
        // Built from the location's own floats, so the expectation cannot drift on their string
        // form; a swapped pair still fails, because the two values would land the other way round.
        assertTrue("expected lon,lat in the path, got: " + path,
                path.contains("/" + mLocation.getLongitude() + "," + mLocation.getLatitude()
                        + "/weather"));
        assertTrue("alerts must be requested, got: " + path, path.contains("alert=true"));
    }

    @Test
    public void anOutageIsReportedAsFailure() throws InterruptedException {
        mServer.failing(WEATHER, 503);

        request().awaitFailure();
    }

    /** Gson throws from inside the IO coroutine, where nothing above the service would catch it. */
    @Test
    public void aMalformedPayloadDegradesToFailure() throws InterruptedException {
        mServer.replying(WEATHER, "<html>gateway error</html>");

        request().awaitFailure();
    }

    @Test
    public void cancellingStopsTheResultFromArriving() throws InterruptedException {
        mServer.slow(1500);

        CaiYunWeatherService service = service();
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service.requestWeather(mContext, mLocation, outcome);
        service.cancel();

        assertTrue("a cancelled request must not report an outcome", outcome.awaitSilence(5000));
    }
}
