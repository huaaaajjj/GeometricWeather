package weather.converters

import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.After
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
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.weather.converters.MetNoResultConverter
import wangdaye.com.geometricweather.weather.json.metno.MetNoAirQualityResult
import wangdaye.com.geometricweather.weather.json.metno.MetNoAlertResult
import wangdaye.com.geometricweather.weather.json.metno.MetNoForecastResult
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.TimeZone

/**
 * MET Norway is the second keyless global source, so it is where users land when Open-Meteo
 * rate-limits and every keyed provider is out of quota — a silent conversion failure here has no
 * backstop.
 *
 * Fixtures are real api.met.no responses for Oslo captured 2026-08-23T16:00Z (18:00 local), trimmed:
 * the forecast keeps 18 of the hourly points, 12 of the 6-hourly tail and the final block-less point
 * (31 in all, so the middle days are missing and the daily list has a gap — deliberate, the tests
 * assert per-day values, never contiguity), and air quality keeps 12 hours minus the ~30
 * source-fraction variables the converter ignores.
 *
 * 18:00 local also means the fixture starts *after* the day half is over, which is the case that
 * exercises the empty-half fallback.
 *
 * Robolectric is needed because CommonConverter.getWindLevel/getAqiQuality resolve string resources.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class MetNoResultConverterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val oslo = TimeZone.getTimeZone("Europe/Oslo")
    private lateinit var defaultTimeZone: TimeZone

    private val location = Location(
        "", 59.9139f, 10.7522f, TimeZone.getTimeZone("Europe/Oslo"),
        "Norge", "Oslo", "Oslo", "",
        null, WeatherSource.METNO, false, false, false
    )

    @Before
    fun setUp() {
        // Pinned to a zone that is neither UTC nor Oslo: the daily folding must key off
        // location.timeZone, and a regression back to the JVM default has to fail here.
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private inline fun <reified T> read(name: String): T {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("metno/$name")) {
            "missing fixture metno/$name"
        }
        return Gson().fromJson(InputStreamReader(stream, StandardCharsets.UTF_8), T::class.java)
    }

    private fun convertAll() = MetNoResultConverter.convert(
        context,
        location,
        read<MetNoForecastResult>("forecast_oslo.json"),
        read<MetNoAirQualityResult>("airquality_oslo.json"),
        read<MetNoAlertResult>("alerts_oslo.json"),
        read<MetNoForecastResult>("nowcast_oslo.json")
    )

    private fun osloMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(oslo).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis

    @Test
    fun convert_foldsTheFlatTimeseriesIntoLocalDays() {
        val daily = requireNotNull(convertAll()).dailyForecast

        // Six distinct local dates survive the fixture trim.
        assertEquals(6, daily.size)
        assertEquals(osloMidnight(2026, 8, 23), daily[0].date.time)
        assertEquals(osloMidnight(2026, 8, 24), daily[1].date.time)
        // Strictly increasing, and never the day before the first point's own local date — that
        // ordering is what keeps index 0 meaning "today" for the 76 sites that assume it.
        for (i in 1 until daily.size) {
            assertTrue(daily[i].date.time > daily[i - 1].date.time)
        }
    }

    @Test
    fun convert_fillsAnEmptyHalfFromTheOtherOne() {
        // The fixture starts at 18:00 local, so day one has no 06:00-18:00 points at all. Both
        // halves must still be present: every consumer dereferences day()/night() unguarded.
        val first = requireNotNull(convertAll()).dailyForecast[0]

        assertNotNull(first.day())
        assertNotNull(first.night())
        assertEquals(23, first.day().temperature.temperature)
        assertEquals(13, first.night().temperature.temperature)
        assertEquals("Day", first.day().weatherPhase)
        assertEquals("Night", first.night().weatherPhase)
    }

    @Test
    fun convert_takesTheWorstWeatherOfTheHalfNotItsMidpoint() {
        val daily = requireNotNull(convertAll()).dailyForecast

        // 2026-08-24 runs fair -> partlycloudy through the day; PARTLY_CLOUDY outranks CLEAR.
        assertEquals(WeatherCode.PARTLY_CLOUDY, daily[1].day().weatherCode)
        // The 6-hourly tail days include a plain "cloudy" block, which outranks partlycloudy.
        assertEquals(WeatherCode.CLOUDY, daily[2].day().weatherCode)
    }

    @Test
    fun convert_computesSunriseForEveryFoldedDay() {
        // The API carries no astro block anywhere; the sun comes from SolarCalculator instead of
        // leaving isDaylight() on the hardcoded 06:00-18:00 clock. Reference times for Oslo on
        // 2026-08-23 (rise 05:49:00 / set 20:50:16 CEST) are from the sunrise-sunset.org almanac,
        // same 90.833° zenith convention — a 3-minute tolerance keeps the test about real errors.
        val daily = requireNotNull(convertAll()).dailyForecast

        daily.forEach { assertTrue(it.sun().isValid) }
        assertNear(daily[0].sun().riseDate, utcMillis(2026, 8, 23, 3, 49, 0), "rise")
        assertNear(daily[0].sun().setDate, utcMillis(2026, 8, 23, 18, 50, 16), "set")
    }

    private fun assertNear(actual: java.util.Date?, expectedMillis: Long, label: String) {
        assertNotNull(label, actual)
        val delta = Math.abs(requireNotNull(actual).time - expectedMillis)
        assertEquals(label, 0.0, delta / 1000.0, 180.0)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun convert_readsCurrentFromTheFirstTimeseriesPoint() {
        val current = requireNotNull(convertAll()).current

        assertEquals(23, current.temperature.temperature)
        // No apparent temperature is reported anywhere in the API.
        assertNull(current.temperature.realFeelTemperature)
        assertEquals(1015.6f, requireNotNull(current.pressure), 0.01f)
        assertEquals(40.3f, requireNotNull(current.relativeHumidity), 0.01f)
        assertEquals(8, current.dewPoint)
        assertEquals(27, current.cloudCover)
        assertEquals(1, current.uv.index)
        assertEquals("晴", current.weatherText)
        assertEquals(WeatherCode.PARTLY_CLOUDY, current.weatherCode)
        assertEquals(0f, requireNotNull(current.precipitation.total), 0.01f)
    }

    @Test
    fun convert_scalesWindFromMetresPerSecondToKmh() {
        val wind = requireNotNull(convertAll()).current.wind

        // 3.4 m/s in the fixture; the app stores km/h.
        assertEquals(3.4f * 3.6f, requireNotNull(wind.speed), 0.01f)
        assertEquals("NE", wind.direction)
        assertEquals(55f, wind.degree.degree, 0.01f)
        assertFalse(wind.degree.isNoDirection)
    }

    @Test
    fun convert_derivesAqiFromConcentrationsNotTheNorwegianIndex() {
        val air = requireNotNull(convertAll()).current.airQuality

        // The nearest hour reports pm25 3.11 / pm10 5.60 µg/m³ -> 6 on the 0-500 Chinese scale.
        // MET Norway's own AQI field for that hour is 1.75, so a 2 here would mean the 1..4+ index
        // leaked through and every location would render as pristine.
        assertEquals(6, air.aqiIndex)
        assertNotNull(air.aqiText)
        assertEquals(3.11f, requireNotNull(air.getPM25()), 0.01f)
        assertEquals(5.60f, requireNotNull(air.getPM10()), 0.01f)
        assertEquals(6.00f, requireNotNull(air.getNO2()), 0.01f)
        assertEquals(75.17f, requireNotNull(air.getO3()), 0.01f)
        // Neither is reported by this endpoint.
        assertNull(air.getSO2())
        assertNull(air.getCO())    }

    @Test
    fun convert_readsDaylightFromTheSymbolSuffixNotTheClock() {
        val hourly = requireNotNull(convertAll()).hourlyForecast

        // Every point carries instant details, including the last one, which has no forecast block.
        assertEquals(31, hourly.size)
        // 18:00 local in late-August Oslo is still daylight — the clock heuristic this replaces
        // would call it night, and there is no sunrise/sunset to fall back on.
        assertTrue(hourly[0].isDaylight)
        assertTrue(hourly.any { !it.isDaylight })
        // The suffix-less bases (cloudy, rain) and the block-less final point fall back to the clock
        // rather than throwing.
        assertTrue(hourly.any { it.weatherCode == WeatherCode.CLOUDY })
        assertTrue(hourly.any { it.weatherCode == WeatherCode.RAIN })
        assertEquals("", hourly.last().weatherText)
    }

    @Test
    fun convert_mapsNowcastToMinutely() {
        val minutely = requireNotNull(convertAll()).minutelyForecast

        assertEquals(23, minutely.size)
        // minuteInterval is minutes since the first step, matching what Accu and MF store.
        assertEquals(0, minutely[0].minuteInterval)
        assertEquals(5, minutely[1].minuteInterval)
        assertEquals(WeatherCode.PARTLY_CLOUDY, minutely[0].weatherCode)
    }

    /**
     * The text builder used to compose English ("Light sleet showers and thunder") from the same
     * symbol parts the icon chain matches. Both must stay in step and read the shared Chinese
     * vocabulary — the text by the very substrings [weatherCodeOf] matches, so neither can drift
     * from the other. MET's own published list carries one typo'd symbol, which the substring
     * matching must absorb.
     */
    @Test
    fun convert_buildsChineseTextFromTheSameSubstringsAsTheIcon() {
        val forecast = read<MetNoForecastResult>("forecast_oslo.json")
        val air = read<MetNoAirQualityResult>("airquality_oslo.json")
        val alerts = read<MetNoAlertResult>("alerts_oslo.json")
        val nowcast = read<MetNoForecastResult>("nowcast_oslo.json")
        val summary = requireNotNull(
            requireNotNull(forecast.properties.timeseries.first().data.next1Hours).summary
        )

        val cases = mapOf(
            "clearsky_day" to ("晴" to WeatherCode.CLEAR),
            "fair_day" to ("晴" to WeatherCode.PARTLY_CLOUDY),
            "partlycloudy_day" to ("多云" to WeatherCode.PARTLY_CLOUDY),
            "cloudy" to ("阴" to WeatherCode.CLOUDY),
            "fog" to ("雾" to WeatherCode.FOG),
            "rain" to ("雨" to WeatherCode.RAIN),
            "lightrain" to ("小雨" to WeatherCode.RAIN),
            "heavyrain" to ("大雨" to WeatherCode.RAIN),
            "rainshowers_day" to ("阵雨" to WeatherCode.RAIN),
            "heavyrainshowersandthunder" to ("雷阵雨" to WeatherCode.THUNDERSTORM),
            "sleet" to ("雨夹雪" to WeatherCode.SLEET),
            "lightsnow" to ("小雪" to WeatherCode.SNOW),
            "heavysnow" to ("大雪" to WeatherCode.SNOW),
            "snowshowers_night" to ("阵雪" to WeatherCode.SNOW),
            // MET Norway's published typo, absorbed by the substring matching (see weatherCodeOf).
            "lightssleetshowersandthunder" to ("雷阵雨" to WeatherCode.THUNDERSTORM),
        )
        for ((symbol, expected) in cases) {
            summary.symbolCode = symbol
            val weather = requireNotNull(
                MetNoResultConverter.convert(context, location, forecast, air, alerts, nowcast)
            ) { "convert failed for symbol $symbol" }
            assertEquals("text of $symbol", expected.first, weather.current.weatherText)
            assertEquals("icon of $symbol", expected.second, weather.current.weatherCode)
        }
    }

    @Test
    fun convert_mapsAlertsWithTheirLevelAndColour() {
        val alerts = requireNotNull(convertAll()).alertList

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("Forest fire danger", alert.description)
        assertEquals("forestFire", alert.type)
        // "2; yellow; Moderate" carries both the rank and the colour.
        assertEquals(2, alert.priority)
        assertEquals(0xFFFFC107.toInt(), alert.color)
        // Description, consequences and instruction are joined into one body.
        assertTrue(alert.content.contains("wildfire"))
        assertTrue(alert.content.contains("Do not use open fire"))
        // Start of the "when" interval, parsed from the "+00:00" offset form.
        assertEquals(1785918600000L, alert.date.time)
    }

    @Test
    fun convert_survivesOutsideTheNordicsWhereOnlyTheForecastAnswers() {
        // Air quality is Norway-only (HTTP 400 elsewhere), nowcast Nordics-only (422) and alerts are
        // Norwegian — RequestScope degrades all three to null, and the source must still work.
        val weather = MetNoResultConverter.convert(
            context, location, read<MetNoForecastResult>("forecast_oslo.json"), null, null, null
        )

        assertNotNull(weather)
        assertEquals(6, requireNotNull(weather).dailyForecast.size)
        assertEquals(31, weather.hourlyForecast.size)
        assertTrue(weather.minutelyForecast.isEmpty())
        assertTrue(weather.alertList.isEmpty())
        assertFalse(weather.current.airQuality.isValid)
        // The forecast fields still have to be there.
        assertNotNull(weather.current.pressure)
        assertNotNull(weather.current.wind.speed)
    }

    @Test
    fun convert_returnsNullWithoutAUsableForecast() {
        assertNull(MetNoResultConverter.convert(context, location, null, null, null, null))
        assertNull(
            MetNoResultConverter.convert(
                context, location, MetNoForecastResult(), null, null, null
            )
        )
    }
}
