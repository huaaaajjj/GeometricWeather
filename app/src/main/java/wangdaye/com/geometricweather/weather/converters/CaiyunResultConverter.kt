package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import wangdaye.com.geometricweather.weather.json.caiyun.CaiYunWeatherResult
import wangdaye.com.geometricweather.weather.services.WeatherService

/**
 * Converts the api.caiyunapp.com v2.6 response into the unified [Weather] model.
 *
 * Notes:
 * - Humidity arrives as a 0..1 fraction and pressure in pascals; the model wants percent and hPa.
 * - Astro is a bare "HH:mm" clock next to the day's own date; see [parseTime].
 * - `Daily` coerces a null moon/moonPhase/pollen to empty instances itself, so those are passed as
 *   null here (identical objects, fewer imports) — same as the other converters.
 * - The bundled token is a trial one: daily is capped at 3 days and there is no minutely block.
 * - Fields off [CaiYunWeatherResult] are Java platform types: Kotlin will not force a null check on
 *   them, so the explicit guards below carry their weight and must stay.
 */
object CaiyunResultConverter {

    private val WIND_DIRECTIONS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        result: CaiYunWeatherResult?
    ): WeatherService.WeatherResultWrapper {
        try {
            val r = result?.result?.realtime
                ?: return WeatherService.WeatherResultWrapper(null)

            val daily = result.result.daily
            val hourly = result.result.hourly

            val now = Date(result.server_time * 1000)
            val skycon = r.skycon ?: "CLEAR_DAY"
            val windSpeed = r.wind.speed.toFloat()

            val weather = Weather(
                Base(
                    location.cityId,
                    now.time,
                    now,
                    now.time,
                    now,
                    now.time
                ),
                Current(
                    getWeatherText(skycon),
                    getWeatherCode(skycon),
                    Temperature(
                        Math.round(r.temperature).toInt(),
                        // apparent_temperature is a primitive, so it reads 0.0 when the payload
                        // omits it; life_index is the marker for "this response carries the rich
                        // fields". Guarding on it is what keeps a missing value off the screen as
                        // 0° rather than as nothing.
                        if (r.life_index != null) Math.round(r.apparent_temperature).toInt() else null,
                        null, null, null, null, null
                    ),
                    Precipitation(
                        r.precipitation?.local?.intensity?.toFloat(),
                        null, null, null, null
                    ),
                    PrecipitationProbability(null, null, null, null, null),
                    Wind(
                        getWindDirection(r.wind.direction),
                        WindDegree(r.wind.direction.toFloat(), false),
                        windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed.toDouble())
                    ),
                    UV(r.life_index?.ultraviolet?.index, null, null),
                    getAirQuality(context, r.air_quality),
                    // CaiYun reports humidity as a 0..1 fraction and pressure in pascals;
                    // the model wants percent and hPa, same as every other source.
                    (r.humidity * 100).toFloat(),
                    (r.pressure / 100).toFloat(),
                    r.visibility.toFloat(),
                    null,
                    // 0..1 fraction -> percent for the detail gauge. Without this the multi-source
                    // merge fills the gap from another provider while the card still credits this one.
                    r.cloudrate?.times(100)?.roundToInt(),
                    null, null,
                    result.result.forecast_keypoint
                ),
                null,
                getDailyList(context, daily, result.timezone),
                getHourlyList(hourly, result.timezone),
                ArrayList<Minutely>(),
                getAlertList(result.result.alert)
            )
            return WeatherService.WeatherResultWrapper(weather)
        } catch (e: Exception) {
            e.printStackTrace()
            return WeatherService.WeatherResultWrapper(null)
        }
    }

    private fun getAirQuality(
        context: Context,
        aq: CaiYunWeatherResult.AirQualityBean?
    ): AirQuality {
        if (aq == null || aq.isMissing()) {
            return emptyAirQuality()
        }
        // aqi.chn, not aqi.usa: the whole app reads aqiIndex as a 0..500 China AQI.
        val index = aq.aqi?.chn
        var quality: String? = null
        try {
            quality = CommonConverter.getAqiQuality(context, index)
        } catch (ignored: Exception) {
        }
        return AirQuality(
            quality,
            index,
            aq.pm25.toFloat(),
            aq.pm10.toFloat(),
            aq.so2.toFloat(),
            aq.no2.toFloat(),
            aq.o3.toFloat(),
            aq.co.toFloat()
        )
    }

    /**
     * Outside its coverage 彩云 answers with a whole block of zeros instead of with missing fields:
     * `aqi.chn` 0, all six concentrations 0, `description.chn` 缺数据 — New York, Oslo and Sydney all
     * read like this. Passing that through would claim an AQI of 0, which the app renders as 优, and
     * in the multi-source merge a zero is a non-null field, so the fake reading beats another
     * provider's real one (that is how 東京 came to show 0 / 优 while Open-Meteo had real numbers).
     * No reading is the honest answer; the card then hides itself and the merge falls through.
     */
    private fun CaiYunWeatherResult.AirQualityBean.isMissing() =
        (aqi?.chn ?: 0) == 0 &&
            pm25 == 0 && pm10 == 0 && o3 == 0 && so2 == 0 && no2 == 0 && co == 0.0

    private fun emptyAirQuality() = AirQuality(null, null, null, null, null, null, null, null)

    private fun getDailyList(
        context: Context,
        daily: CaiYunWeatherResult.DailyBean?,
        timezone: String?
    ): List<Daily> {
        val list = ArrayList<Daily>()
        if (daily == null || daily.skycon == null || daily.temperature == null) {
            return list
        }

        val count = minOf(daily.skycon.size, daily.temperature.size)
        for (i in 0 until count) {
            val daySkycon = daily.skycon[i].value
            val nightSkycon =
                if (daily.skycon_20h_32h != null && i < daily.skycon_20h_32h.size) {
                    daily.skycon_20h_32h[i].value
                } else {
                    daySkycon
                }
            val tempMax = daily.temperature[i].max ?: 0.0
            val tempMin = daily.temperature[i].min ?: 0.0

            val calendar = Calendar.getInstance()
            val date = runCatching { parseDate(daily.skycon[i].date, timezone) }.getOrNull()
            if (date != null) {
                calendar.time = date
            } else {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, i)
            }

            list.add(
                Daily(
                    calendar.time,
                    calendar.timeInMillis,
                    buildHalfDay(daySkycon, tempMax, getWind(context, daily, i, true)),
                    buildHalfDay(nightSkycon, tempMin, getWind(context, daily, i, false)),
                    getAstro(daily, i, timezone),
                    null, null,
                    getDailyAirQuality(context, daily, i),
                    null,
                    UV(null, null, null),
                    0f
                )
            )
        }
        return list
    }

    /** CaiYun gives one condition per half-day, so text and phase are the same string. */
    private fun buildHalfDay(
        skycon: String?,
        temperature: Double,
        wind: Wind
    ) = HalfDay(
        getWeatherText(skycon),
        getWeatherText(skycon),
        getWeatherCode(skycon),
        Temperature(Math.round(temperature).toInt(), null, null, null, null, null, null),
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(null, null, null, null, null),
        PrecipitationDuration(null, null, null, null, null),
        wind,
        null
    )

    private fun getAstro(
        daily: CaiYunWeatherResult.DailyBean,
        i: Int,
        timezone: String?
    ): Astro? {
        try {
            val astroList = daily.astro
            if (astroList != null && i < astroList.size) {
                val astro = astroList[i]
                return Astro(
                    parseTime(astro.date, astro.sunrise.time, timezone),
                    parseTime(astro.date, astro.sunset.time, timezone)
                )
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    /**
     * Direction-less placeholder for the entries CaiYun leaves wind out of. The Java version
     * returned `Wind(null, null, null, null)` from its catch arm, which is the exact shape that
     * crashed the v2.6 migration — three of Wind's four slots are @NonNull.
     */
    private fun emptyWind() = Wind("", WindDegree(0f, true), null, "")

    private fun getWind(
        context: Context,
        daily: CaiYunWeatherResult.DailyBean,
        i: Int,
        day: Boolean
    ): Wind {
        return try {
            val windData = when {
                daily.wind != null && i < daily.wind.size -> daily.wind[i]
                day && daily.wind_08h_20h != null && i < daily.wind_08h_20h.size ->
                    daily.wind_08h_20h[i]
                !day && daily.wind_20h_32h != null && i < daily.wind_20h_32h.size ->
                    daily.wind_20h_32h[i]
                else -> return emptyWind()
            }
            val speed = (windData.avg?.speed ?: 0.0).toFloat()
            val direction = windData.avg?.direction ?: 0.0
            Wind(
                getWindDirection(direction),
                WindDegree(direction.toFloat(), false),
                speed,
                CommonConverter.getWindLevel(context, speed.toDouble())
            )
        } catch (e: Exception) {
            emptyWind()
        }
    }

    private fun getDailyAirQuality(
        context: Context,
        daily: CaiYunWeatherResult.DailyBean,
        i: Int
    ): AirQuality {
        try {
            val aqDay = daily.air_quality
            if (aqDay != null) {
                var aqiValue = 0
                val aqiList = aqDay.aqi
                if (!aqiList.isNullOrEmpty()) {
                    aqiValue = aqiList[minOf(i, aqiList.size - 1)].avg?.chn ?: 0
                }
                var pm25: Int? = null
                val pm25List = aqDay.pm25
                if (!pm25List.isNullOrEmpty()) {
                    pm25 = pm25List[minOf(i, pm25List.size - 1)].avg
                }
                // Same shape as realtime (see AirQualityBean.isMissing): a day whose average AQI is
                // 0 with no PM2.5 behind it is an absent reading, not spotless air.
                if (aqiValue == 0 && (pm25 ?: 0) == 0) {
                    return emptyAirQuality()
                }
                return AirQuality(
                    CommonConverter.getAqiQuality(context, aqiValue),
                    aqiValue,
                    pm25?.toFloat(),
                    null, null, null, null, null
                )
            }
        } catch (ignored: Exception) {
        }
        return emptyAirQuality()
    }

    private fun getHourlyList(
        hourly: CaiYunWeatherResult.HourlyBean?,
        timezone: String?
    ): List<Hourly> {
        val list = ArrayList<Hourly>()
        if (hourly == null || hourly.temperature == null || hourly.skycon == null) {
            return list
        }

        val count = minOf(hourly.temperature.size, hourly.skycon.size)
        for (i in 0 until count) {
            val skycon = hourly.skycon[i].value
            val temperature = hourly.temperature[i].value

            val calendar = Calendar.getInstance()
            val date = runCatching { parseDate(hourly.skycon[i].datetime, timezone) }.getOrNull()
            if (date != null) {
                calendar.time = date
            } else {
                calendar.time = Date()
                calendar.add(Calendar.HOUR_OF_DAY, i)
            }

            list.add(
                Hourly(
                    calendar.time,
                    calendar.timeInMillis,
                    skycon != null && (skycon.contains("DAY") || !skycon.contains("NIGHT")),
                    getWeatherText(skycon),
                    getWeatherCode(skycon),
                    Temperature(
                        Math.round(temperature).toInt(), null, null, null, null, null, null
                    ),
                    Precipitation(null, null, null, null, null),
                    PrecipitationProbability(null, null, null, null, null),
                    emptyWind(),
                    UV(null, null, null)
                )
            )
        }
        return list
    }

    private fun getAlertList(alertBean: CaiYunWeatherResult.AlertBean?): List<Alert> {
        val list = ArrayList<Alert>()
        val contents = alertBean?.content ?: return list

        var id = 0L
        for (c in contents) {
            if (c == null || TextUtils.isEmpty(c.title)) {
                continue
            }
            val time = if (c.pubtimestamp > 0) c.pubtimestamp * 1000 else System.currentTimeMillis()
            list.add(
                Alert(
                    id++,
                    Date(time),
                    time,
                    c.title ?: "",
                    c.description ?: "",
                    c.code ?: "",
                    1,
                    0xFFFFB82B.toInt()
                )
            )
        }
        return list
    }

    private fun getWeatherText(skycon: String?): String {
        if (skycon.isNullOrEmpty()) {
            return "未知"
        }
        return when (skycon) {
            "CLEAR_DAY", "CLEAR_NIGHT" -> "晴"
            "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> "多云"
            "CLOUDY" -> "阴"
            "LIGHT_RAIN" -> "小雨"
            "MODERATE_RAIN" -> "中雨"
            "HEAVY_RAIN" -> "大雨"
            "STORM_RAIN" -> "暴雨"
            "LIGHT_SNOW" -> "小雪"
            "MODERATE_SNOW" -> "中雪"
            "HEAVY_SNOW" -> "大雪"
            "STORM_SNOW" -> "暴雪"
            "LIGHT_HAIL", "HAIL" -> "冰雹"
            "LIGHT_SLEET", "SLEET" -> "雨夹雪"
            "THUNDERSTORM" -> "雷阵雨"
            "THUNDER" -> "雷雨"
            "FOG" -> "雾"
            // v2.6 grades 霾 into three levels; the bare "HAZE" stays for older payloads.
            "HAZE" -> "霾"
            "LIGHT_HAZE" -> "轻度雾霾"
            "MODERATE_HAZE" -> "中度雾霾"
            "HEAVY_HAZE" -> "重度雾霾"
            "WIND" -> "大风"
            "DUST" -> "扬沙"
            "SAND" -> "沙尘暴"
            else -> getWeatherFamilyText(skycon)
        }
    }

    /**
     * Names an unlisted skycon by its family instead of printing "未知".
     *
     * The table above matches exactly, [WeatherCode.getInstance] matches by substring — so when
     * v2.6 graded 霾 into LIGHT_/MODERATE_/HEAVY_HAZE, 南开区's header read "未知, 体感 30°" over a
     * correctly hazy icon and background. Matching the same way here keeps the two in step the next
     * time a level is added. Order matters where one family's name contains another's.
     */
    private fun getWeatherFamilyText(skycon: String): String {
        val value = skycon.uppercase(Locale.ROOT)
        return when {
            value.contains("HAZE") -> "霾"
            value.contains("FOG") -> "雾"
            value.contains("SLEET") -> "雨夹雪"
            value.contains("HAIL") -> "冰雹"
            value.contains("THUNDER") -> "雷雨"
            value.contains("RAIN") -> "雨"
            value.contains("SNOW") -> "雪"
            value.contains("PARTLY_CLOUDY") -> "多云"
            value.contains("CLOUDY") -> "阴"
            value.contains("CLEAR") -> "晴"
            value.contains("WIND") -> "大风"
            value.contains("DUST") -> "扬沙"
            value.contains("SAND") -> "沙尘暴"
            else -> "未知"
        }
    }

    private fun getWeatherCode(skycon: String?): WeatherCode =
        WeatherCode.getInstance(skycon ?: "")

    private fun getWindDirection(degree: Double): String {
        val index = Math.round(degree / 22.5).toInt() % 16
        return WIND_DIRECTIONS[if (index < 0) 0 else index]
    }

    private fun parseDate(dateStr: String?, timezone: String?): Date? {
        if (dateStr == null) {
            return null
        }
        val zone = TimeZone.getTimeZone(timezone ?: "Asia/Shanghai")
        for (pattern in arrayOf("yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = zone
                return sdf.parse(dateStr)
            } catch (ignored: Exception) {
                // try the next pattern
            }
        }
        return null
    }

    /**
     * CaiYun reports astro as a bare "HH:mm" clock next to the day's own date. Parsing the clock
     * alone produced a 1970-01-01 instant, which made [Weather.isDaylight] compare "now" against an
     * epoch-day sunset and answer "night" forever. Anchor the clock to its day.
     */
    private fun parseTime(dayDate: String?, timeStr: String?, timezone: String?): Date? {
        if (timeStr == null) {
            return null
        }
        val day = parseDate(dayDate, timezone)
        val zone = TimeZone.getTimeZone(timezone ?: "Asia/Shanghai")
        val utc = TimeZone.getTimeZone("UTC")
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.timeZone = utc
            val clock = sdf.parse(timeStr) ?: return null
            if (day == null) {
                return clock
            }

            val clockCalendar = Calendar.getInstance(utc)
            clockCalendar.time = clock

            val calendar = Calendar.getInstance(zone)
            calendar.time = day
            calendar.set(Calendar.HOUR_OF_DAY, clockCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, clockCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.time
        } catch (e: Exception) {
            null
        }
    }
}
