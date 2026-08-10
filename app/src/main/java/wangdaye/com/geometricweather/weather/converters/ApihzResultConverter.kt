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
import kotlin.math.roundToInt
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality
import wangdaye.com.geometricweather.common.basic.models.weather.Alert
import wangdaye.com.geometricweather.common.basic.models.weather.Astro
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
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult

/**
 * Converts the apihz.cn tqybip.php (中国天气网) response into the unified [Weather] model.
 *
 * Notes:
 * - `nowinfo` carries no weather text; the current condition is taken from today's day/night
 *   text (weather1/weather2) by time of day, same approach as the CMA converter.
 * - Weather text -> [WeatherCode] and wind speed -> level reuse [CmaResultConverter] and
 *   [CommonConverter] (same package) rather than duplicating the keyword/Beaufort tables.
 * - Daily wind has only a Chinese scale string (`微风`/`3级`); used as the display level.
 * - Hourly periods are 3-hourly and cross midnight; dates are walked forward from today, bumping a
 *   day whenever the hour wraps, identical to the CMA hourly handling.
 *
 * Fields off [ApihzWeatherResult] are Java platform types: Kotlin will not force a null check on
 * them, so the explicit guards below carry their weight and must stay.
 */
object ApihzResultConverter {

    private val CN_TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private val HOUR_MINUTE = Pattern.compile("(\\d{1,2}):\\d{2}")
    private val TIME_OF_DAY = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?")

