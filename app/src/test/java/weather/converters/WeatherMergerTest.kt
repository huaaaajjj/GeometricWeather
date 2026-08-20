package weather.converters

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import wangdaye.com.geometricweather.common.basic.models.weather.Daily
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
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

    /**
     * There is no single leader any more: in production the hourly series comes from Open-Meteo, the
     * daily overview from 中国天气网 and the "now" reading from 彩云. Which real provider fills which
     * slot is the service's business — what has to hold here is that each block follows *its own*
     * assignment and borrows nothing from another block's leader.
     */
    @Test
    fun eachBlockFollowsItsOwnLeader() {
        assertNotEqualsSomewhere(openMeteo, weatherApi)

        val merged = WeatherMerger.merge(
            results = listOf(openMeteo, weatherApi),
            timeZone = TimeZone.getDefault(),
            daily = listOf(weatherApi, openMeteo),
            hourly = listOf(openMeteo, weatherApi),
            current = listOf(weatherApi, openMeteo),
            airQuality = listOf(weatherApi, openMeteo)
        )!!

        // The daily overview is its leader's, entry by whole entry.
        weatherApi.dailyForecast.forEachIndexed { i, expected ->
            val actual = merged.dailyForecast[i]
            assertEquals(expected.date, actual.date)
            assertEquals(expected.day().weatherText, actual.day().weatherText)
            assertEquals(
                expected.day().temperature.temperature,
                actual.day().temperature.temperature
            )
        }

        // The hourly series answers to a different provider. Compare at an hour both of them cover,
        // since the union would be 72 entries long either way.
        val shared = weatherApi.hourlyForecast
            .map { it.time / HOUR_MS }
            .first { hourAt(openMeteo, it) != null }
        assertNotEquals(
            "the fixtures agree on that hour — this would prove nothing",
            hourAt(openMeteo, shared)!!.temperature.temperature,
            hourAt(weatherApi, shared)!!.temperature.temperature
        )
        assertEquals(
            hourAt(openMeteo, shared)!!.temperature.temperature,
            hourAt(merged, shared)!!.temperature.temperature
        )

        // And "now" answers to a third assignment. (Not the temperature: both captures round to
        // 29 there, so it would prove nothing — the condition text and the feels-like do differ.)
        assertNotEquals(openMeteo.current.weatherText, weatherApi.current.weatherText)
        assertEquals(weatherApi.current.weatherText, merged.current.weatherText)
        assertEquals(
            weatherApi.current.temperature.realFeelTemperature,
            merged.current.temperature.realFeelTemperature
        )
    }

    /**
     * Air quality is assigned too, so it must come from that provider even when the block leader has
     * a perfectly good reading of its own. That is the break from the old single-leader merge, where
     * whoever led always won the field.
     */
    @Test
    fun airQualityFollowsItsAssignmentEvenWhenTheLeaderHasOneOfItsOwn() {
        val spiked = withPm25(199.0)
        assertTrue(weatherApi.current.airQuality.isValid)
        assertNotEquals(
            weatherApi.current.airQuality.getPM25(),
            spiked.current.airQuality.getPM25()
        )

        val merged = WeatherMerger.merge(
            results = listOf(openMeteo, weatherApi, spiked),
            timeZone = TimeZone.getDefault(),
            daily = listOf(weatherApi, openMeteo, spiked),
            hourly = listOf(openMeteo, weatherApi, spiked),
            current = listOf(openMeteo, weatherApi, spiked),
            airQuality = listOf(spiked, weatherApi, openMeteo)
        )!!

        assertEquals(spiked.current.airQuality.getPM25(), merged.current.airQuality.getPM25())
        assertEquals(
            spiked.dailyForecast[0].airQuality.getPM25(),
            merged.dailyForecast[0].airQuality.getPM25()
        )
        // The blocks that were not assigned to it keep their own leaders.
        assertEquals(openMeteo.current.weatherText, merged.current.weatherText)
        assertEquals(
            weatherApi.dailyForecast[0].day().weatherText,
            merged.dailyForecast[0].day().weatherText
        )
    }

    // ---- harness ----

    /**
     * 中国天气网 leads the daily overview and carries no chance of rain at all, so its seven days
     * showed none while the appended days 8..16 (whole Open-Meteo entries) did. The probability is
     * grafted from whoever has it — and only the probability: the day it lands in stays the leader's.
     */
    @Test
    fun theChanceOfRainIsGraftedIntoDaysThatLackIt() {
        val leaderWithoutChance = stripDailyChanceOfRain(weatherApi)
        // Guard the guard: the donor must actually carry a probability for those same days.
        assertTrue(
            "fixture drifted: Open-Meteo is supposed to carry a daily chance of rain",
            openMeteo.dailyForecast.any { it.day().precipitationProbability.isValid }
        )

        val merged = merge(leaderWithoutChance, openMeteo)!!

        var grafted = 0
        merged.dailyForecast.forEachIndexed { i, day ->
            val donor = openMeteo.dailyForecast.firstOrNull { it.date == day.date } ?: return@forEachIndexed
            if (!donor.day().precipitationProbability.isValid) return@forEachIndexed
            assertEquals(
                "day $i kept an empty chance of rain",
                donor.day().precipitationProbability.total,
                day.day().precipitationProbability.total
            )
            // …while everything that makes the day *that day* is still the leader's.
            val leaderDay = leaderWithoutChance.dailyForecast.first { it.date == day.date }
            assertEquals(leaderDay.day().weatherText, day.day().weatherText)
            assertEquals(leaderDay.day().temperature.temperature, day.day().temperature.temperature)
            assertEquals(
                leaderDay.day().precipitation.total,
                day.day().precipitation.total
            )
            grafted++
        }
        assertTrue("nothing was grafted — the test proves nothing", grafted > 0)
    }

    /** A leader that has its own probability keeps it; the donor's is not allowed to win. */
    @Test
    fun aChanceOfRainTheLeaderAlreadyHasIsLeftAlone() {
        val ownChance = openMeteo.dailyForecast.first { it.day().precipitationProbability.isValid }
        val merged = merge(openMeteo, stripDailyChanceOfRain(weatherApi))!!
        val mergedDay = merged.dailyForecast.first { it.date == ownChance.date }
        assertEquals(
            ownChance.day().precipitationProbability.total,
            mergedDay.day().precipitationProbability.total
        )
    }

    /** A stand-in for 中国天气网, which fills every daily probability with nulls. */
    private fun stripDailyChanceOfRain(weather: Weather) = Weather(
        weather.base,
        weather.current,
        weather.yesterday,
        weather.dailyForecast.map { day ->
            Daily(
                day.date, day.time,
                withoutChanceOfRain(day.day()), withoutChanceOfRain(day.night()),
                day.sun(), day.moon(), day.moonPhase, day.airQuality, day.pollen, day.uv,
                day.hoursOfSun
            )
        },
        weather.hourlyForecast,
        weather.minutelyForecast,
        weather.alertList
    )

    private fun withoutChanceOfRain(half: HalfDay) = HalfDay(
        half.weatherText, half.weatherPhase, half.weatherCode,
        half.temperature, half.precipitation,
        PrecipitationProbability(null, null, null, null, null),
        half.precipitationDuration, half.wind, half.cloudCover
    )

    private fun hourAt(weather: Weather, bucket: Long) =
        weather.hourlyForecast.firstOrNull { it.time / HOUR_MS == bucket }

    /** The same WeatherAPI capture with a different PM2.5 — a stand-in for a second AQI provider. */
    private fun withPm25(pm25: Double): Weather {
        val result = fixture("weatherapi/forecast.json", WeatherApiResult::class.java)
        result.current.airQuality.pm25 = pm25
        result.forecast.forecastday.forEach { it.day.airQuality?.pm25 = pm25 }
        return WeatherApiResultConverter.convert(context, location, result)!!
    }

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

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
    }
}
