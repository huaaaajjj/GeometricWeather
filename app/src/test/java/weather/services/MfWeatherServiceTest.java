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
import wangdaye.com.geometricweather.weather.apis.AtmoAuraIqaApi;
import wangdaye.com.geometricweather.weather.apis.MfWeatherApi;
import wangdaye.com.geometricweather.weather.services.MfWeatherService;

/**
 * Météo France is the widest fan-out in the app — five joined calls plus a conditional sixth to a
 * different host — and two of its arrangements are load-bearing but invisible to a converter test:
 *
 * 1. Warnings are *chained after* the forecast, because they are keyed by the French department
 *    number and the forecast response is the only place that number comes from. Fanning them out
 *    in parallel (which is what the code did until 3.5.3) sends the stored province name instead,
 *    and the warning list silently comes back empty forever.
 * 2. Only the observation and the forecast are required. The other calls each cover a strictly
 *    optional block, and MF drops them often enough that promoting any of them would read as the
 *    whole source being down.
 *
 * Payloads are the verbatim webservice.meteofrance.com captures the converter test uses (Paris).
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = 34)
public class MfWeatherServiceTest {

    private static final String OBSERVATION = "/v2/observation";
    private static final String FORECAST = "/v2/forecast";
    private static final String EPHEMERIS = "/v2/ephemeris";
    private static final String RAIN = "/v3/nowcast/rain";
    private static final String WARNINGS = "/v3/warning/full";
    private static final String ATMO_AURA = "/api/v1/iqa/full";

    private ProviderServer mServer;
    private Context mContext;
    private Location mParis;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        mParis = parisIn("75");

        mServer = new ProviderServer("mf")
                .serving(OBSERVATION, "observation.json")
                .serving(FORECAST, "forecast.json")
                .serving(EPHEMERIS, "ephemeris.json")
                .serving(RAIN, "rain.json")
                .serving(WARNINGS, "warning.json");
    }

    @After
    public void tearDown() throws IOException {
        mServer.shutdown();
    }

    /** Paris, filed under an arbitrary department so the Atmo Aura region check can be exercised. */
    private static Location parisIn(String department) {
        return new Location(
                "75056",
                48.8566f, 2.3522f,
                TimeZone.getTimeZone("Europe/Paris"),
                "FR - France", department, "Paris", "",
                null,
                WeatherSource.MF,
                false, false, false
        );
    }

    private ProviderServer.WeatherOutcome request(Location location) {
        ProviderServer.WeatherOutcome outcome = new ProviderServer.WeatherOutcome();
        new MfWeatherService(
                mServer.api(MfWeatherApi.class, ProviderServer.utcGson()),
                mServer.api(AtmoAuraIqaApi.class)
        ).requestWeather(mContext, location, outcome);
        return outcome;
    }

    @Test
    public void everyEndpointHealthyProducesAJoinedWeather() throws InterruptedException {
        Weather weather = request(mParis).awaitWeather();

        assertNotNull("five healthy endpoints must produce a Weather", weather);
        assertEquals(mParis.getCityId(), weather.getBase().getCityId());
        assertEquals(4, weather.getDailyForecast().size());
        assertEquals(26, weather.getHourlyForecast().size());
        // From the observation, which wins over the forecast for "now".
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        // One yellow Canicule among green ones; greens are dropped.
        assertEquals(1, weather.getAlertList().size());
    }

    /**
     * The regression this whole arrangement exists to prevent. {@code location.getProvince()} here
     * is "75" only because this test files Paris that way; the department that must reach the wire
     * is the one the forecast reported. Assert the call happened and carried it.
     */
    @Test
    public void warningsAreKeyedByTheDepartmentTheForecastReported() throws InterruptedException {
        // File the location under a name, the way a located user actually has it, so a fallback to
        // location.getProvince() cannot accidentally produce the right query.
        Weather weather = request(parisIn("Île-de-France")).awaitWeather();

        String warningPath = mServer.requestedPath(WARNINGS);
        assertNotNull("warnings must be requested once the forecast supplies a department",
                warningPath);
        assertTrue("warnings must be keyed by the forecast's french_department (75), got: "
                        + warningPath,
                warningPath.contains("domain=75"));
        assertEquals(1, weather.getAlertList().size());
    }

    /** The observation is required: it is what the "now" block is built from. */
    @Test
    public void observationIsRequired() throws InterruptedException {
        mServer.failing(OBSERVATION, 500);

        request(mParis).awaitFailure();
    }

    /** The forecast is required: it carries the daily and hourly lists. */
    @Test
    public void forecastIsRequired() throws InterruptedException {
        mServer.failing(FORECAST, 500);

        request(mParis).awaitFailure();
    }

    /**
     * Ephemeris, rain and warnings are each optional. All three failing at once must still leave a
     * usable forecast rather than reading as an outage.
     */
    @Test
    public void optionalCallsCanAllFailWithoutLosingTheForecast() throws InterruptedException {
        mServer.failing(EPHEMERIS, 500).failing(RAIN, 500).failing(WARNINGS, 503);

        Weather weather = request(mParis).awaitWeather();

        assertNotNull("optional outages must not take the source down", weather);
        assertEquals(4, weather.getDailyForecast().size());
        assertEquals(29, weather.getCurrent().getTemperature().getTemperature());
        assertTrue("no warnings call succeeded, so there is nothing to show",
                weather.getAlertList().isEmpty());
    }

    /**
     * Atmo Aura publishes for the Auvergne-Rhône-Alpes departments only. Paris must not spend a
     * request on it; a Rhône location must.
     */
    @Test
    public void airQualityIsRequestedOnlyInsideTheAtmoAuraRegion() throws InterruptedException {
        request(mParis).awaitWeather();
        assertNull("Paris is outside Atmo Aura's coverage, so the call must be skipped",
                mServer.requestedPath(ATMO_AURA));
    }

    @Test
    public void airQualityIsRequestedForAnAtmoAuraDepartment() throws InterruptedException {
        // 69 = Rhône, one of the departments the service lists.
        request(parisIn("69")).awaitWeather();

        assertNotNull("a Rhône location must reach for Atmo Aura's air quality",
                mServer.requestedPath(ATMO_AURA));
    }
}
