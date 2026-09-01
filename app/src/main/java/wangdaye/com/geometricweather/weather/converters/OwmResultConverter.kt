package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import android.text.TextUtils
import java.util.Date
import java.util.TimeZone
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
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
import wangdaye.com.geometricweather.weather.json.owm.OwmAirPollutionResult
import wangdaye.com.geometricweather.weather.json.owm.OwmCurrentResult
import wangdaye.com.geometricweather.weather.json.owm.OwmForecastResult
import wangdaye.com.geometricweather.weather.json.owm.OwmLocationResult
import wangdaye.com.geometricweather.weather.services.WeatherService

/**
 * Converts the free OpenWeather data/2.5 responses into the unified [Weather] model.
 *
 * Notes:
 * - OWM's metric units are m/s and metres; the model works in km/h and km.
 * - The free tier has no daily endpoint: [getDailyList] buckets the 3-hour steps into local days
 *   itself, so an empty forecast yields an empty daily list rather than a failure.
 * - `Daily` coerces a null sun/moon/moonPhase/airQuality/pollen to empty instances itself, so
 *   moon/moonPhase/airQuality/pollen are passed as null here (identical objects, fewer imports) —
 *   same as the Open-Meteo converter. The sun is filled only for the current day, from the
 *   observation's own sys.sunrise/sunset; the free forecast carries no astro at all.
 * - Fields off the Owm* results are Java platform types: Kotlin will not force a null check on
 *   them, so the explicit guards below carry their weight and must stay.
 */
object OwmResultConverter {

