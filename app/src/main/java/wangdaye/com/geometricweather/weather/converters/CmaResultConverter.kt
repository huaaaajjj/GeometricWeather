package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import android.text.TextUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality
import wangdaye.com.geometricweather.common.basic.models.weather.Alert
import wangdaye.com.geometricweather.common.basic.models.weather.Base
import wangdaye.com.geometricweather.common.basic.models.weather.Current
import wangdaye.com.geometricweather.common.basic.models.weather.Daily
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.common.basic.models.weather.Wind
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult

/**
 * Converts the China Meteorological Administration (weather.cma.cn) responses into the unified
 * [Weather] model.
 *
 * Notes specific to CMA:
 * - The `now` block has no weather text/code, so the current condition is taken from today's daily
 *   day/night text.
 * - Daily wind has only a Chinese scale string (`微风`/`3级`), no numeric speed.
 * - Hourly forecast has no JSON endpoint; it is scraped from the `hour-table` HTML. The weather
 *   icon number in the HTML is mapped back to a [WeatherCode] via the `code -> text` pairs already
 *   present in the daily JSON (self-consistent).
 * - Fields off [CmaWeatherResult] are Java platform types: Kotlin will not force a null check on
 *   them, so the explicit guards below carry their weight and must stay.
 */
object CmaResultConverter {

