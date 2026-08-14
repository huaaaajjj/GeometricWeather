package weather.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import wangdaye.com.geometricweather.common.basic.models.Location;
import wangdaye.com.geometricweather.common.basic.models.weather.Weather;
import wangdaye.com.geometricweather.weather.services.WeatherService;

/**
 * Drives a {@code WeatherService} against canned provider responses over a real Retrofit/OkHttp
 * stack, so the layer these tests exercise is the orchestration — how many calls go out, which of
 * them are allowed to fail, what gets chained after what, and which outcome the service reports.
 *
 * A route matches on path prefix, in insertion order, and holds a *sequence* of replies: the first
 * call to that path gets the first entry, the second the second, and the last entry repeats
 * thereafter. That is what lets a fallback chain be expressed directly — "this endpoint rejects the
 * stored id, then answers for the resolved one" — while a plain single-reply route still reads as
 * one line. Every request is recorded, so a test can assert that a call carried a particular query
 * parameter, or was never made at all.
 */
final class ProviderServer {

    /** A canned reply. Rebuilt per dispatch so concurrent fan-out calls never share one instance. */
    static final class Stub {
        final int code;
        final String body;

        private Stub(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private final MockWebServer mServer = new MockWebServer();
    private final String mFixtureDir;
    private final Map<String, List<Stub>> mRoutes = new LinkedHashMap<>();
    private final Map<String, AtomicInteger> mRouteHits = new LinkedHashMap<>();
    private final List<String> mRequestedPaths = new CopyOnWriteArrayList<>();
    private volatile long mBodyDelayMillis;

    ProviderServer(String fixtureDir) throws IOException {
        mFixtureDir = fixtureDir;
        mServer.setDispatcher(new Dispatcher() {
            @NonNull
            @Override
            public MockResponse dispatch(@NonNull RecordedRequest request) {
                String path = request.getPath();
                mRequestedPaths.add(path == null ? "" : path);
                if (path != null) {
                    for (Map.Entry<String, List<Stub>> route : mRoutes.entrySet()) {
                        if (path.startsWith(route.getKey())) {
                            Stub stub = next(route.getKey(), route.getValue());
                            return delayed(new MockResponse()
                                    .setResponseCode(stub.code)
                                    .setBody(stub.body));
                        }
                    }
                }
                // An unrouted path is a test bug, not a provider outage; 404 makes it visible as a
                // failed expectation rather than a silently skipped call.
                return delayed(new MockResponse().setResponseCode(404));
            }
        });
        mServer.start();
    }

    private MockResponse delayed(MockResponse response) {
        long delay = mBodyDelayMillis;
        return delay > 0 ? response.setBodyDelay(delay, TimeUnit.MILLISECONDS) : response;
    }

    /** Holds every reply back, so a request can be observed while it is still in flight. */
    ProviderServer slow(long millis) {
        mBodyDelayMillis = millis;
        return this;
    }

    /** Walks a route's reply sequence, holding on the last entry once it is exhausted. */
    private Stub next(String prefix, List<Stub> sequence) {
        int hit = mRouteHits.get(prefix).getAndIncrement();
        return sequence.get(Math.min(hit, sequence.size() - 1));
    }

    // ---- reply building ----

    /** A 200 carrying the captured payload {@code <fixtureDir>/<name>}. */
    Stub payload(String fixtureName) {
        return new Stub(200, fixture(fixtureName));
    }

    /** A 200 carrying an arbitrary body — for shapes no captured fixture expresses. */
    Stub body(String raw) {
        return new Stub(200, raw);
    }

    Stub status(int code) {
        return new Stub(code, "");
    }

    // ---- routing ----

    ProviderServer route(String pathPrefix, Stub... sequence) {
        mRoutes.put(pathPrefix, Arrays.asList(sequence));
        mRouteHits.put(pathPrefix, new AtomicInteger());
        return this;
    }

    /** Serves captured payloads, one per successive call, the last repeating. */
    ProviderServer serving(String pathPrefix, String... fixtureNames) {
        Stub[] sequence = new Stub[fixtureNames.length];
        for (int i = 0; i < fixtureNames.length; i++) {
            sequence[i] = payload(fixtureNames[i]);
        }
        return route(pathPrefix, sequence);
    }

    /** Serves an arbitrary body for every call to the prefix. */
    ProviderServer replying(String pathPrefix, String raw) {
        return route(pathPrefix, body(raw));
    }

    /** Takes one endpoint down while the rest stay healthy. */
    ProviderServer failing(String pathPrefix, int code) {
        return route(pathPrefix, status(code));
    }

    // ---- api construction ----

    /** Mirrors the shared converter factory {@code RetrofitModule} installs for every non-MF source. */
    <T> T api(Class<T> apiClass) {
        return api(apiClass, new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create());
    }

    <T> T api(Class<T> apiClass, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(mServer.url("/"))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(apiClass);
    }

    /**
     * Mirrors the UTC date handling {@code ApiModule} installs for the Météo France instance —
     * MF stamps everything "2026-08-10T09:00:00.000Z" and the shared format drops the zone.
     */
    static Gson utcGson() {
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

    // ---- assertions about traffic ----

    int requestCount() {
        return mRequestedPaths.size();
    }

    /** The first recorded path under the prefix, or null when the call was never made. */
    String requestedPath(String pathPrefix) {
        List<String> matches = requestedPaths(pathPrefix);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Every recorded path under the prefix, in the order the requests arrived. */
    List<String> requestedPaths(String pathPrefix) {
        List<String> matches = new ArrayList<>();
        for (String path : mRequestedPaths) {
            if (path.startsWith(pathPrefix)) {
                matches.add(path);
            }
        }
        return matches;
    }

    int requestCount(String pathPrefix) {
        return requestedPaths(pathPrefix).size();
    }

    void shutdown() throws IOException {
        mServer.shutdown();
    }

    // ---- fixtures ----

    private String fixture(String name) {
        String path = mFixtureDir + "/" + name;
        try (InputStream in = ProviderServer.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull("fixture missing: " + path, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new AssertionError("could not read fixture " + path, e);
        }
    }

    // ---- callbacks ----

    /**
     * Blocks the test thread until the service reports an outcome. The services invoke their
     * callbacks from the IO threads they fan out onto, so there is nothing to assert synchronously.
     */
    static final class WeatherOutcome implements WeatherService.RequestWeatherCallback {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private volatile Location mSucceeded;
        private volatile boolean mFailed;

        @Override
        public void requestWeatherSuccess(@NonNull Location requestLocation) {
            mSucceeded = requestLocation;
            mLatch.countDown();
        }

        @Override
        public void requestWeatherFailed(@NonNull Location requestLocation) {
            mFailed = true;
            mLatch.countDown();
        }

        /** Asserts the service succeeded and hands back the weather it produced. */
        Weather awaitWeather() throws InterruptedException {
            await();
            assertFalse("expected success, but the service reported failure", mFailed);
            assertNotNull("the service reported success without a location", mSucceeded);
            return mSucceeded.getWeather();
        }

        void awaitFailure() throws InterruptedException {
            await();
            assertNull("expected failure, but the service reported success", mSucceeded);
            assertTrue(mFailed);
        }

        /** True when no outcome arrived within the window — used to assert a cancelled request. */
        boolean awaitSilence(long millis) throws InterruptedException {
            return !mLatch.await(millis, TimeUnit.MILLISECONDS);
        }

        private void await() throws InterruptedException {
            // Comfortably above the round trip to a local MockWebServer, but below the 30s barrier
            // the fan-out services wait on, so a stuck join fails the test rather than hanging it.
            assertTrue("the service never reported an outcome",
                    mLatch.await(20, TimeUnit.SECONDS));
        }
    }

    /** The location-resolution counterpart of {@link WeatherOutcome}. */
    static final class LocationOutcome implements WeatherService.RequestLocationCallback {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private volatile List<Location> mSucceeded;
        private volatile boolean mFailed;

        @Override
        public void requestLocationSuccess(String query, List<Location> locationList) {
            mSucceeded = locationList;
            mLatch.countDown();
        }

        @Override
        public void requestLocationFailed(String query) {
            mFailed = true;
            mLatch.countDown();
        }

        List<Location> awaitLocations() throws InterruptedException {
            assertTrue("the service never reported an outcome",
                    mLatch.await(20, TimeUnit.SECONDS));
            assertFalse("expected success, but the service reported failure", mFailed);
            assertNotNull(mSucceeded);
            return mSucceeded;
        }

        void awaitFailure() throws InterruptedException {
            assertTrue("the service never reported an outcome",
                    mLatch.await(20, TimeUnit.SECONDS));
            assertNull("expected failure, but the service reported success", mSucceeded);
            assertTrue(mFailed);
        }
    }
}
