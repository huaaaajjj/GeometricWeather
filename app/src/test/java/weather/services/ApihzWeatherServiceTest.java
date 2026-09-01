package weather.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
import wangdaye.com.geometricweather.weather.apis.ApihzApi;
import wangdaye.com.geometricweather.weather.services.ApihzWeatherService;

/**
 * apihz.cn answers a place lookup or nothing — there is no coordinate endpoint — and its district
 * coverage is partial (海淀 resolves, 天河 and 渝中 do not). The service therefore walks a ladder:
 * province+district, district alone, province+city, city alone, and only then the by-IP endpoint.
 *
 * That ladder is the whole reliability story for this source and no converter test can see it, so
 * these tests drive it by rung. The rejection body below is what the API actually answers with for
 * an unknown place: HTTP 200 carrying a non-200 {@code code}, which is why the service inspects the
 * body rather than the status.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class ApihzWeatherServiceTest {

    private static final String BY_PLACE = "/api/tianqi/tqyb.php";
    private static final String BY_IP = "/api/tianqi/tqybip.php";

    /** What apihz.cn returns for a place it does not know: a 200 whose body says otherwise. */
    private static final String REJECTED = "{\"code\":400,\"msg\":\"地区不存在\"}";

    private ProviderServer mServer;
    private Context mContext;
    private Location mLocation;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        mServer = new ProviderServer("apihz");

        // 南开区 / 天津市: the district is tried first, then the city — four place lookups in all,
        // since each is attempted with and without the province.
        mLocation = new Location(
                "天津市天津市",
                39.113019f, 117.150738f,
                TimeZone.getTimeZone("Asia/Shanghai"),
                "中国", "天津市", "天津市", "南开区",
                null,
                WeatherSource.APIHZ,
                false, false, true
        );
    }

    @After
    public void tearDown() throws IOException {
        mServer.shutdown();
    }

    private ProviderServer.WeatherOutcome request() {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        new ApihzWeatherService(mServer.api(ApihzApi.class))
                .requestWeather(mContext, mLocation, outcome);
        return outcome;
    }

    /** The common case: the district resolves on the first try, so the ladder is never climbed. */
    @Test
    public void aResolvableDistrictCostsOneCall() throws InterruptedException {
        mServer.serving(BY_PLACE, "tqyb_full.json");

        Weather weather = request().awaitWeather();

        assertNotNull(weather);
        assertEquals(mLocation.getCityId(), weather.getBase().getCityId());
        assertEquals("the first lookup answered; nothing else should have been tried",
                1, mServer.requestCount());
    }

    /**
     * An unknown district must fall back to the *city* before the by-IP endpoint. Skipping straight
     * to IP is what produces weather for another province entirely, since a mobile carrier's egress
     * can be far from the user.
     */
    @Test
    public void anUnknownDistrictFallsBackToTheCityNotToIp() throws InterruptedException {
        // Rungs 1 and 2 (province+district, district alone) are rejected; rung 3 answers.
        mServer.route(BY_PLACE,
                mServer.body(REJECTED),
                mServer.body(REJECTED),
                mServer.payload("tqyb_full.json"));
        mServer.serving(BY_IP, "tqyb_full.json");

        Weather weather = request().awaitWeather();

        assertNotNull(weather);
        assertEquals("the ladder must stop at the city rung",
                3, mServer.requestCount(BY_PLACE));
        assertNull("a resolvable city must never reach the by-IP fallback",
                mServer.requestedPath(BY_IP));
    }

    /** With no place recognised at all, the by-IP endpoint is the last rung rather than a failure. */
    @Test
    public void everyPlaceLookupFailingFallsBackToIp() throws InterruptedException {
        mServer.replying(BY_PLACE, REJECTED);
        mServer.serving(BY_IP, "tqyb_full.json");

        Weather weather = request().awaitWeather();

        assertNotNull("the by-IP endpoint is what keeps an unrecognised place usable", weather);
        assertEquals("district and city, each with and without the province",
                4, mServer.requestCount(BY_PLACE));
        assertNotNull(mServer.requestedPath(BY_IP));
    }

    /**
     * A place outside China gets no request at all. Neither lookup would answer "nothing" for one:
     * 东京 is also the name of a Chinese village, and the by-IP endpoint answers where the request
     * came from (Beijing, for a foreign IP). A wrong-place answer is worse than none — the composite
     * source hands the whole daily block, sunrise and sunset included, to whoever answered, which is
     * how a Tokyo forecast came to show Beijing's sun times.
     */
    @Test
    public void aPlaceOutsideChinaIsRefusedWithoutAsking() throws InterruptedException {
        mServer.serving(BY_PLACE, "tqyb_full.json");
        mServer.serving(BY_IP, "tqyb_full.json");
        mLocation = new Location(
                "tokyo",
                35.6895f, 139.6917f,
                TimeZone.getTimeZone("Asia/Tokyo"),
                "日本", "東京都", "東京", "",
                null,
                WeatherSource.APIHZ,
                false, false, false
        );

        request().awaitFailure();

        assertEquals("a China-only source must not answer for a place abroad",
                0, mServer.requestCount());
    }

    /** When even the IP fallback is rejected there is nothing to show — report failure, not empty. */
    @Test
    public void everyRungFailingReportsFailure() throws InterruptedException {
        mServer.replying(BY_PLACE, REJECTED);
        mServer.replying(BY_IP, REJECTED);

        request().awaitFailure();
    }
}
