package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import android.text.TextUtils
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
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.common.basic.models.weather.Wind
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree
import wangdaye.com.geometricweather.weather.json.weatherapi.WeatherApiResult

/**
 * Converts the api.weatherapi.com/v1/forecast.json response into the unified [Weather] model.
 *
 * This is the default source since 3.5.2, so every new install lands here first.
 *
 * Notes:
 * - Wind is already km/h upstream (`wind_kph` sits next to `wind_mph`) and must not be scaled.
 * - Astro is a bare 12-hour clock with the date one level up; see [parseDayTime].
 * - The provider sometimes attaches an alert belonging to another admin area; see [isForeignArea].
 * - Fields off [WeatherApiResult] are Java platform types: Kotlin will not force a null check on
 *   them, so the explicit guards below carry their weight and must stay.
 */
object WeatherApiResultConverter {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    private val ADMIN_SUFFIX = Regex("(省|市|自治区|特别行政区|地区|盟)$")

    private val ALERT_COLOR = 0xFFFFB82B.toInt()

    @JvmStatic
    fun convert(context: Context, location: Location, result: WeatherApiResult?): Weather? {
        val current = result?.current ?: return null
        return try {
            val now = System.currentTimeMillis()
            Weather(
                Base(location.cityId, now, Date(), now, Date(), now),
                convertCurrent(context, current),
                null,
                convertDailyList(context, location, result),
                convertHourlyList(context, result),
                ArrayList<Minutely>(),
                convertAlertList(result, location)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun convertCurrent(context: Context, c: WeatherApiResult.Current): Current {
        val windSpeed = c.windKph?.toFloat() ?: 0f
        val windDegree = (c.windDegree ?: 0).toFloat()

        return Current(
            c.condition?.text ?: "Unknown",
            convertWeatherCode(c.condition?.code),
            Temperature(
                c.tempC?.toInt() ?: 0,
                c.feelslikeC?.toInt(),
                null, null, null, null, null
            ),
            Precipitation(c.precipMm?.toFloat(), null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            Wind(
                c.windDir ?: "N",
                WindDegree(windDegree, false),
                windSpeed,
                CommonConverter.getWindLevel(context, windSpeed.toDouble())
            ),
            UV(c.uv?.toInt(), null, null),
            convertAirQuality(context, c.airQuality),
            c.humidity?.toFloat(),
            c.pressureMb?.toFloat(),
            c.visKm?.toFloat(),
            null,
            c.cloud,
            null,
            null,
            null
        )
    }

    private fun convertDailyList(
        context: Context,
        location: Location,
        result: WeatherApiResult
    ): List<Daily> {
        val list = ArrayList<Daily>()
        val forecastDays = result.forecast?.forecastday ?: return list

        for (fd in forecastDays) {
            val d = fd.day ?: continue
            val date = parseDate(fd.date) ?: continue

            val precipitation = d.totalprecipMm?.toFloat()
            val precipitationProbability = d.dailyChanceOfRain?.toFloat()
            val windSpeed = d.maxwindKph?.toFloat() ?: 0f
            val conditionText = d.condition?.text ?: "Unknown"
            val weatherCode = convertWeatherCode(d.condition?.code)

            val day = buildHalfDay(
                context, conditionText, "Day", weatherCode, d.maxtempC?.toInt() ?: 0,
                precipitation, precipitationProbability, windSpeed
            )
            val night = buildHalfDay(
                context, conditionText, "Night", weatherCode, d.mintempC?.toInt() ?: 0,
                precipitation, precipitationProbability, windSpeed
            )

            val astro = fd.astro
            val timeZone = location.timeZone
            val sun = astro?.let {
                Astro(
                    parseDayTime(fd.date, it.sunrise, timeZone),
                    parseDayTime(fd.date, it.sunset, timeZone)
                )
            }
            val moon = astro?.let {
                Astro(
                    parseDayTime(fd.date, it.moonrise, timeZone),
                    parseDayTime(fd.date, it.moonset, timeZone)
                )
            }

            list.add(
                Daily(
                    date, date.time, day, night, sun, moon,
                    astro?.let { convertMoonPhase(it.moonPhase) },
                    convertAirQuality(context, d.airQuality),
                    null,
                    UV(d.uv?.toInt(), null, null),
                    0f
                )
            )
        }
        return list
    }

    /** Day and night share the provider's single daily condition, differing only in temperature. */
    private fun buildHalfDay(
        context: Context,
        conditionText: String,
        phase: String,
        weatherCode: WeatherCode,
        temperature: Int,
        precipitation: Float?,
        precipitationProbability: Float?,
        windSpeed: Float
    ) = HalfDay(
        conditionText,
        phase,
        weatherCode,
        Temperature(temperature, null, null, null, null, null, null),
        Precipitation(precipitation, null, null, null, null),
        PrecipitationProbability(precipitationProbability, null, null, null, null),
        PrecipitationDuration(null, null, null, null, null),
        Wind(
            "N",
            WindDegree(0f, false),
            windSpeed,
            CommonConverter.getWindLevel(context, windSpeed.toDouble())
        ),
        null
    )

    private fun convertHourlyList(context: Context, result: WeatherApiResult): List<Hourly> {
        val list = ArrayList<Hourly>()
        val forecastDays = result.forecast?.forecastday ?: return list

        for (fd in forecastDays) {
            val hours = fd.hour ?: continue
            for (h in hours) {
                val date = parseDateTime(h.time) ?: continue

                val windSpeed = h.windKph?.toFloat() ?: 0f
                val windDegree = (h.windDegree ?: 0).toFloat()

                list.add(
                    Hourly(
                        date,
                        date.time,
                        h.isDay == 1,
                        h.condition?.text ?: "Unknown",
                        convertWeatherCode(h.condition?.code),
                        Temperature(
                            h.tempC?.toInt() ?: 0,
                            h.feelslikeC?.toInt(),
                            null, null, null, null, null
                        ),
                        Precipitation(h.precipMm?.toFloat(), null, null, null, null),
                        PrecipitationProbability(
                            h.chanceOfRain?.toFloat(), null, null, null, null
                        ),
                        Wind(
                            h.windDir ?: "N",
                            WindDegree(windDegree, false),
                            windSpeed,
                            CommonConverter.getWindLevel(context, windSpeed.toDouble())
                        ),
                        UV(h.uv?.toInt(), null, null)
                    )
                )
            }
        }
        return list
    }

    private fun convertAlertList(result: WeatherApiResult, location: Location): List<Alert> {
        val list = ArrayList<Alert>()
        val alerts = result.alerts?.alert ?: return list

        var id = 0L
        for (a in alerts) {
            // WeatherAPI sometimes attaches an alert for a different region (observed: a Beijing
            // 延庆区 warning returned for a Tianjin point). Drop alerts whose areas field names a
            // province/city other than this location's.
            if (isForeignArea(a, location)) {
                continue
            }

            val date = parseDateTime(a.effective)
            list.add(
                Alert(
                    id++,
                    date ?: Date(),
                    date?.time ?: System.currentTimeMillis(),
                    a.headline,
                    a.desc,
                    a.event,
                    1,
                    ALERT_COLOR
                )
            )
        }
        return list
    }

    /**
     * True when the alert clearly belongs to another Chinese admin area than the location. Only
     * judges when WeatherAPI provided an areas field and we have a location name to compare.
     */
    private fun isForeignArea(a: WeatherApiResult.Alert, location: Location): Boolean {
        if (!location.isChina || TextUtils.isEmpty(a.areas)) {
            return false
        }
        if (location.province.isEmpty() && location.city.isEmpty()) {
            return false
        }
        val text = a.areas + " " + (a.headline ?: "")
        return !(nameMentioned(text, location.province) || nameMentioned(text, location.city))
    }

    private fun nameMentioned(text: String, adminName: String): Boolean {
        if (text.isEmpty() || adminName.isEmpty()) {
            return false
        }
        val stem = adminName.replace(ADMIN_SUFFIX, "")
        return stem.isNotEmpty() && text.contains(stem)
    }

    /**
     * [Current] declares airQuality @NonNull and quietly coerces a null to an empty instance, as
     * does [Daily] — a coercion Kotlin cannot lean on, since it will not pass a null in the first
     * place. Building the empty instance here yields the same object at both call sites.
     */
    private fun convertAirQuality(
        context: Context,
        aq: WeatherApiResult.AirQuality?
    ): AirQuality {
        if (aq == null) {
            return AirQuality(null, null, null, null, null, null, null, null)
        }

        val pm25 = aq.pm25?.toFloat()
        val pm10 = aq.pm10?.toFloat()

        // us-epa-index 是 1~6 的档位号，原样写进 aqiIndex 会被当成 0~500 的 AQI，
        // 六档全部落在 ≤50 的第一档 → 空气再脏也是绿色。有浓度就按中国标准算真实
        // AQI（与彩云取 aqi.chn 一致），档位号仅在无浓度时兜底。
        val index: Int? = CommonConverter.getAqiIndexFromConcentration(pm25, pm10)
            ?: CommonConverter.getAqiIndexFromUsEpaCategory(aq.usEpaIndex)

        return AirQuality(
            CommonConverter.getAqiQuality(context, index),
            index,
            pm25,
            pm10,
            aq.so2?.toFloat(),
            aq.no2?.toFloat(),
            aq.o3?.toFloat(),
            aq.co?.toFloat()
        )
    }

    // Current/HalfDay declare weatherCode @NonNull, so a missing code cannot stay null the way the
    // Java version left it — it falls through to the same CLEAR default as any unmapped code.
    private fun convertWeatherCode(code: Int?): WeatherCode {
        if (code == null) {
            return WeatherCode.CLEAR
        }
        return when {
            code == 1000 -> WeatherCode.CLEAR
            code in 1003..1009 -> WeatherCode.CLOUDY
            code in 1030..1035 || code in 1135..1147 -> WeatherCode.FOG
            code in 1063..1069 || code in 1150..1153
                    || code in 1180..1198 || code in 1240..1246 -> WeatherCode.RAIN
            code == 1087 || code in 1273..1282 -> WeatherCode.THUNDERSTORM
            code in 1114..1117 || code in 1210..1225
                    || code in 1255..1264 -> WeatherCode.SNOW
            code in 1168..1171 || code in 1201..1207
                    || code in 1249..1252 -> WeatherCode.SLEET
            code == 1237 -> WeatherCode.HAIL
            else -> WeatherCode.CLEAR
        }
    }

    private fun convertMoonPhase(mp: String?): MoonPhase? = when (mp?.lowercase()) {
        null -> null
        "new moon" -> MoonPhase(0, "new moon")
        "waxing crescent" -> MoonPhase(45, "waxing crescent")
        "first quarter" -> MoonPhase(90, "first quarter")
        "waxing gibbous" -> MoonPhase(135, "waxing gibbous")
        "full moon" -> MoonPhase(180, "full moon")
        "waning gibbous" -> MoonPhase(225, "waning gibbous")
        "last quarter", "third quarter" -> MoonPhase(270, "last quarter")
        "waning crescent" -> MoonPhase(315, "waning crescent")
        else -> MoonPhase(0, "new moon")
    }

    private fun parseDate(value: String?): Date? = parse(value, "yyyy-MM-dd")

    private fun parseDateTime(value: String?): Date? =
        parse(value, "yyyy-MM-dd HH:mm") ?: parse(value, "yyyy-MM-dd'T'HH:mm")

    private fun parse(value: String?, pattern: String): Date? {
        if (value == null) {
            return null
        }
        return try {
            SimpleDateFormat(pattern, Locale.US).parse(value)
        } catch (e: ParseException) {
            null
        }
    }

    /**
     * WeatherAPI reports astro as a bare 12-hour clock ("05:21 AM") with the date living one level
     * up in forecastday.date. Parsing the clock alone yields 1970-01-01, which made
     * [Weather.isDaylight] compare "now" against an epoch-day sunset and answer "night" forever.
     * Anchor the clock to its own day, in the location's zone, so the result is a real instant.
     */
    private fun parseDayTime(dayDate: String?, clock: String?, timeZone: TimeZone): Date? {
        if (dayDate == null || clock.isNullOrEmpty()) {
            return null
        }

        var clockOnly: Date? = null
        for (pattern in arrayOf("hh:mm a", "HH:mm")) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = UTC
                clockOnly = sdf.parse(clock)
                break
            } catch (ignored: ParseException) {
                // try the next pattern
            }
        }
        if (clockOnly == null) {
            return null
        }

        val clockCalendar = Calendar.getInstance(UTC)
        clockCalendar.time = clockOnly

        val day = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = timeZone
            sdf.parse(dayDate)
        } catch (e: ParseException) {
            null
        } ?: return null

        val calendar = Calendar.getInstance(timeZone)
        calendar.time = day
        calendar.set(Calendar.HOUR_OF_DAY, clockCalendar.get(Calendar.HOUR_OF_DAY))
        calendar.set(Calendar.MINUTE, clockCalendar.get(Calendar.MINUTE))
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}
