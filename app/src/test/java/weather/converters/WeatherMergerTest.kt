package weather.converters

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter
import wangdaye.com.geometricweather.weather.converters.WeatherApiResultConverter
import wangdaye.com.geometricweather.weather.converters.WeatherMerger
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.TimeZone

/**
 * The composite source's merge, driven by the two real captures it actually combines in production.
 *
 * Both fixtures cover 2026-08-11..13 for the same place, and they are incomplete in exactly the
 * complementary ways the composite exists to paper over: Open-Meteo brings 72 hours and UV but no
 * air quality and no warnings; WeatherAPI brings air quality and a warning over 18 hours.
 *
 * The load-bearing assertion is the *negative* one — a merged day must never carry one provider's
 * temperature next to another's condition text. Everything else here is a gap being filled.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class WeatherMergerTest {

    private lateinit var context: Context
    private lateinit var location: Location

    private lateinit var openMeteo: Weather
    private lateinit var weatherApi: Weather

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        location = Location(
            "54517_tj",
            39.113019f, 117.150738f,
            TimeZone.getTimeZone("Asia/Shanghai"),
            "中国", "天津市", "天津市", "南开区",
            null,
            WeatherSource.COMPOSITE,
            false, false, true
        )

        openMeteo = OpenMeteoResultConverter.convert(
            context, location, fixture("openmeteo/forecast.json", OpenMeteoResult::class.java)
        )!!

        val weatherApiResult = fixture("weatherapi/forecast.json", WeatherApiResult::class.java)
        // That capture's warning is the mis-attributed 霸州市 one from the 3.4.13 bug, and the
        // converter's admin-area filter correctly drops it for any other place. Re-attribute it the
        // way WeatherApiResultConverterTest does, so this file can test the union rather than the
        // filter — which has its own test already.
        weatherApiResult.alerts.alert[0].areas = "天津市"
        weatherApiResult.alerts.alert[0].headline = "天津市气象台更新暴雨红色预警[I级/特别严重]"
        weatherApi = WeatherApiResultConverter.convert(context, location, weatherApiResult)!!
    }

    /**
     * The whole reason the merge is block-granular: a day's reading must stay internally consistent.
     * Every merged day has to be the leader's day verbatim, not a blend, even though the other
     * provider offers its own numbers for the very same dates.
     */
    @Test
    fun theLeadersOwnReadingsAreNeverBlendedWithAnothers() {
        val merged = merge(openMeteo, weatherApi)!!

        // Guard the guard: if the two providers happened to agree, this test would pass vacuously.
        assertNotEqualsSomewhere(openMeteo, weatherApi)

        openMeteo.dailyForecast.forEachIndexed { i, expected ->
            val actual = merged.dailyForecast[i]
            assertEquals(expected.date, actual.date)
            assertEquals(expected.day().weatherText, actual.day().weatherText)
            assertEquals(expected.day().weatherCode, actual.day().weatherCode)
            assertEquals(
                expected.day().temperature.temperature,
                actual.day().temperature.temperature
            )
            assertEquals(
                expected.night().temperature.temperature,
                actual.night().temperature.temperature
            )
        }
        assertEquals(openMeteo.current.weatherText, merged.current.weatherText)
        assertEquals(
            openMeteo.current.temperature.temperature,
            merged.current.temperature.temperature
        )
    }

    /** Open-Meteo carries no air quality at all; WeatherAPI does. That is the point of the mix. */
    @Test
    fun airQualityComesFromTheProviderThatHasIt() {
        assertFalse(
            "fixture drifted: Open-Meteo is supposed to have no air quality",
            openMeteo.current.airQuality.isValid
        )
        assertTrue(weatherApi.current.airQuality.isValid)

        val merged = merge(openMeteo, weatherApi)!!

        assertTrue(merged.current.airQuality.isValid)
        assertEquals(weatherApi.current.airQuality.getPM25(), merged.current.airQuality.getPM25())
    }

    /** Warnings are a union: Open-Meteo has none, so WeatherAPI's must survive. */
    @Test
    fun warningsFromEveryProviderSurvive() {
        assertEquals(0, openMeteo.alertList.size)
        assertEquals(1, weatherApi.alertList.size)

        assertEquals(1, merge(openMeteo, weatherApi)!!.alertList.size)
    }

    /** The leader still wins where it has data of its own — UV here. */
    @Test
    fun theLeaderKeepsItsOwnBlocksWhenTheyCarryData() {
        assertTrue(openMeteo.dailyForecast[0].uv.isValidIndex)

        val merged = merge(openMeteo, weatherApi)!!

        assertEquals(
            openMeteo.dailyForecast[0].uv.index,
            merged.dailyForecast[0].uv.index
        )
    }

    /** Days past the leader's range are appended whole, so the series is as long as the longest. */
    @Test
    fun theSeriesRunsAsLongAsTheLongestProvider() {
        val shortened = truncateDaily(openMeteo, 1)
        assertEquals(1, shortened.dailyForecast.size)
        assertEquals(3, weatherApi.dailyForecast.size)

        val merged = merge(shortened, weatherApi)!!

        assertEquals(3, merged.dailyForecast.size)
        // Day 1 is still the leader's; days 2-3 are borrowed whole.
        assertEquals(
            openMeteo.dailyForecast[0].day().temperature.temperature,
            merged.dailyForecast[0].day().temperature.temperature
        )
        assertEquals(
            weatherApi.dailyForecast[1].day().temperature.temperature,
            merged.dailyForecast[1].day().temperature.temperature
        )
        // And they stay in date order after the splice.
        assertTrue(merged.dailyForecast.zipWithNext().all { (a, b) -> a.time < b.time })
    }

    /** WeatherAPI's 18 hours fall inside Open-Meteo's 72, so the union must not double them up. */
    @Test
    fun overlappingHoursAreOneEntryEach() {
        assertEquals(72, openMeteo.hourlyForecast.size)
        assertEquals(18, weatherApi.hourlyForecast.size)

        val merged = merge(openMeteo, weatherApi)!!

        assertEquals(72, merged.hourlyForecast.size)
        assertEquals(
            openMeteo.hourlyForecast[0].temperature.temperature,
            merged.hourlyForecast[0].temperature.temperature
        )
        assertTrue(merged.hourlyForecast.zipWithNext().all { (a, b) -> a.time < b.time })
    }

    /** Degenerate inputs: one provider answered, or none did. */
    @Test
    fun aSingleAnswerIsPassedStraightThroughAndNoneIsNull() {
        assertEquals(openMeteo, merge(openMeteo))
        assertNull(merge())
    }

    // ---- harness ----

    private fun merge(vararg results: Weather) =
        WeatherMerger.merge(results.toList(), TimeZone.getDefault())

    private fun <T> fixture(path: String, type: Class<T>): T {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
        assertNotNull("fixture missing: $path", stream)
        return Gson().fromJson(InputStreamReader(stream, StandardCharsets.UTF_8), type)
    }

    private fun truncateDaily(weather: Weather, days: Int) = Weather(
        weather.base,
        weather.current,
        weather.yesterday,
        weather.dailyForecast.take(days),
        weather.hourlyForecast,
        weather.minutelyForecast,
        weather.alertList
    )

    /** Fails if the two providers agree everywhere, which would make the no-blending test empty. */
    private fun assertNotEqualsSomewhere(a: Weather, b: Weather) {
        val differs = a.dailyForecast.indices.any { i ->
            i < b.dailyForecast.size && (
                a.dailyForecast[i].day().temperature.temperature !=
                    b.dailyForecast[i].day().temperature.temperature
                    || a.dailyForecast[i].day().weatherText != b.dailyForecast[i].day().weatherText
                )
        }
        assertTrue("the two fixtures agree on every day — this test proves nothing", differs)
    }
}
