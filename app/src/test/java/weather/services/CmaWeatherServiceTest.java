package weather.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.lang.reflect.Field;
import java.util.TimeZone;

import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.weather.apis.CmaApi;
import wangdaye.com.geometricweather.weather.services.CmaWeatherService;

/**
 * CMA is keyed by station id and has no coordinates-to-station endpoint, so a location whose
 * cityId is not a CMA station — a current position, or one re-sourced from another provider by a
 * global source switch — has to be resolved before it can be asked for weather.
 *
 * The failure this pins is subtle and cost a release to find: CMA answers an unknown station id
 * with {@code "data": ""} — an empty *string* where an object belongs — so Gson throws, and the
 * throw used to escape past the retry to the outermost catch. The retry existed but had never once
 * run, and the source simply reported "no data" for every searched or re-sourced city.
 *
 * Fixtures are synthetic: weather.cma.cn's WAF drops the TLS handshake from a development machine
 * (curl and python both get UNEXPECTED_EOF) while phones are served normally, so these payloads are
 * built to the documented shapes rather than captured.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class CmaWeatherServiceTest {

    private static final String VIEW = "/api/weather/view";
    private static final String NATIONAL_MAP = "/api/map/weather";
    private static final String HOURLY_HTML = "/web/weather/";

    /** What CMA answers for a station id it does not know: data is an empty string, not an object. */
    private static final String UNKNOWN_STATION = "{\"msg\":\"OK\",\"code\":200,\"data\":\"\"}";

    private ProviderServer mServer;
    private Context mContext;
    private Location mForeignId;
    private Location mKnownStation;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        clearStationCache();
        mServer = new ProviderServer("cma");

        // 南开 filed under a WeatherAPI-style cityId: exactly what a global source switch leaves
        // behind, and the case that used to yield "no data" forever.
        mForeignId = nankaiWithId("54517_tj");
        // The same place already filed under its CMA station id.
        mKnownStation = nankaiWithId("54517");
    }

    private static Location nankaiWithId(String cityId) {
        return new Location(
                cityId,
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.CMA,
                false, false, true
        );
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
        clearStationCache();
    }

    /**
     * The national station list is cached for the process lifetime, which would otherwise leak
     * between test methods and hide whether a given case actually fetched it.
     */
    private static void clearStationCache() throws Exception {
        Field stations = CmaWeatherService.class.getDeclaredField("stations");
        stations.setAccessible(true);
        stations.set(null, null);
    }

    private ProviderServer.WeatherOutcome request(Location location) {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        new CmaWeatherService(mServer.api(CmaApi.class))
                .requestWeather(mContext, location, outcome);
        return outcome;
    }

    /** A cityId that is already a CMA station needs no resolution and no station list. */
    @Test
    public void aKnownStationIsAskedOnceAndDirectly() throws InterruptedException {
        mServer.serving(VIEW, "weather.json").serving(HOURLY_HTML, "hourly.html");

        Weather weather = request(mKnownStation).awaitWeather();

        assertNotNull(weather);
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals(1, mServer.requestCount(VIEW));
        assertNull("a station id that already works must not trigger resolution",
                mServer.requestedPath(NATIONAL_MAP));
    }

    /**
     * The regression test proper. The first ask is rejected with {@code data: ""}; the service must
     * survive the Gson failure, resolve the nearest station from the national map, and ask again.
     */
    @Test
    public void anUnknownStationIdIsResolvedByCoordinatesAndRetried() throws InterruptedException {
        mServer.route(VIEW, mServer.body(UNKNOWN_STATION), mServer.payload("weather.json"))
                .serving(NATIONAL_MAP, "national.json")
                .serving(HOURLY_HTML, "hourly.html");

        Weather weather = request(mForeignId).awaitWeather();

        assertNotNull("the retry after station resolution must produce weather", weather);
        assertEquals(3, weather.getDailyForecast().size());
        assertEquals("the stored id, then the resolved one", 2, mServer.requestCount(VIEW));
        assertNotNull("resolution must go through the national station map",
                mServer.requestedPath(NATIONAL_MAP));
    }

    /**
     * Resolution must be geographic, not IP-based: a mobile carrier's egress can sit in another
     * province, which is how a 南开 location once reported weather for 开州. The nearest station to
     * the fixture's coordinates is 54517, and the retry must carry exactly that id.
     */
    @Test
    public void theRetryUsesTheNearestStationToTheCoordinates() throws InterruptedException {
        mServer.route(VIEW, mServer.body(UNKNOWN_STATION), mServer.payload("weather.json"))
                .serving(NATIONAL_MAP, "national.json")
                .serving(HOURLY_HTML, "hourly.html");

        request(mForeignId).awaitWeather();

        // The first view request carries the stored id; the second is the retry under test.
        String retry = mServer.requestedPaths(VIEW).get(1);
        assertTrue("the retry must name the nearest station (54517), got: " + retry,
                retry.contains("stationid=54517"));
        assertFalse("IP resolution is a last resort, not the first move",
                retry.endsWith("stationid="));
    }

    /**
     * The hourly page is scraped per station, and it must use the station the data actually came
     * from — scraping the stored (rejected) id yields nothing, which is how the hourly strip went
     * missing for every re-sourced city.
     */
    @Test
    public void theHourlyScrapeFollowsTheStationTheDataCameFrom() throws InterruptedException {
        mServer.route(VIEW, mServer.body(UNKNOWN_STATION), mServer.payload("weather.json"))
                .serving(NATIONAL_MAP, "national.json")
                .serving(HOURLY_HTML, "hourly.html");

        Weather weather = request(mForeignId).awaitWeather();

        String htmlPath = mServer.requestedPath(HOURLY_HTML);
        assertNotNull("the hourly page must be fetched", htmlPath);
        assertTrue("expected the resolved station's page, got: " + htmlPath,
                htmlPath.startsWith("/web/weather/54517.html"));
        assertFalse("the scrape produced no hourly data", weather.getHourlyForecast().isEmpty());
    }

    /** The hourly scrape is best effort: losing the page costs the hourly strip and nothing else. */
    @Test
    public void aFailedHourlyScrapeStillLeavesTheDailyForecast() throws InterruptedException {
        mServer.serving(VIEW, "weather.json").failing(HOURLY_HTML, 503);

        Weather weather = request(mKnownStation).awaitWeather();

        assertNotNull("an HTML outage must not take the source down", weather);
        assertEquals(3, weather.getDailyForecast().size());
        assertTrue(weather.getHourlyForecast().isEmpty());
    }

    /** With no station resolvable at all there is nothing to show — report failure, not empty. */
    @Test
    public void anUnresolvableLocationReportsFailure() throws InterruptedException {
        mServer.replying(VIEW, UNKNOWN_STATION).failing(NATIONAL_MAP, 503);

        request(mForeignId).awaitFailure();
    }
}
