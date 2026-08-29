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
import wangdaye.com.geometricweather.weather.converters.XiaomiResultConverter
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiForecastResult
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiMinutelyResult
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.TimeZone

/**
 * Xiaomi is the richest China source here — current, 15 daily, 23 hourly, real AQI concentrations,
 * alerts and minute-level precipitation from one `weather/all` plus one minutely call — and it also
 * answers worldwide through an `accu:` locationKey, so a conversion fault here costs a lot.
 *
 * Fixtures are real weatherapi.market.xiaomi.com responses, untrimmed:
 *  - `all_beijing.json` / `minutely_beijing.json`, captured together 2026-08-24T08:57+08:00
 *  - `all_beijing_alerts.json`, 2026-08-23T23:02+08:00, kept because it carries four live warnings
 *  - `all_paris.json`, the global path (`accu:1094121`, `isGlobal=true`), whose daily array starts on
 *    the *previous* local date — the case that decides whether `dailyForecast[0]` still means today
 *
 * Robolectric is needed because CommonConverter.getWindLevel/getAqiQuality resolve string resources.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class XiaomiResultConverterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val beijingZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val parisZone = TimeZone.getTimeZone("Europe/Paris")
    private lateinit var defaultTimeZone: TimeZone

    private val beijing = Location(
        "", 39.9042f, 116.4074f, TimeZone.getTimeZone("Asia/Shanghai"),
        "中国", "北京市", "东城", "",
        null, WeatherSource.XIAOMI, false, false, true
    )

    private val paris = Location(
        "", 48.8566f, 2.3522f, TimeZone.getTimeZone("Europe/Paris"),
        "France", "Île-de-France", "Paris", "",
        null, WeatherSource.XIAOMI, false, false, false
    )

    @Before
    fun setUp() {
        // Neither Beijing nor Paris: the daily folding and the "drop past days" cut must key off
        // location.timeZone, and a regression to the JVM default has to fail here.
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private inline fun <reified T> read(name: String): T {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("xiaomi/$name")) {
            "missing fixture xiaomi/$name"
        }
        return Gson().fromJson(InputStreamReader(stream, StandardCharsets.UTF_8), T::class.java)
    }

    private fun convertBeijing() = XiaomiResultConverter.convert(
        context,
        beijing,
        read<XiaomiForecastResult>("all_beijing.json"),
        read<XiaomiMinutelyResult>("minutely_beijing.json")
    )

    private fun midnight(zone: TimeZone, year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis

    @Test
    fun convert_readsCurrentFromTheStringSoup() {
        val weather = requireNotNull(convertBeijing())
        val current = weather.current

        assertEquals(24, current.temperature.temperature)
        assertEquals(28, current.temperature.realFeelTemperature)
        assertEquals(91f, requireNotNull(current.relativeHumidity), 0.01f)
        assertEquals(1006f, requireNotNull(current.pressure), 0.01f)
        assertEquals(3, current.uv.index)
        // Icon "2" is 阴, which has no PARTLY_CLOUDY/CLEAR equivalent and lands on CLOUDY.
        assertEquals("阴", current.weatherText)
        assertEquals(WeatherCode.CLOUDY, current.weatherCode)
        // Reported as "" rather than omitted, which a plain toFloatOrNull would turn into 0.
        assertNull(current.visibility)
        // publishDate is the API's own pubTime (2026-08-24T08:57:24+08:00), not the conversion moment.
        assertEquals(1787533044000L, weather.base.publishTime)
    }

    @Test
    fun convert_keepsWindInKmhBecauseXiaomiAlreadyReportsIt() {
        val wind = requireNotNull(convertBeijing()).current.wind

        assertEquals(2.0f, requireNotNull(wind.speed), 0.01f)
        assertEquals(19.18f, wind.degree.degree, 0.01f)
        assertEquals("N", wind.direction)
        assertFalse(wind.degree.isNoDirection)
    }

    @Test
    fun convert_usesTheReportedAqiIndexAndItsConcentrations() {
        val air = requireNotNull(convertBeijing()).current.airQuality

        // A real 0-500 Chinese index straight from 中国环境监测总站, not a 1..6 band number.
        assertEquals(25, air.aqiIndex)
        assertNotNull(air.aqiText)
        assertEquals(9f, requireNotNull(air.getPM25()), 0.01f)
        assertEquals(13f, requireNotNull(air.getPM10()), 0.01f)
        assertEquals(2f, requireNotNull(air.getSO2()), 0.01f)
        assertEquals(13f, requireNotNull(air.getNO2()), 0.01f)
        assertEquals(77f, requireNotNull(air.getO3()), 0.01f)
        // mg/m³ on both sides, so it is stored unscaled.
        assertEquals(0.6f, requireNotNull(air.getCO()), 0.01f)
    }

    @Test
    fun convert_datesDailyEntriesFromSunriseNotFromPublication() {
        val daily = requireNotNull(convertBeijing()).dailyForecast

        assertEquals(15, daily.size)
        assertEquals(midnight(beijingZone, 2026, 8, 24), daily[0].date.time)
        assertEquals(midnight(beijingZone, 2026, 8, 25), daily[1].date.time)
        // Sunrise/sunset come through, so the day/night background is not a clock guess.
        assertTrue(daily[0].sun().isValid)
        assertEquals(1787520840000L, requireNotNull(daily[0].sun().riseDate).time)
    }

    @Test
    fun convert_splitsTheFromToPairsIntoDayAndNight() {
        val first = requireNotNull(convertBeijing()).dailyForecast[0]

        // temperature {"from":"32","to":"24"} — from is the day high, to the night low.
        assertEquals(32, first.day().temperature.temperature)
        assertEquals(24, first.night().temperature.temperature)
        // weather {"from":"2","to":"1"} — 阴 by day, 多云 at night.
        assertEquals(WeatherCode.CLOUDY, first.day().weatherCode)
        assertEquals(WeatherCode.PARTLY_CLOUDY, first.night().weatherCode)
        assertEquals("多云", first.night().weatherText)
        assertEquals(49f, requireNotNull(first.day().precipitationProbability.total), 0.01f)
        // Daily AQI is an index only, no per-day concentrations.
        assertEquals(25, first.airQuality.aqiIndex)
    }

    @Test
    fun convert_toleratesThePrecipitationProbabilityArrayRunningOut() {
        // It ships 5 entries against the other blocks' 15, which a parallel-index read must survive.
        val daily = requireNotNull(convertBeijing()).dailyForecast

        assertNotNull(daily[4].day().precipitationProbability.total)
        assertNull(daily[5].day().precipitationProbability.total)
        assertNull(daily[14].night().precipitationProbability.total)
        // The rest of day 14 is still there.
        assertEquals(WeatherCode.CLOUDY, daily[14].day().weatherCode)
        assertEquals(34, daily[14].day().temperature.temperature)
    }

    @Test
    fun convert_anchorsHourlyOnTheTemperaturePubTime() {
        val hourly = requireNotNull(convertBeijing()).hourlyForecast

        assertEquals(23, hourly.size)
        // 2026-08-24T09:00+08:00, one entry per hour after it.
        assertEquals(1787533200000L, hourly[0].time)
        assertEquals(1787533200000L + 3600000L, hourly[1].time)
        assertEquals(25, hourly[0].temperature.temperature)
        assertEquals(WeatherCode.PARTLY_CLOUDY, hourly[0].weatherCode)
        // 09:00 sits between the 05:34 sunrise and the 18:59 sunset of its own day.
        assertTrue(hourly[0].isDaylight)
        // The per-hour wind block is a separate array with its own strings.
        assertEquals(2.52f, requireNotNull(hourly[0].wind.speed), 0.01f)
        assertEquals("NE", hourly[0].wind.direction)
    }

    @Test
    fun convert_mapsTheTwoHourMinutelyWindow() {
        val weather = requireNotNull(convertBeijing())
        val minutely = weather.minutelyForecast

        assertEquals(120, minutely.size)
        // Anchored on the minutely call's own pubTime with the seconds dropped (09:01:34 -> 09:01).
        assertEquals(1787533260000L, minutely[0].time)
        assertEquals(0, minutely[0].minuteInterval)
        assertEquals(1, minutely[1].minuteInterval)
        assertEquals(119, minutely[119].minuteInterval)
        // This capture is dry throughout, so the app's precipitation bar stays hidden.
        assertTrue(minutely.none { it.isPrecipitation })
        // The call's own sentence becomes the hourly card's subtitle.
        assertEquals("最近的降雨带在东南53公里外呢", weather.current.hourlyForecast)
    }

    @Test
    fun convert_rainInTheWindowMarksThoseMinutesWet() {
        // The captured fixture is dry throughout, so the rain is injected onto the real response:
        // 0.5/0.2 mm/min in minutes 40..41. The app reads wet-or-dry, not intensity — a wet minute
        // is what makes the main screen's minutely bar visible at all (needToShowMinutelyForecast).
        val rain = read<XiaomiMinutelyResult>("minutely_beijing.json").apply {
            precipitation.isRainOrSnow = 1
            precipitation.value[40] = 0.5
            precipitation.value[41] = 0.2
        }
        val minutely = requireNotNull(
            XiaomiResultConverter.convert(
                context, beijing, read<XiaomiForecastResult>("all_beijing.json"), rain
            )
        ).minutelyForecast

        assertEquals(120, minutely.size)
        assertFalse(minutely[39].isPrecipitation)
        assertTrue(minutely[40].isPrecipitation)
        assertTrue(minutely[41].isPrecipitation)
        assertFalse(minutely[42].isPrecipitation)
    }

    @Test
    fun convert_marksWetMinutesAsPrecipitation() {
        // Built by hand rather than from a fixture: rain has to be falling at capture time to get a
        // non-zero array, and the wet branch is the half that makes the bar appear at all.
        val wet = XiaomiMinutelyResult().apply {
            status = 0
            precipitation = XiaomiMinutelyResult.Precipitation().apply {
                pubTime = "2026-08-24T09:01:34+08:00"
                description = "您北边5公里正在下小雨哦"
                value = listOf(0.0, 0.0263, 0.0527, 0.0)
            }
        }
        val minutely = requireNotNull(
            XiaomiResultConverter.convert(
                context, beijing, read<XiaomiForecastResult>("all_beijing.json"), wet
            )
        ).minutelyForecast

        assertEquals(4, minutely.size)
        assertFalse(minutely[0].isPrecipitation)
        assertTrue(minutely[1].isPrecipitation)
        assertTrue(minutely[2].isPrecipitation)
        assertFalse(minutely[3].isPrecipitation)
        assertEquals(WeatherCode.RAIN, minutely[1].weatherCode)
    }

    @Test
    fun convert_mapsAlertsWithTheirWarningColour() {
        val alerts = requireNotNull(
            XiaomiResultConverter.convert(
                context, beijing, read<XiaomiForecastResult>("all_beijing_alerts.json"), null
            )
        ).alertList

        assertEquals(4, alerts.size)
        assertEquals("东城发布暴雨蓝色预警", alerts[0].description)
        assertEquals("暴雨", alerts[0].type)
        // 蓝色 is the lowest of the four Chinese warning levels.
        assertEquals(1, alerts[0].priority)
        assertEquals(0xFF3364FF.toInt(), alerts[0].color)
        assertTrue(alerts[0].content.contains("暴雨蓝色预警"))
        // 黄色 outranks it.
        assertEquals("雷电", alerts[2].type)
        assertEquals(2, alerts[2].priority)
        assertEquals(0xFFFAED24.toInt(), alerts[2].color)
        // Distinct ids from distinct alertId strings.
        assertTrue(alerts.map { it.alertId }.toSet().size == 4)
    }

    @Test
    fun convert_dropsTheLeadingPastDayOnTheGlobalPath() {
        // Paris was captured at 02:31 local, and its daily array opens on 2026-08-23 — yesterday.
        // Counting days off publication would mislabel all five; keeping it would put yesterday at
        // dailyForecast[0], which 76 sites in the app read as today.
        val weather = requireNotNull(
            XiaomiResultConverter.convert(
                context, paris, read<XiaomiForecastResult>("all_paris.json"), null
            )
        )
        val daily = weather.dailyForecast

        assertEquals(4, daily.size)
        assertEquals(midnight(parisZone, 2026, 8, 24), daily[0].date.time)
        // Day 0 must carry day-index-1's values, not day-index-0's.
        assertEquals(27, daily[0].day().temperature.temperature)
        assertEquals(16, daily[0].night().temperature.temperature)
        assertEquals(15f, requireNotNull(daily[0].day().precipitationProbability.total), 0.01f)
        assertEquals(WeatherCode.RAIN, daily[0].night().weatherCode)
    }

    @Test
    fun convert_survivesTheGlobalPathHavingNoAirQuality() {
        // Abroad the whole aqi block arrives as {"status": -2}, daily and current alike.
        val weather = requireNotNull(
            XiaomiResultConverter.convert(
                context, paris, read<XiaomiForecastResult>("all_paris.json"), null
            )
        )

        assertFalse(weather.current.airQuality.isValid)
        assertTrue(weather.dailyForecast.none { it.airQuality.isValid })
        // Minutely is China-only too, and this call had none.
        assertTrue(weather.minutelyForecast.isEmpty())
        assertTrue(weather.alertList.isEmpty())
        assertNull(weather.current.hourlyForecast)
        // The forecast itself still has to be complete.
        assertEquals(23, weather.hourlyForecast.size)
        assertEquals(17, weather.hourlyForecast[0].temperature.temperature)
        // 03:00 is before the 06:55 sunrise of its own day.
        assertFalse(weather.hourlyForecast[0].isDaylight)
        assertNotNull(weather.current.pressure)
    }

    @Test
    fun convert_returnsNullWithoutACurrentBlock() {
        assertNull(XiaomiResultConverter.convert(context, beijing, null, null))
        assertNull(
            XiaomiResultConverter.convert(context, beijing, XiaomiForecastResult(), null)
        )
    }
}