    @JvmStatic
    fun convert(context: Context, location: Location, result: ApihzWeatherResult?): Weather? {
        if (result?.code == null || result.code != 200) {
            return null
        }
        return try {
            val now = System.currentTimeMillis()
            Weather(
                Base(location.cityId, now, Date(), now, Date(), now),
                convertCurrent(context, result),
                null,
                convertDailyList(context, result),
                convertHourlyList(context, result),
                ArrayList<Minutely>(),
                convertAlertList(result)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun convertCurrent(context: Context, r: ApihzWeatherResult): Current {
        val now = r.nowinfo

        // nowinfo has no weather text; derive it from today's daily by day/night.
        val isDay = isDaytime()
        var weatherText = if (isDay) r.weather1 else r.weather2
        if (TextUtils.isEmpty(weatherText)) {
            weatherText = if (isDay) r.weather2 else r.weather1
        }

        val temp = now?.temperature?.roundToInt() ?: 0
        val feelsLike = now?.feelst?.roundToInt()
        val windKph = now?.windSpeed?.let { (it * 3.6).toFloat() } ?: 0f
        val windDir = now?.windDirection ?: ""
        val windDegree = now?.windDirectionDegree?.toFloat() ?: 0f

        return Current(
            weatherText ?: "",
            CmaResultConverter.getWeatherCode(weatherText),
            Temperature(temp, feelsLike, null, null, null, null, null),
            Precipitation(now?.precipitation?.toFloat(), null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            Wind(windDir, WindDegree(windDegree, isNoWindDirection(windDir)), windKph,
                CommonConverter.getWindLevel(context, windKph.toDouble())),
            UV(null, null, null),
            // Current declares @NonNull AirQuality (the Java ctor quietly coerced null to this);
            // APIHZ carries no AQI, so pass the empty object the ctor would have built.
            AirQuality(null, null, null, null, null, null, null, null),
            now?.humidity?.toFloat(), now?.pressure?.toFloat(),
            null, null, null, null, null, null
        )
    }

    private fun convertDailyList(context: Context, r: ApihzWeatherResult): List<Daily> {
        val sunMap = buildSunMap(r.suntimes)
        val list = ArrayList<Daily>()

        // Day 1 is flat on the root; its date has no field of its own, so anchor it to today (CN).
        addDaily(context, list, sunMap, todayCn(), r.weather1, r.weather2, r.wd1, r.wd2,
            r.winddirection1, r.winddirection2, r.windleve1, r.windleve2)

        // Days 2-7 are nested objects, each with its own date.
        for (d in listOf(r.weatherday2, r.weatherday3, r.weatherday4,
                r.weatherday5, r.weatherday6, r.weatherday7)) {
            if (d == null) {
                continue
            }
            addDaily(context, list, sunMap, parseDate(d.date), d.weather1, d.weather2, d.wd1, d.wd2,
                d.winddirection1, d.winddirection2, d.windleve1, d.windleve2)
        }
        return list
    }

    private fun addDaily(context: Context, list: MutableList<Daily>,
                         sunMap: Map<String, ApihzWeatherResult.SunTime>, date: Date?,
                         dayText: String?, nightText: String?, high: String?, low: String?,
                         dayDir: String?, nightDir: String?, dayScale: String?, nightScale: String?) {
        if (date == null) {
            return
        }
        list.add(Daily(
            date, date.time,
            buildHalfDay(context, "Day", dayText, dayDir, dayScale, toInt(high)),
            buildHalfDay(context, "Night", nightText, nightDir, nightScale, toInt(low)),
            buildAstro(sunMap, date),
            null, null, null, null,
            UV(null, null, null), 0f
        ))
    }

    private fun buildHalfDay(context: Context, phase: String, text: String?,
                             windDirection: String?, windScale: String?, temp: Int): HalfDay {
        val windDir = windDirection ?: ""
        // Daily wind has no numeric speed, only a Chinese scale string used as the display level.
        val level = if (!TextUtils.isEmpty(windScale)) windScale!!
                    else CommonConverter.getWindLevel(context, 0.0)
        return HalfDay(
            text ?: "",
            phase,
            CmaResultConverter.getWeatherCode(text),
            Temperature(temp, null, null, null, null, null, null),
            Precipitation(null, null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            PrecipitationDuration(null, null, null, null, null),
            Wind(windDir, WindDegree(0f, isNoWindDirection(windDir)), 0f, level),
            null
        )
    }

    private fun convertHourlyList(context: Context, r: ApihzWeatherResult): List<Hourly> {
        val list = ArrayList<Hourly>()
        val all = listOf(r.hour1, r.hour2, r.hour3, r.hour4, r.hour5, r.hour6, r.hour7)
            .filterNotNull()
            .flatten()
        if (all.isEmpty()) {
            return list
        }

        // ponytail: anchor to today and bump a day on each hour-wrap. Good enough for 3-hourly
        // periods; a first period before "now" can be misdated by up to a day. Same as CMA.
        val cal = Calendar.getInstance(CN_TZ)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var prevHour = -1
        var started = false

        for (h in all) {
            val hour = parseHourField(h.time) ?: continue
            if (!started) {
                started = true
            } else if (hour <= prevHour) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            prevHour = hour
            cal.set(Calendar.HOUR_OF_DAY, hour)
            val date = cal.time

            val windKph = parseDecimal(h.windSpeed)?.times(3.6f) ?: 0f
            val wdir = h.windDirection ?: ""

            list.add(Hourly(
                date, date.time, hour in 6..17,
                h.weather ?: "",
                CmaResultConverter.getWeatherCode(h.weather),
                Temperature(parseInt(h.temperature) ?: 0, null, null, null, null, null, null),
                Precipitation(parsePrecip(h.precipitation), null, null, null, null),
                PrecipitationProbability(null, null, null, null, null),
                Wind(wdir, WindDegree(0f, isNoWindDirection(wdir)), windKph,
                    CommonConverter.getWindLevel(context, windKph.toDouble())),
                UV(null, null, null)
            ))
        }
        return list
    }

    private fun convertAlertList(r: ApihzWeatherResult): List<Alert> {
        val list = ArrayList<Alert>()
        val alarms = r.alarm ?: return list
        var id = 0L
        for (a in alarms) {
            if (a == null || TextUtils.isEmpty(a.title)) {
                continue
            }
            val date = parseDateTime(a.effective)
            list.add(Alert(
                id++,
                date ?: Date(),
                date?.time ?: System.currentTimeMillis(),
                a.title,
                joinNonEmpty(a.signaltype, a.signallevel),
                a.type ?: "",
                1,
                0xFFFFB82B.toInt()
            ))
        }
        return list
    }

    // ---- sun times ----

    private fun buildSunMap(
        suntimes: List<ApihzWeatherResult.SunTime?>?
    ): Map<String, ApihzWeatherResult.SunTime> {
        val map = HashMap<String, ApihzWeatherResult.SunTime>()
        if (suntimes == null) {
            return map
        }
        for (st in suntimes) {
            if (st != null && !TextUtils.isEmpty(st.date)) {
                map[st.date] = st
            }
        }
        return map
    }

    private fun buildAstro(sunMap: Map<String, ApihzWeatherResult.SunTime>, date: Date): Astro? {
        val st = sunMap[keyOf(date)] ?: return null
        return Astro(combineTime(date, st.sunrise), combineTime(date, st.sunset))
    }

    private fun combineTime(date: Date?, hms: String?): Date? {
        if (date == null || TextUtils.isEmpty(hms)) {
            return null
        }
        val m = TIME_OF_DAY.matcher(hms!!)
        if (!m.find()) {
            return null
        }
        val cal = Calendar.getInstance(CN_TZ)
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, m.group(1)!!.toInt())
        cal.set(Calendar.MINUTE, m.group(2)!!.toInt())
        cal.set(Calendar.SECOND, m.group(3)?.toInt() ?: 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    // ---- misc helpers ----

    private fun isDaytime() = Calendar.getInstance(CN_TZ).get(Calendar.HOUR_OF_DAY) in 6..17

    private fun isNoWindDirection(dir: String) = TextUtils.isEmpty(dir) || dir.contains("无")

    private fun todayCn(): Date {
        val cal = Calendar.getInstance(CN_TZ)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun keyOf(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = CN_TZ
        return sdf.format(date)
    }

    private fun joinNonEmpty(vararg parts: String?) =
        parts.filter { !TextUtils.isEmpty(it) }.joinToString(" ")

    // Day/night temps arrive as "33" (root) or 29 (nested, coerced to string by Gson).
    private fun toInt(s: String?) = parseDecimal(s)?.roundToInt() ?: 0

    private fun parseInt(s: String?) = parseDecimal(s)?.roundToInt()

    // "无降水" -> 0; "0.5mm" -> 0.5; null/other -> null.
    private fun parsePrecip(s: String?): Float? {
        if (s == null) {
            return null
        }
        return if (s.contains("无")) 0f else parseDecimal(s)
    }

    private fun parseDecimal(s: String?): Float? {
        if (s == null) {
            return null
        }
        val m = DECIMAL.matcher(s)
        return if (m.find()) m.group().toFloatOrNull() else null
    }

    private fun parseHourField(s: String?): Int? {
        if (s == null) {
            return null
        }
        val m = HOUR_MINUTE.matcher(s)
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    // Daily dates: "yyyy/MM/dd" or "yyyy-MM-dd".
    private fun parseDate(s: String?) = parseWithFormats(s, "yyyy/MM/dd", "yyyy-MM-dd")

    // Alert effective times come in mixed formats ("yyyy/MM/dd HH:mm" and "yyyy-MM-dd HH:mm:ss").
    private fun parseDateTime(s: String?) = parseWithFormats(s,
        "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")

    private fun parseWithFormats(s: String?, vararg formats: String): Date? {
        if (TextUtils.isEmpty(s)) {
            return null
        }
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = CN_TZ
                return sdf.parse(s!!)
            } catch (ignored: ParseException) {
            }
        }
        return null
    }
}
