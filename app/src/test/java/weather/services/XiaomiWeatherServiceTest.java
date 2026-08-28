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
import wangdaye.com.geometricweather.weather.apis.XiaomiApi;
import wangdaye.com.geometricweather.weather.services.XiaomiWeatherService;

/**
 * Xiaomi is the only source here whose refresh is two dependent steps: {@code weather/all} cannot be
 * asked anything until {@code location/city/geo} has produced a {@code locationKey}, and there is
 * nowhere to cache that key. So the orchestration is what these tests pin down, not the conversion.
 *
 * The one that matters most is the {@code isGlobal} flag. It has to agree with the key's prefix —
 * {@code weathercn:} means China, {@code accu:} means everywhere else — and disagreeing does not
 * produce an error: the backend answers 200 with an empty forecast. A regression there would look
 * exactly like "the source stopped having data", the same shape as the CMA {@code data:""} bug that
 * once made its retry path unreachable.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class XiaomiWeatherServiceTest {

    private static final String GEO = "/location/city/geo";
    private static final String FORECAST = "/weather/all";
    private static final String MINUTELY = "/weather/xm/forecast/minutely";

    private ProviderServer mServer;
    private Context mContext;
    private Location mBeijing;
    private Location mParis;
    private TimeZone mDefaultTimeZone;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        // Neither of the two locations, so anything that reads the device clock's zone instead of the
        // location's own shows up here rather than passing by luck.
        mDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        mBeijing = new Location(
                "101011600_bj",
                39.9042f, 116.4074f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "北京市", "北京市", "东城",
                null,
                WeatherSource.XIAOMI,
                false, false, true
        );
        mParis = new Location(
                "1094121_fr",
                48.8566f, 2.3522f,
                TimeZone.getTimeZone("Europe/Paris"),
                "France", "Île-de-France", "Paris", "",
                null,
                WeatherSource.XIAOMI,
                false, false, false
        );
        mServer = new ProviderServer("xiaomi")
                .serving(GEO, "geo_beijing.json")
                .serving(FORECAST, "all_beijing.json")
                .serving(MINUTELY, "minutely_beijing.json");
    }

    @After
    public void tearDown() throws IOException {
        TimeZone.setDefault(mDefaultTimeZone);
        mServer.shutdown();
    }

    private XiaomiWeatherService service() {
        return new XiaomiWeatherService(mServer.api(XiaomiApi.class));
    }

    private ProviderServer.WeatherOutcome request(Location location) {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service().requestWeather(mContext, location, outcome);
        return outcome;
    }

    @Test
    public void aRefreshResolvesTheKeyThenAsksForTheForecast() throws InterruptedException {
        Weather weather = request(mBeijing).awaitWeather();

        assertNotNull(weather);
        assertEquals(mBeijing.getCityId(), weather.getBase().getCityId());
        assertEquals(15, weather.getDailyForecast().size());
        assertEquals(23, weather.getHourlyForecast().size());
        assertEquals(120, weather.getMinutelyForecast().size());
        // Exactly three calls: the resolve, then the two data calls it unlocks.
        assertEquals(3, mServer.requestCount());
        assertEquals(1, mServer.requestCount(GEO));

        // The resolve is asked in WGS-84, straight from the location. Built from the location's own
        // floats so the expectation cannot drift on their string form, while a swapped pair still
        // fails because the two values would land the other way round.
        String geo = mServer.requestedPath(GEO);
        assertNotNull(geo);
        assertTrue("expected the caller's coordinates, got: " + geo,
                geo.contains("latitude=" + (double) mBeijing.getLatitude())
                        && geo.contains("longitude=" + (double) mBeijing.getLongitude()));

        // Both data calls must carry the key the resolve produced, not the location's cityId.
        String forecast = mServer.requestedPath(FORECAST);
        String minutely = mServer.requestedPath(MINUTELY);
        assertNotNull(forecast);
        assertNotNull(minutely);
        assertTrue("the forecast lost the resolved key: " + forecast,
                forecast.contains("locationKey=weathercn%3A101011600"));
        assertTrue("the minutely call lost the resolved key: " + minutely,
                minutely.contains("locationKey=weathercn%3A101011600"));
        assertTrue("the forecast must ask for the full 15 days: " + forecast,
                forecast.contains("days=15"));
        // Xiaomi's own fixed credentials; absent ones are rejected.
        assertTrue(forecast.contains("appKey=") && forecast.contains("sign="));
        assertTrue(minutely.contains("appKey=") && minutely.contains("sign="));
    }

    /**
     * A {@code weathercn:} key with {@code isGlobal=true} comes back 200 and empty, so this flag is
     * only ever observable as missing data.
     */
    @Test
    public void aChinaKeyIsSentWithIsGlobalFalse() throws InterruptedException {
        request(mBeijing).awaitWeather();

        assertTrue(mServer.requestedPath(FORECAST).contains("isGlobal=false"));
        assertTrue(mServer.requestedPath(MINUTELY).contains("isGlobal=false"));
    }

    /** The other half of the same rule: abroad the resolve hands back an {@code accu:} key. */
    @Test
    public void anAccuKeyFlipsIsGlobalToTrue() throws InterruptedException {
        mServer.serving(GEO, "geo_paris.json").serving(FORECAST, "all_paris.json");
        // Minute-level precipitation is China-only; abroad this endpoint has nothing to say.
        mServer.failing(MINUTELY, 404);

        Weather weather = request(mParis).awaitWeather();

        assertNotNull(weather);
        String forecast = mServer.requestedPath(FORECAST);
        String minutely = mServer.requestedPath(MINUTELY);
        assertTrue("the global path must announce itself: " + forecast,
                forecast.contains("isGlobal=true"));
        assertTrue("the global path must announce itself: " + minutely,
                minutely.contains("isGlobal=true"));
        assertTrue(forecast.contains("locationKey=accu%3A1094121"));
    }

    /**
     * Without a key the data calls cannot be formed at all, so the refresh has to stop at the
     * resolve rather than send a request that would answer 200-and-empty.
     */
    @Test
    public void aRejectedResolveStopsBeforeTheDataCalls() throws InterruptedException {
        mServer.replying(GEO, "[{\"status\":-1}]");

        request(mBeijing).awaitFailure();

        assertEquals(1, mServer.requestCount());
        assertNull(mServer.requestedPath(FORECAST));
        assertNull(mServer.requestedPath(MINUTELY));
    }

    @Test
    public void anEmptyResolveIsReportedAsFailure() throws InterruptedException {
        mServer.replying(GEO, "[]");

        request(mBeijing).awaitFailure();

        assertEquals(1, mServer.requestCount());
    }

    @Test
    public void aResolveOutageIsReportedAsFailure() throws InterruptedException {
        mServer.failing(GEO, 503);

        request(mBeijing).awaitFailure();

        assertNull(mServer.requestedPath(FORECAST));
    }

    /** Minute-level precipitation is a bonus; losing it must not cost the whole refresh. */
    @Test
    public void aMinutelyOutageDoesNotSinkTheRefresh() throws InterruptedException {
        mServer.failing(MINUTELY, 500);

        Weather weather = request(mBeijing).awaitWeather();

        assertEquals(15, weather.getDailyForecast().size());
        assertTrue(weather.getMinutelyForecast().isEmpty());
        // The card subtitle comes from that call, so it is absent rather than stale.
        assertNull(weather.getCurrent().getHourlyForecast());
    }

    /**
     * The point of the cache: the second refresh at the same spot must skip the resolve entirely
     * (geo answered once, the data calls twice). Fresh Robolectric prefs per test keep this from
     * leaking between cases.
     */
    @Test
    public void aCachedKeySkipsTheResolveOnTheNextRefresh() throws InterruptedException {
        request(mBeijing).awaitWeather();
        Weather again = request(mBeijing).awaitWeather();

        assertNotNull(again);
        assertEquals(2, mServer.requestCount(FORECAST));
        assertEquals("the resolve must run once, not once per refresh", 1, mServer.requestCount(GEO));
    }

    /**
     * A cached key that stops answering must not brick the source: the data call fails with it, the
     * cache entry is dropped, the full resolve runs again, and the third data call succeeds.
     */
    @Test
    public void aStaleCachedKeyIsDroppedAndTheSourceRecovers() throws InterruptedException {
        mServer.route(FORECAST,
                mServer.payload("all_beijing.json"), mServer.status(503),
                mServer.payload("all_beijing.json"));

        request(mBeijing).awaitWeather();  // healthy: key resolved and cached
        Weather weather = request(mBeijing).awaitWeather();  // cached key gets the 503

        assertNotNull("the stale key must not sink the refresh", weather);
        assertEquals("the stale-key path re-resolved", 2, mServer.requestCount(GEO));
        assertEquals("cached attempt + one retry after resolving", 3, mServer.requestCount(FORECAST));
    }

    /** Gson throws from inside the IO coroutine, where nothing above the service would catch it. */
    @Test
    public void aMalformedForecastDegradesToFailure() throws InterruptedException {
        mServer.replying(FORECAST, "<html>gateway error</html>");

        request(mBeijing).awaitFailure();
    }

    @Test
    public void cancellingStopsTheResultFromArriving() throws InterruptedException {
        mServer.slow(1500);

        XiaomiWeatherService service = service();
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        service.requestWeather(mContext, mBeijing, outcome);
        service.cancel();

        assertTrue("a cancelled request must not report an outcome", outcome.awaitSilence(5000));
    }
}
