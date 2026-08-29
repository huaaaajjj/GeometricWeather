package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiForecastResult
import wangdaye.com.geometricweather.weather.json.xiaomi.XiaomiMinutelyResult

/**
 * Xiaomi Weather converter.
 *
 * Three things need care here, all of them because the forecast arrays carry no timestamps:
 *
 * 1. **Daily dates come from `sunRiseSet[i].from`, not from `pubTime + i days`.** Abroad the array
 *    starts on the *previous* local date (verified with Paris at 02:31 local: `sunRiseSet[0]` is
 *    yesterday), so counting days off publication would mislabel every entry.
 * 2. **Days already past are dropped.** The app reads `dailyForecast[0]` as today in 76 places, so a
 *    leading yesterday is not a cosmetic problem.
 * 3. **Hourly is anchored on `forecastHourly.temperature.pubTime` + i hours**, which the per-entry
 *    `wind[i].datetime` corroborates. That anchor is the next whole hour, so "now" is not in the list.
 *
 * Unlike most sources here, Xiaomi reports wind in km/h and a real 0-500 Chinese AQI, so neither
 * needs converting — and it does give sunrise/sunset, so per-hour daylight is computed properly
 * instead of guessed from the clock.
 */
object XiaomiResultConverter {

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        forecast: XiaomiForecastResult?,
        minutely: XiaomiMinutelyResult?
    ): Weather? {
        val current = forecast?.current ?: return null
        return try {
            val zone = location.timeZone
            val published = parseIso(current.pubTime) ?: Date()
            val sunByDay = sunByDayOf(forecast.forecastDaily, zone)
            val timestamp = System.currentTimeMillis()

            Weather(
                Base(
                    location.cityId, timestamp,
                    published, published.time,
                    Date(timestamp), timestamp
                ),
                convertCurrent(context, current, forecast.aqi, minutely),
                null,
                convertDailyList(context, forecast.forecastDaily, published, zone),
                convertHourlyList(context, forecast.forecastHourly, sunByDay, zone),
                convertMinutelyList(minutely, sunByDay, zone),
                convertAlertList(forecast.alerts)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun convertCurrent(
        context: Context,
        current: XiaomiForecastResult.Current,
        aqi: XiaomiForecastResult.Aqi?,
        minutely: XiaomiMinutelyResult?
    ): Current = Current(
        weatherTextOf(current.weather),
        weatherCodeOf(current.weather),
        Temperature(
            intOf(current.temperature) ?: 0,
            intOf(current.feelsLike),
            null, null, null, null, null
        ),
        // Neither an amount nor a probability is reported for "now".
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(null, null, null, null, null),
        windOf(context, current.wind?.direction, current.wind?.speed),
        UV(current.uvIndex?.toIntOrNull(), null, null),
        convertAirQuality(context, aqi),
        floatOf(current.humidity),
        floatOf(current.pressure),
        floatOf(current.visibility),
        null,
        null,
        null,
        null,
        // The minutely call's own sentence ("最近的降雨带在东南53公里外呢") is exactly what the app
        // renders as the hourly card's subtitle.
        minutely?.precipitation?.description
    )

    /**
     * Xiaomi hands over a real Chinese AQI index (0-500) alongside the concentrations, so unlike the
     * band-number sources this can be used as-is; the concentration formula is only the fallback.
     * Outside China the whole block arrives as `{"status": -2}` and everything stays null.
     */
    private fun convertAirQuality(context: Context, aqi: XiaomiForecastResult.Aqi?): AirQuality {
        if (aqi == null) {
            return AirQuality(null, null, null, null, null, null, null, null)
        }
        val pm25 = aqi.pm25?.toFloatOrNull()
        val pm10 = aqi.pm10?.toFloatOrNull()
        val index = aqi.aqi?.toFloatOrNull()?.toInt()
            ?: CommonConverter.getAqiIndexFromConcentration(pm25, pm10)
        return AirQuality(
            CommonConverter.getAqiQuality(context, index),
            index,
            pm25,
            pm10,
            aqi.so2?.toFloatOrNull(),
            aqi.no2?.toFloatOrNull(),
            aqi.o3?.toFloatOrNull(),
            // mg/m³ here and mg/m³ in AirQuality.getCOColor's thresholds, so no scaling.
            aqi.co?.toFloatOrNull()
        )
    }

    private fun convertDailyList(
        context: Context,
        daily: XiaomiForecastResult.ForecastDaily?,
        published: Date,
        zone: TimeZone
    ): List<Daily> {
        val codes = daily?.weather?.value ?: return emptyList()
        val today = startOfDay(published, zone).time
        val list = ArrayList<Daily>(codes.size)

        for (i in codes.indices) {
            val date = dayDate(daily, i, published, zone)
            // Outside China the array opens on yesterday; index 0 has to be today.
            if (date.time < today) {
                continue
            }
            val temperature = daily.temperature?.value?.getOrNull(i)
            val direction = daily.wind?.direction?.value?.getOrNull(i)
            val speed = daily.wind?.speed?.value?.getOrNull(i)
            // Shorter than the other arrays (5 against 15), so it simply runs out.
            val probability = daily.precipitationProbability?.value?.getOrNull(i)
            val sun = daily.sunRiseSet?.value?.getOrNull(i)
            val airQualityIndex = daily.aqi?.value?.getOrNull(i)

            list.add(
                Daily(
                    date, date.time,
                    halfDayOf(
                        context, "Day", codes[i].from, temperature?.from,
                        direction?.from, speed?.from, probability
                    ),
                    halfDayOf(
                        context, "Night", codes[i].to, temperature?.to,
                        direction?.to, speed?.to, probability
                    ),
                    Astro(parseIso(sun?.from), parseIso(sun?.to)),
                    null,
                    null,
                    if (airQualityIndex == null) {
                        null
                    } else {
                        AirQuality(
                            CommonConverter.getAqiQuality(context, airQualityIndex),
                            airQualityIndex,
                            null, null, null, null, null, null
                        )
                    },
                    null,
                    // Daily UV lives in the `indices` block, which nothing else here reads.
                    UV(null, null, null),
                    0f
                )
            )
        }
        return list
    }

    private fun halfDayOf(
        context: Context,
        phase: String,
        code: String?,
        temperature: String?,
        degree: String?,
        speed: String?,
        probability: String?
    ): HalfDay = HalfDay(
        weatherTextOf(code),
        phase,
        weatherCodeOf(code),
        Temperature(temperature?.toFloatOrNull()?.toInt() ?: 0, null, null, null, null, null, null),
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(probability?.toFloatOrNull(), null, null, null, null),
        PrecipitationDuration(null, null, null, null, null),
        windOfStrings(context, degree, speed),
        null
    )

    private fun convertHourlyList(
        context: Context,
        hourly: XiaomiForecastResult.ForecastHourly?,
        sunByDay: Map<Long, Astro>,
        zone: TimeZone
    ): List<Hourly> {
        val codes = hourly?.weather?.value ?: return emptyList()
        val anchor = parseIso(hourly.temperature?.pubTime) ?: return emptyList()
        val calendar = Calendar.getInstance(zone)
        val list = ArrayList<Hourly>(codes.size)

        for (i in codes.indices) {
            calendar.time = anchor
            calendar.add(Calendar.HOUR_OF_DAY, i)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val date = calendar.time
            val wind = hourly.wind?.value?.getOrNull(i)

            list.add(
                Hourly(
                    date, date.time,
                    isDaylight(date, sunByDay, zone),
                    weatherTextOf(codes[i].toString()),
                    weatherCodeOf(codes[i].toString()),
                    Temperature(
                        hourly.temperature?.value?.getOrNull(i) ?: 0,
                        null, null, null, null, null, null
                    ),
                    Precipitation(null, null, null, null, null),
                    PrecipitationProbability(null, null, null, null, null),
                    windOfStrings(context, wind?.direction, wind?.speed),
                    UV(null, null, null)
                )
            )
        }
        return list
    }

    /**
     * 120 one-minute steps. The app's [Minutely] has no intensity field — only `isPrecipitation()`
     * off the weather code decides whether the precipitation bar appears at all — so the mm/min
     * values collapse to wet-or-dry. Rain rather than snow for any wet minute, as MfResultConverter
     * also does: the response says nothing about the form.
     */
    private fun convertMinutelyList(
        result: XiaomiMinutelyResult?,
        sunByDay: Map<Long, Astro>,
        zone: TimeZone
    ): List<Minutely> {
        val values = result?.precipitation?.value ?: return ArrayList()
        val anchor = parseIso(result.precipitation.pubTime) ?: return ArrayList()
        val calendar = Calendar.getInstance(zone)
        val list = ArrayList<Minutely>(values.size)

        for (i in values.indices) {
            calendar.time = anchor
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.MINUTE, i)
            val date = calendar.time
            val wet = (values[i] ?: 0.0) > 0.0

            list.add(
                Minutely(
                    date, date.time,
                    isDaylight(date, sunByDay, zone),
                    "",
                    if (wet) WeatherCode.RAIN else WeatherCode.CLEAR,
                    i,
                    null,
                    null,
                    // mm/min — the precipitation bar scales its columns by this.
                    values[i]?.toFloat()
                )
            )
        }
        return list
    }

    /**
     * `level` is a Chinese warning colour — 蓝色 / 黄色 / 橙色 / 红色, ascending — and it carries both the
     * rank and the colour. Some feeds drop the 色 suffix, hence the pairs.
     */
    private fun convertAlertList(alerts: List<XiaomiForecastResult.Alert>?): List<Alert> {
        if (alerts.isNullOrEmpty()) {
            return ArrayList()
        }
        val list = ArrayList<Alert>(alerts.size)
        for (alert in alerts) {
            val title = alert.title ?: alert.type ?: continue
            val start = parseIso(alert.pubTime) ?: Date()
            list.add(
                Alert(
                    // alertId is a String here and a long in the model; it only has to be stable
                    // and distinct within one refresh.
                    (alert.alertId ?: title).hashCode().toLong(),
                    start,
                    start.time,
                    title,
                    alert.detail ?: "",
                    alert.type ?: "",
                    alertPriorityOf(alert.level),
                    alertColorOf(alert.level)
                )
            )
        }
        return list
    }

    private fun alertPriorityOf(level: String?): Int = when (level) {
        "红", "红色" -> 4
        "橙", "橙色", "橘", "橘色", "橘黄", "橘黄色" -> 3
        "黄", "黄色" -> 2
        "蓝", "蓝色" -> 1
        else -> 0
    }

    private fun alertColorOf(level: String?): Int = when (level) {
        "红", "红色" -> 0xFFD7302A.toInt()
        "橙", "橙色", "橘", "橘色", "橘黄", "橘黄色" -> 0xFFF98A1E.toInt()
        "黄", "黄色" -> 0xFFFAED24.toInt()
        "蓝", "蓝色" -> 0xFF3364FF.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    /** Local midnight -> that day's sunrise/sunset, so an hour can be judged against its own day. */
    private fun sunByDayOf(
        daily: XiaomiForecastResult.ForecastDaily?,
        zone: TimeZone
    ): Map<Long, Astro> {
        val values = daily?.sunRiseSet?.value ?: return emptyMap()
        val map = HashMap<Long, Astro>(values.size)
        for (value in values) {
            val rise = parseIso(value?.from) ?: continue
            map[startOfDay(rise, zone).time] = Astro(rise, parseIso(value.to))
        }
        return map
    }

    private fun isDaylight(date: Date, sunByDay: Map<Long, Astro>, zone: TimeZone): Boolean {
        val sun = sunByDay[startOfDay(date, zone).time]
        val rise = sun?.riseDate
        val set = sun?.setDate
        if (rise != null && set != null) {
            // Compared as instants rather than through CommonConverter.isDaylight, which reduces all
            // three to minutes-of-day in the *device's* zone (`Calendar.getInstance()`) and so
            // answers wrongly for any location outside it. Here the sunrise is looked up by the
            // hour's own local date, so the plain instant comparison is both correct and shorter.
            return date.after(rise) && date.before(set)
        }
        // No astro for that date (the hourly list can run one day past the daily list).
        val calendar = Calendar.getInstance(zone)
        calendar.time = date
        return calendar.get(Calendar.HOUR_OF_DAY) in 6..17
    }

    private fun dayDate(
        daily: XiaomiForecastResult.ForecastDaily,
        index: Int,
        published: Date,
        zone: TimeZone
    ): Date {
        val stated = parseIso(daily.sunRiseSet?.value?.getOrNull(index)?.from)
        return if (stated != null) {
            startOfDay(stated, zone)
        } else {
            startOfDay(published, zone, index)
        }
    }

    private fun startOfDay(date: Date, zone: TimeZone, plusDays: Int = 0): Date =
        Calendar.getInstance(zone).apply {
            time = date
            if (plusDays != 0) {
                add(Calendar.DAY_OF_YEAR, plusDays)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    /** Already km/h, so the only work is parsing and a level. */
    private fun windOf(
        context: Context,
        direction: XiaomiForecastResult.UnitValue?,
        speed: XiaomiForecastResult.UnitValue?
    ): Wind = windOfStrings(context, direction?.value, speed?.value)

    private fun windOfStrings(context: Context, degree: String?, speed: String?): Wind {
        val deg = degree?.toFloatOrNull()
        val kph = speed?.toFloatOrNull()
        return Wind(
            if (deg == null) "" else windDirectionOf(deg),
            WindDegree(deg ?: 0f, deg == null),
            kph,
            if (kph == null) "" else CommonConverter.getWindLevel(context, kph.toDouble())
        )
    }

    private fun windDirectionOf(degree: Float): String = when {
        degree < 23 || degree >= 338 -> "N"
        degree < 68 -> "NE"
        degree < 113 -> "E"
        degree < 158 -> "SE"
        degree < 203 -> "S"
        degree < 248 -> "SW"
        degree < 293 -> "W"
        else -> "NW"
    }

    private fun intOf(value: XiaomiForecastResult.UnitValue?): Int? =
        floatOf(value)?.toInt()

    /** Missing readings arrive as `""` rather than as absent fields, so emptiness is the real test. */
    private fun floatOf(value: XiaomiForecastResult.UnitValue?): Float? =
        value?.value?.takeIf { it.isNotEmpty() }?.toFloatOrNull()

    /**
     * The 中国气象局 icon numbers, as also used by 中国天气网. Both the zero-padded and bare spellings
     * appear across the daily/hourly/current blocks, hence the pairs.
     */
    private fun weatherCodeOf(icon: String?): WeatherCode = when (icon) {
        "0", "00" -> WeatherCode.CLEAR
        "1", "01" -> WeatherCode.PARTLY_CLOUDY
        "3", "03", "7", "07", "8", "08", "9", "09", "10", "11", "12",
        "21", "22", "23", "24", "25" -> WeatherCode.RAIN
        "4", "04" -> WeatherCode.THUNDERSTORM
        "5", "05" -> WeatherCode.HAIL
        "6", "06", "19" -> WeatherCode.SLEET
        "13", "14", "15", "16", "17", "26", "27", "28" -> WeatherCode.SNOW
        "18", "32", "49", "57" -> WeatherCode.FOG
        "20", "29", "30", "31" -> WeatherCode.WIND
        "53", "54", "55", "56" -> WeatherCode.HAZE
        // 2 (阴) lands here, as does anything Xiaomi adds later.
        else -> WeatherCode.CLOUDY
    }

    private fun weatherTextOf(icon: String?): String = when (icon) {
        "0", "00" -> "晴"
        "1", "01" -> "多云"
        "2", "02" -> "阴"
        "3", "03" -> "阵雨"
        "4", "04" -> "雷阵雨"
        "5", "05" -> "雷阵雨伴有冰雹"
        "6", "06" -> "雨夹雪"
        "7", "07" -> "小雨"
        "8", "08" -> "中雨"
        "9", "09" -> "大雨"
        "10" -> "暴雨"
        "11" -> "大暴雨"
        "12" -> "特大暴雨"
        "13" -> "阵雪"
        "14" -> "小雪"
        "15" -> "中雪"
        "16" -> "大雪"
        "17" -> "暴雪"
        "18", "32", "49", "57" -> "雾"
        "19" -> "冻雨"
        "20" -> "沙尘暴"
        "21" -> "小到中雨"
        "22" -> "中到大雨"
        "23" -> "大到暴雨"
        "24" -> "暴雨到大暴雨"
        "25" -> "大暴雨到特大暴雨"
        "26" -> "小到中雪"
        "27" -> "中到大雪"
        "28" -> "大到暴雪"
        "29" -> "浮尘"
        "30" -> "扬沙"
        "31" -> "强沙尘暴"
        "53", "54", "55", "56" -> "霾"
        else -> ""
    }

    /**
     * Every timestamp carries an explicit offset ("2026-08-24T08:57:24+08:00"), and one backend adds
     * milliseconds ("...T09:00:00.000+08:00"). minSdk 21 has no ISO-8601 'X' pattern, so the offset's
     * colon is dropped to reach the "+0800" form that 'Z' parses.
     */
    private fun parseIso(value: String?): Date? {
        if (value.isNullOrEmpty()) {
            return null
        }
        val text = when {
            value.endsWith("Z") -> value.dropLast(1) + "+0000"
            // Six from the end is where the sign of "+08:00" sits.
            value.length >= 6 && (value[value.length - 6] == '+' || value[value.length - 6] == '-') ->
                value.removeRange(value.length - 3, value.length - 2)
            else -> "$value+0000"
        }
        for (pattern in PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text)
            } catch (e: ParseException) {
                // try the next pattern
            }
        }
        return null
    }

    private val PATTERNS = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    )
}