    private val CN_TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private val HOUR_TABLE = Pattern.compile("(?s)<table[^>]*class=\"hour-table\"[^>]*>(.*?)</table>")
    private val TABLE_ROW = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>")
    private val TABLE_CELL = Pattern.compile("(?s)<td[^>]*>(.*?)</td>")
    private val HTML_TAG = Regex("(?s)<[^>]*>")
    private val ICON_NUMBER = Pattern.compile("w(\\d+)\\.png")
    private val HOUR_MINUTE = Pattern.compile("(\\d{1,2}):\\d{2}")
    private val DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?")

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        result: CmaWeatherResult?,
        hourlyHtml: String?
    ): Weather? {
        val data = result?.data ?: return null
        return try {
            var cityId: String? = location.cityId
            if (TextUtils.isEmpty(cityId) && data.location != null) {
                cityId = data.location.id
            }

            val iconCodeMap = buildIconCodeMap(data.daily)
            val now = System.currentTimeMillis()

            Weather(
                Base(cityId, now, Date(), now, Date(), now),
                convertCurrent(context, data),
                null,
                convertDailyList(context, data),
                convertHourlyList(context, hourlyHtml, iconCodeMap),
                ArrayList<Minutely>(),
                convertAlertList(data)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun convertCurrent(context: Context, data: CmaWeatherResult.Data): Current {
        val now = data.now

        // The now block carries no weather text; derive it from today's daily by day/night.
        val isDay = isDaytime()
        var weatherText: String? = ""
        if (!data.daily.isNullOrEmpty()) {
            val today = data.daily[0]
            weatherText = if (isDay) today.dayText else today.nightText
            if (TextUtils.isEmpty(weatherText)) {
                weatherText = if (isDay) today.nightText else today.dayText
            }
        }

        val windKph = now?.windSpeed?.let { msToKph(it) } ?: 0f
        val windDir = now?.windDirection ?: ""
        val noWindDir = TextUtils.isEmpty(windDir) || windDir.contains("无")

        return Current(
            weatherText ?: "",
            getWeatherCode(weatherText),
            Temperature(
                now?.temperature?.toInt() ?: 0,
                now?.feelst?.toInt(),
                null, null, null, null, null
            ),
            Precipitation(now?.precipitation?.toFloat(), null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            Wind(
                windDir,
                WindDegree(now?.windDirectionDegree?.toFloat() ?: 0f, noWindDir),
                windKph,
                CommonConverter.getWindLevel(context, windKph.toDouble())
            ),
            UV(null, null, null),
            AirQuality(null, null, null, null, null, null, null, null),
            now?.humidity?.toFloat(),
            now?.pressure?.toFloat(),
            null, null, null, null, null, null
        )
    }

    private fun convertDailyList(context: Context, data: CmaWeatherResult.Data): List<Daily> {
        val list = ArrayList<Daily>()
        val dailyList = data.daily ?: return list

        for (d in dailyList) {
            val date = parseDate(d.date) ?: continue

            list.add(
                Daily(
                    date,
                    date.time,
                    buildHalfDay(
                        context, "Day", d.dayText, d.dayWindDirection, d.dayWindScale,
                        d.high?.toInt() ?: 0
                    ),
                    buildHalfDay(
                        context, "Night", d.nightText, d.nightWindDirection, d.nightWindScale,
                        d.low?.toInt() ?: 0
                    ),
                    null, null, null, null, null,
                    UV(null, null, null),
                    0f
                )
            )
        }
        return list
    }

    private fun buildHalfDay(
        context: Context,
        phase: String,
        text: String?,
        windDirection: String?,
        windScale: String?,
        temperature: Int
    ): HalfDay {
        val windDir = windDirection ?: ""
        val noWindDir = TextUtils.isEmpty(windDir) || windDir.contains("无")
        // Daily wind has no numeric speed, only a Chinese scale string used as the display level.
        val level = if (!TextUtils.isEmpty(windScale)) {
            windScale!!
        } else {
            CommonConverter.getWindLevel(context, 0.0)
        }
        return HalfDay(
            text ?: "",
            phase,
            getWeatherCode(text),
            Temperature(temperature, null, null, null, null, null, null),
            Precipitation(null, null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            PrecipitationDuration(null, null, null, null, null),
            Wind(windDir, WindDegree(0f, noWindDir), 0f, level),
            null
        )
    }

    private fun convertHourlyList(
        context: Context,
        html: String?,
        iconCodeMap: Map<Int, WeatherCode>
    ): List<Hourly> {
        val list = ArrayList<Hourly>()
        if (html.isNullOrEmpty()) {
            return list
        }
        try {
            val tableMatcher = HOUR_TABLE.matcher(html)

            val cal = Calendar.getInstance(CN_TZ)
            var prevHour = -1
            var started = false

            while (tableMatcher.find()) {
                val rows = parseRows(tableMatcher.group(1) ?: continue)

                val times = rows["时间"]
                if (times.isNullOrEmpty()) {
                    continue
                }
                val weathers = rows["天气"]
                val temps = rows["气温"]
                val winds = rows["风速"]
                val windDirs = rows["风向"]

                for (i in times.indices) {
                    val hour = parseHour(times[i]) ?: continue

                    if (!started) {
                        started = true
                    } else if (hour <= prevHour) {
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    prevHour = hour
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val date = cal.time

                    var code = WeatherCode.CLEAR
                    val icon = at(weathers, i)?.let { extractIcon(it) }
                    if (icon != null) {
                        iconCodeMap[icon]?.let { code = it }
                    }

                    val windKph = (parseDecimal(at(winds, i)) ?: 0f) * 3.6f
                    val windDir = at(windDirs, i)?.let { stripTags(it) } ?: ""
                    val noWindDir = TextUtils.isEmpty(windDir) || windDir.contains("无")

                    list.add(
                        Hourly(
                            date,
                            date.time,
                            hour in 6..17,
                            "",
                            code,
                            Temperature(
                                parseNumber(at(temps, i)) ?: 0,
                                null, null, null, null, null, null
                            ),
                            Precipitation(null, null, null, null, null),
                            PrecipitationProbability(null, null, null, null, null),
                            Wind(
                                windDir,
                                WindDegree(0f, noWindDir),
                                windKph,
                                CommonConverter.getWindLevel(context, windKph.toDouble())
                            ),
                            UV(null, null, null)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return ArrayList()
        }
        return list
    }

    private fun convertAlertList(data: CmaWeatherResult.Data): List<Alert> {
        val list = ArrayList<Alert>()
        val alarms = data.alarm ?: return list

        var id = 0L
        for (a in alarms) {
            if (a == null) {
                continue
            }
            val title = if (!TextUtils.isEmpty(a.headline)) a.headline else a.title
            if (TextUtils.isEmpty(title) && TextUtils.isEmpty(a.description)) {
                continue
            }
            val date = parseDateTime(a.effective)
            list.add(
                Alert(
                    id++,
                    date ?: Date(),
                    date?.time ?: System.currentTimeMillis(),
                    title ?: "",
                    a.description ?: "",
                    a.type ?: "",
                    1,
                    0xFFFFB82B.toInt()
                )
            )
        }
        return list
    }

    /** Builds icon-number -> WeatherCode from the daily code/text pairs (self-consistent). */
    private fun buildIconCodeMap(daily: List<CmaWeatherResult.Daily>?): Map<Int, WeatherCode> {
        val map = HashMap<Int, WeatherCode>()
        if (daily == null) {
            return map
        }
        for (d in daily) {
            if (d.dayCode != null && !TextUtils.isEmpty(d.dayText)) {
                map[d.dayCode] = getWeatherCode(d.dayText)
            }
            if (d.nightCode != null && !TextUtils.isEmpty(d.nightText)) {
                map[d.nightCode] = getWeatherCode(d.nightText)
            }
        }
        return map
    }

    /** Also used by [ApihzResultConverter], which shares CMA's Chinese weather-text vocabulary. */
    internal fun getWeatherCode(text: String?): WeatherCode {
        if (text.isNullOrEmpty()) {
            return WeatherCode.CLEAR
        }
        return when {
            text.contains("雷") && text.contains("雨") -> WeatherCode.THUNDERSTORM
            text.contains("雷") -> WeatherCode.THUNDER
            text.contains("雹") -> WeatherCode.HAIL
            text.contains("雨夹雪") || text.contains("雨雪")
                    || (text.contains("雨") && text.contains("雪")) -> WeatherCode.SLEET
            text.contains("雪") -> WeatherCode.SNOW
            text.contains("雨") -> WeatherCode.RAIN
            text.contains("雾") -> WeatherCode.FOG
            text.contains("霾") || text.contains("沙")
                    || text.contains("尘") -> WeatherCode.HAZE
            text.contains("风") || text.contains("飑")
                    || text.contains("龙卷") -> WeatherCode.WIND
            text.contains("阴") -> WeatherCode.CLOUDY
            text.contains("多云") -> WeatherCode.PARTLY_CLOUDY
            text.contains("晴") -> WeatherCode.CLEAR
            else -> WeatherCode.CLEAR
        }
    }

    // ---- HTML helpers ----

    private fun parseRows(tableHtml: String): Map<String, List<String>> {
        val rows = HashMap<String, List<String>>()
        val rowMatcher = TABLE_ROW.matcher(tableHtml)
        while (rowMatcher.find()) {
            val cells = ArrayList<String>()
            val cellMatcher = TABLE_CELL.matcher(rowMatcher.group(1) ?: continue)
            while (cellMatcher.find()) {
                cellMatcher.group(1)?.let { cells.add(it) }
            }
            if (cells.isEmpty()) {
                continue
            }
            val label = stripTags(cells[0])
            if (TextUtils.isEmpty(label)) {
                continue
            }
            rows[label] = cells.subList(1, cells.size)
        }
        return rows
    }

    private fun at(list: List<String>?, i: Int): String? =
        if (list != null && i < list.size) list[i] else null

    private fun extractIcon(cellHtml: String): Int? {
        val m = ICON_NUMBER.matcher(cellHtml)
        if (m.find()) {
            return m.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun stripTags(s: String?): String {
        if (s == null) {
            return ""
        }
        return s.replace(HTML_TAG, "")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun parseHour(cell: String): Int? {
        val m = HOUR_MINUTE.matcher(stripTags(cell))
        if (m.find()) {
            return m.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun parseNumber(cell: String?): Int? = parseDecimal(cell)?.let { Math.round(it) }

    private fun parseDecimal(cell: String?): Float? {
        if (cell == null) {
            return null
        }
        val m = DECIMAL.matcher(stripTags(cell))
        if (m.find()) {
            return m.group().toFloatOrNull()
        }
        return null
    }

    // ---- misc helpers ----

    private fun isDaytime(): Boolean =
        Calendar.getInstance(CN_TZ).get(Calendar.HOUR_OF_DAY) in 6..17

    private fun msToKph(ms: Double): Float = (ms * 3.6).toFloat()

    private fun parseDate(s: String?): Date? = parse(s, "yyyy/MM/dd")

    private fun parseDateTime(s: String?): Date? = parse(s, "yyyy/MM/dd HH:mm")

    private fun parse(s: String?, pattern: String): Date? {
        if (s == null) {
            return null
        }
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = CN_TZ
            sdf.parse(s)
        } catch (e: ParseException) {
            null
        }
    }
}