    private val WIND_DIRECTIONS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    private val CHINA_COUNTRY_CODES = setOf("CN", "cn", "HK", "hk", "TW", "tw")

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        currentResult: OwmCurrentResult?,
        forecastResult: OwmForecastResult?,
        airPollutionResult: OwmAirPollutionResult?
    ): WeatherService.WeatherResultWrapper {
        try {
            if (currentResult == null
                || currentResult.weather.isNullOrEmpty()
                || currentResult.main == null) {
                return WeatherService.WeatherResultWrapper(null)
            }

            val timezoneOffset = currentResult.timezone
            val now = Date(currentResult.dt * 1000)
            val weatherId = currentResult.weather[0].id
            val windSpeed = msToKph(currentResult.wind.speed)
            val windDeg = currentResult.wind.deg.toFloat()

            // The observation's sys.sunrise/sunset are the only sun times the free tier hands
            // over, and they were being dropped: with every astro empty, isDaylight() fell back
            // to a hardcoded 06:00–18:00 and mispainted the theme, widgets and notifications at
            // high latitudes. They describe the location's *current* day, so they attach to that
            // day's bucket rather than to whichever day comes first.
            val sys = currentResult.sys
            val sun = if (sys != null && sys.sunrise != 0L && sys.sunset != 0L) {
                Astro(Date(sys.sunrise * 1000), Date(sys.sunset * 1000))
            } else {
                null
            }

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
                    getWeatherText(weatherId),
                    getWeatherCode(weatherId),
                    Temperature(
                        Math.round(currentResult.main.temp).toInt(),
                        Math.round(currentResult.main.feels_like).toInt(),
                        null, null, null, null, null
                    ),
                    Precipitation(null, null, null, null, null),
                    PrecipitationProbability(null, null, null, null, null),
                    Wind(
                        getWindDirection(windDeg),
                        WindDegree(windDeg, false),
                        windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed.toDouble())
                    ),
                    UV(null, null, null),
                    convertAirQuality(context, airPollutionResult),
                    currentResult.main.humidity.toFloat(),
                    currentResult.main.pressure.toFloat(),
                    // OWM reports visibility in metres; the model works in kilometres.
                    currentResult.visibility / 1000f,
                    null, null, null, null, null
                ),
                null,
                getDailyList(context, forecastResult, timezoneOffset, sun),
                getHourlyList(context, forecastResult, timezoneOffset),
                ArrayList<Minutely>(),
                ArrayList<Alert>()
            )
            return WeatherService.WeatherResultWrapper(weather)
        } catch (e: Exception) {
            e.printStackTrace()
            return WeatherService.WeatherResultWrapper(null)
        }
    }

    private fun getDailyList(
        context: Context,
        forecast: OwmForecastResult?,
        timezoneOffset: Int,
        sun: Astro?
    ): List<Daily> {
        val dailyList = ArrayList<Daily>()
        val entries = forecast?.list
        if (entries.isNullOrEmpty()) {
            return dailyList
        }

        val sunDay = sun?.riseDate?.let { localDay(it.time / 1000, timezoneOffset) }
            ?: Long.MIN_VALUE

        // Group by the *location's* calendar day, the same local clock the day/night split below
        // uses: grouping in the device's zone puts a foreign place's evening on the wrong day and
        // dates every day of the forecast an hour or more off.
        var currentDay = Long.MIN_VALUE
        val dayEntries = ArrayList<OwmForecastResult.ListBean>()

        for (entry in entries) {
            val day = localDay(entry.dt, timezoneOffset)

            if (day != currentDay) {
                if (dayEntries.isNotEmpty()) {
                    buildDaily(context, dayEntries, timezoneOffset,
                        if (currentDay == sunDay) sun else null)?.let { dailyList.add(it) }
                }
                currentDay = day
                dayEntries.clear()
            }
            dayEntries.add(entry)
        }
        if (dayEntries.isNotEmpty()) {
            buildDaily(context, dayEntries, timezoneOffset,
                if (currentDay == sunDay) sun else null)?.let { dailyList.add(it) }
        }

        return dailyList
    }

    private fun buildDaily(
        context: Context,
        entries: List<OwmForecastResult.ListBean>,
        timezoneOffset: Int,
        sun: Astro?
    ): Daily? {
        if (entries.isEmpty()) {
            return null
        }

        // Seeding a running max with Double.MIN_VALUE — the smallest *positive* double, not the
        // most negative one — left an all-sub-zero day reporting a max of ~0, which showed up in
        // the midpoint fallback below. minOf/maxOf need no seed.
        val tempMin = entries.minOf { it.main.temp_min }
        val tempMax = entries.maxOf { it.main.temp_max }

        var dayTemp = 0.0
        var nightTemp = 0.0
        var dayCount = 0
        var nightCount = 0
        var totalPop = 0.0
        var windSpeed = 0.0
        var windDeg = 0.0
        var windCount = 0
        // Day takes the last entry carrying a condition, night the last *night* one. The original
        // also re-assigned the day id inside the daytime branch, which the unconditional
        // assignment below already covers, so that line is gone rather than translated.
        var weatherId = entries[0].weather[0].id
        var nightWeatherId = entries[entries.size - 1].weather[0].id

        for (e in entries) {
            if (!e.weather.isNullOrEmpty()) {
                weatherId = e.weather[0].id
            }
            totalPop += e.pop
            windSpeed += e.wind.speed
            windDeg += e.wind.deg
            windCount++

            val hour = ((e.dt + timezoneOffset) % 86400 / 3600).toInt()
            if (hour in 6..17) {
                dayTemp += e.main.temp
                dayCount++
            } else {
                nightTemp += e.main.temp
                nightCount++
                if (!e.weather.isNullOrEmpty()) {
                    nightWeatherId = e.weather[0].id
                }
            }
        }

        // pop is a 0..1 probability; the model stores percentages.
        val avgPop = (totalPop / entries.size).toFloat() * 100f
        val avgWindSpeed = msToKph(windSpeed / windCount)
        val avgWindDeg = (windDeg / windCount).toFloat()

        val startOfDay = (localDay(entries[0].dt, timezoneOffset) * 86400 - timezoneOffset) * 1000L

        // A partial day can hold no daytime (or no night-time) sample at all; fall back to the
        // day's midpoint instead of dividing by zero.
        val avgDayTemp = if (dayCount > 0) dayTemp / dayCount else (tempMax + tempMin) / 2
        val avgNightTemp = if (nightCount > 0) nightTemp / nightCount else (tempMax + tempMin) / 2

        return Daily(
            Date(startOfDay),
            startOfDay,
            buildHalfDay(context, weatherId, avgDayTemp, avgPop, avgWindSpeed, avgWindDeg),
            buildHalfDay(context, nightWeatherId, avgNightTemp, avgPop, avgWindSpeed, avgWindDeg),
            sun, null, null, null, null,
            UV(null, null, null),
            0f
        )
    }

    private fun buildHalfDay(
        context: Context,
        weatherId: Int,
        temperature: Double,
        precipitationProbability: Float,
        windSpeed: Float,
        windDeg: Float
    ) = HalfDay(
        getWeatherText(weatherId),
        getWeatherText(weatherId),
        getWeatherCode(weatherId),
        Temperature(Math.round(temperature).toInt(), null, null, null, null, null, null),
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(precipitationProbability, null, null, null, null),
        PrecipitationDuration(null, null, null, null, null),
        Wind(
            getWindDirection(windDeg),
            WindDegree(windDeg, false),
            windSpeed,
            CommonConverter.getWindLevel(context, windSpeed.toDouble())
        ),
        null
    )

    private fun getHourlyList(
        context: Context,
        forecast: OwmForecastResult?,
        timezoneOffset: Int
    ): List<Hourly> {
        val hourlyList = ArrayList<Hourly>()
        val entries = forecast?.list ?: return hourlyList

        for (entry in entries) {
            val weatherId = if (!entry.weather.isNullOrEmpty()) entry.weather[0].id else 800
            val time = entry.dt * 1000

            val windKph = msToKph(entry.wind.speed)
            val windDeg = entry.wind.deg.toFloat()

            hourlyList.add(
                Hourly(
                    Date(time),
                    time,
                    isDaylight(entry, timezoneOffset),
                    getWeatherText(weatherId),
                    getWeatherCode(weatherId),
                    Temperature(
                        Math.round(entry.main.temp).toInt(), null, null, null, null, null, null
                    ),
                    Precipitation(entry.rain?.let { it._3h.toFloat() }, null, null, null, null),
                    PrecipitationProbability(
                        entry.pop.toFloat() * 100f, null, null, null, null
                    ),
                    Wind(
                        getWindDirection(windDeg),
                        WindDegree(windDeg, false),
                        windKph,
                        CommonConverter.getWindLevel(context, windKph.toDouble())
                    ),
                    UV(null, null, null)
                )
            )
        }
        return hourlyList
    }

    /**
     * Every hourly entry used to be flagged as daytime, which put sun icons on the 3am steps of
     * the hourly trend. OWM labels each step itself in sys.pod ("d"/"n"); fall back to the same
     * 6..18 local-hour rule the daily bucketing uses when that is absent.
     */
    private fun isDaylight(entry: OwmForecastResult.ListBean, timezoneOffset: Int): Boolean {
        val pod = entry.sys?.pod
        if (!TextUtils.isEmpty(pod)) {
            return "d".equals(pod, ignoreCase = true)
        }
        val hour = ((entry.dt + timezoneOffset) % 86400 / 3600).toInt()
        return hour in 6..17
    }

    /** OWM's metric units report wind in m/s; the model works in km/h. */
    private fun msToKph(metresPerSecond: Double): Float = (metresPerSecond * 3.6).toFloat()

    /**
     * [Current] declares airQuality @NonNull and quietly coerces a null to an empty instance — a
     * coercion Kotlin cannot lean on, since it will not pass a null in the first place. Building
     * the empty instance here yields the same object.
     */
    private fun convertAirQuality(
        context: Context,
        result: OwmAirPollutionResult?
    ): AirQuality {
        val components = result?.list?.firstOrNull()?.components
            ?: return AirQuality(null, null, null, null, null, null, null, null)

        val pm25 = components.pm2_5.toFloat()
        val pm10 = components.pm10.toFloat()

        // main.aqi 是 1~5 的档位号（Good..Very Poor），不是 0~500 的 AQI —— 原样写进
        // aqiIndex 会让五档全部落在 ≤50 的第一档，空气再脏也是绿色。按中国标准用浓度
        // 换算（与彩云取 aqi.chn 一致）。
        val index = CommonConverter.getAqiIndexFromConcentration(pm25, pm10)
        return AirQuality(
            CommonConverter.getAqiQuality(context, index),
            index,
            pm25,
            pm10,
            components.so2.toFloat(),
            components.no2.toFloat(),
            components.o3.toFloat(),
            components.co.toFloat()
        )
    }

    /**
     * The Java version branched on whether the caller already had province/city/district filled in,
     * but both branches built a byte-identical Location, and the zip code it also took was never
     * read. Both parameters are gone rather than carried over as dead weight.
     */
    @JvmStatic
    fun convert(result: OwmLocationResult?): Location? {
        if (result == null) {
            return null
        }
        return Location(
            "${result.lat},${result.lon}",
            result.lat.toFloat(),
            result.lon.toFloat(),
            TimeZone.getTimeZone("UTC"),
            result.country,
            "",
            result.name,
            "",
            null,
            WeatherSource.OWM,
            false,
            false,
            result.country in CHINA_COUNTRY_CODES
        )
    }

    private fun getWeatherText(icon: Int): String = when (icon) {
        201, 202, 210, 211, 212, 221, 230, 231, 232 -> "雷阵雨"
        300, 301, 302, 310, 311, 312, 313, 314, 321 -> "毛毛雨"
        500 -> "小雨"
        501 -> "中雨"
        502 -> "大雨"
        503 -> "暴雨"
        504 -> "大暴雨"
        511 -> "冻雨"
        520, 521 -> "阵雨"
        522, 531 -> "大阵雨"
        600, 620 -> "小雪"
        601, 621 -> "中雪"
        602, 622 -> "大雪"
        611, 612, 613, 614, 615, 616 -> "雨夹雪"
        701, 711, 721, 741 -> "霾"
        731, 751 -> "扬沙"
        761 -> "沙尘"
        762 -> "沙尘暴"
        771 -> "大风"
        781 -> "龙卷风"
        800, 801 -> "晴"
        802, 803 -> "多云"
        804 -> "阴"
        else -> "未知"
    }

    private fun getWeatherCode(icon: Int): WeatherCode = when (icon) {
        201, 202, 210, 211, 212, 221, 230, 231, 232 -> WeatherCode.THUNDERSTORM
        300, 301, 302, 310, 311, 312, 313, 314, 321,
        500, 501, 502, 503, 504, 511, 520, 521, 522, 531 -> WeatherCode.RAIN
        600, 601, 602, 611, 612, 613, 614, 615, 616, 620, 621, 622 -> WeatherCode.SNOW
        701, 711, 721, 731, 741, 751, 761, 762 -> WeatherCode.FOG
        771, 781 -> WeatherCode.WIND
        800 -> WeatherCode.CLEAR
        801, 802 -> WeatherCode.PARTLY_CLOUDY
        803, 804 -> WeatherCode.CLOUDY
        else -> WeatherCode.CLEAR
    }

    private fun getWindDirection(degree: Float): String {
        val index = Math.round(degree / 22.5).toInt() % 16
        return WIND_DIRECTIONS[if (index < 0) 0 else index]
    }

    /**
     * Which local calendar day an epoch second falls on, counted from the epoch. `timezoneOffset`
     * is the location's offset in seconds, straight from the response — the same shift the day/night
     * split uses, so both agree on where a day starts.
     */
    private fun localDay(epochSeconds: Long, timezoneOffset: Int): Long =
        (epochSeconds + timezoneOffset).floorDiv(86400L)
}
