package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import wangdaye.com.geometricweather.R
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
import wangdaye.com.geometricweather.common.basic.models.weather.Pollen
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.common.basic.models.weather.Wind
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoAirQualityResult
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult

object OpenMeteoResultConverter {

    @JvmStatic
    @JvmOverloads
    fun convert(
        context: Context,
        location: Location,
        result: OpenMeteoResult?,
        airQualityResult: OpenMeteoAirQualityResult? = null
    ): Weather? {
        if (result == null) {
            return null
        }
        return try {
            val now = System.currentTimeMillis()
            Weather(
                Base(location.cityId, now, Date(), now, Date(), now),
                convertCurrent(context, result, airQualityResult),
                null,
                convertDailyList(context, result, convertPollenMap(context, airQualityResult)),
                convertHourlyList(context, result),
                ArrayList<Minutely>(),
                ArrayList<Alert>()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun convertCurrent(
        context: Context,
        result: OpenMeteoResult,
        airQualityResult: OpenMeteoAirQualityResult?
    ): Current {
        val current = checkNotNull(result.current)
        val temperature = current.temperature?.toInt() ?: 0
        val feelsLike = current.apparentTemperature?.toInt()
        val precipitation = current.precipitation?.toFloat()
        val windSpeed = current.windSpeed?.toFloat() ?: 0f
        val windDirection = current.windDirection?.toInt() ?: 0
        val humidity = current.humidity?.toFloat()
        val pressure = current.pressure?.toFloat()
        val cloudCover = current.cloudCover?.toInt()

        // The AQI comes from concentrations, never from the endpoint's european_aqi/us_aqi grade
        // numbers — the whole app reads aqiIndex as a 0-500 China AQI.
        val air = airQualityResult?.current
        val pm25 = air?.pm25?.toFloat()
        val pm10 = air?.pm10?.toFloat()
        val aqiIndex = CommonConverter.getAqiIndexFromConcentration(pm25, pm10)
        val airQuality = AirQuality(
            CommonConverter.getAqiQuality(context, aqiIndex),
            aqiIndex,
            pm25,
            pm10,
            air?.sulphurDioxide?.toFloat(),
            air?.nitrogenDioxide?.toFloat(),
            air?.ozone?.toFloat(),
            // The API reports CO in μg/m³, the model's default CO unit is mg/m³.
            air?.carbonMonoxide?.toFloat()?.div(1000f)
        )

        return Current(
            getWeatherText(current.weatherCode),
            convertWeatherCode(current.weatherCode),
            Temperature(temperature, feelsLike, null, null, null, null, null),
            Precipitation(precipitation, null, null, null, null),
            PrecipitationProbability(null, null, null, null, null),
            Wind(
                getWindDirection(windDirection),
                WindDegree(windDirection.toFloat(), false),
                windSpeed,
                CommonConverter.getWindLevel(context, windSpeed.toDouble())
            ),
            UV(null, null, null),
            airQuality,
            humidity,
            pressure,
            null,
            null,
            cloudCover,
            null,
            null,
            null
        )
    }

    private fun convertDailyList(
        context: Context,
        result: OpenMeteoResult,
        pollenMap: Map<String, Pollen>
    ): List<Daily> {
        val daily = result.daily ?: return emptyList()
        val times = daily.time ?: return emptyList()
        val list = ArrayList<Daily>()

        for (i in times.indices) {
            val date = parseDate(times[i]) ?: continue
            val weatherCode = getVal(daily.weatherCode, i)
            val temperatureMax = getIntVal(daily.temperatureMax, i)
            val temperatureMin = getIntVal(daily.temperatureMin, i)
            val precipitation = getFloatVal(daily.precipitationSum, i)
            val precipitationProbability = getFloatVal(daily.precipitationProbabilityMax, i)
            val windSpeed = getFloatValOrZero(daily.windSpeedMax, i)
            val windDirection = getIntVal(daily.windDirectionDominant, i)
            val uvIndex = getNullableIntVal(daily.uvIndexMax, i)

            val day = buildHalfDay(
                context, "Day", weatherCode, temperatureMax, precipitation,
                precipitationProbability, windSpeed, windDirection
            )
            val night = buildHalfDay(
                context, "Night", weatherCode, temperatureMin, precipitation,
                precipitationProbability, windSpeed, windDirection
            )
            // sunrise/sunset are requested by the service and were being dropped on the floor,
            // which left Weather.isDaylight() falling back to a clock-only guess.
            val sun = Astro(
                parseDateTime(getVal(daily.sunrise, i)),
                parseDateTime(getVal(daily.sunset, i))
            )

            list.add(
                Daily(
                    date, date.time, day, night, sun, null, null, null,
                    pollenMap[times[i]],
                    UV(uvIndex, null, null), 0f
                )
            )
        }
        return list
    }

    private fun buildHalfDay(
        context: Context,
        phase: String,
        weatherCode: Int?,
        temperature: Int,
        precipitation: Float?,
        precipitationProbability: Float?,
        windSpeed: Float,
        windDirection: Int
    ) = HalfDay(
        getWeatherText(weatherCode),
        phase,
        convertWeatherCode(weatherCode),
        Temperature(temperature, null, null, null, null, null, null),
        Precipitation(precipitation, null, null, null, null),
        PrecipitationProbability(precipitationProbability, null, null, null, null),
        PrecipitationDuration(null, null, null, null, null),
        Wind(
            getWindDirection(windDirection),
            WindDegree(windDirection.toFloat(), false),
            windSpeed,
            CommonConverter.getWindLevel(context, windSpeed.toDouble())
        ),
        null
    )

    // ---- pollen (the air quality host's hourly grains/m³ columns) ----

    // Daily-max grains/m³ -> level 0..4. Thresholds from Atmo France, the same table
    // breezy-weather uses for its pollen index.
    private val ALDER_LIMITS = intArrayOf(0, 10, 60, 100, 500)
    private val BIRCH_LIMITS = intArrayOf(0, 10, 60, 100, 500)
    private val OLIVE_LIMITS = intArrayOf(0, 20, 100, 200, 500)
    private val GRASS_LIMITS = intArrayOf(0, 3, 30, 50, 250)
    private val RAGWEED_LIMITS = intArrayOf(0, 3, 30, 50, 250)

    /**
     * Folds the hourly pollen columns into one [Pollen] per day, keyed by the date part of the
     * hourly timestamp. Both endpoints are asked for the same timezone, so that key matches
     * daily.time exactly and no date parsing is involved.
     */
    private fun convertPollenMap(
        context: Context,
        airQualityResult: OpenMeteoAirQualityResult?
    ): Map<String, Pollen> {
        val hourly = airQualityResult?.hourly ?: return emptyMap()
        val times = hourly.time ?: return emptyMap()

        val alder = HashMap<String, Float?>()
        val birch = HashMap<String, Float?>()
        val olive = HashMap<String, Float?>()
        val grass = HashMap<String, Float?>()
        val ragweed = HashMap<String, Float?>()
        for (i in times.indices) {
            val day = times[i]?.substringBefore('T') ?: continue
            alder[day] = maxSoFar(alder[day], hourly.alderPollen, i)
            birch[day] = maxSoFar(birch[day], hourly.birchPollen, i)
            olive[day] = maxSoFar(olive[day], hourly.olivePollen, i)
            grass[day] = maxSoFar(grass[day], hourly.grassPollen, i)
            ragweed[day] = maxSoFar(ragweed[day], hourly.ragweedPollen, i)
        }

        val pollenMap = HashMap<String, Pollen>()
        for (day in alder.keys) {
            val tree = treeSlot(alder[day], birch[day], olive[day])
            val grassMax = grass[day]
            val grassLevel = grassMax?.let { pollenLevel(it, GRASS_LIMITS) }
            val ragweedMax = ragweed[day]
            val ragweedLevel = ragweedMax?.let { pollenLevel(it, RAGWEED_LIMITS) }
            pollenMap[day] = Pollen(
                // The model stores the index as an integer (Accu's concentrations are whole
                // numbers too), so fractional grains/m³ round at this boundary.
                grassMax?.roundToInt(), grassLevel, pollenDescription(context, grassLevel),
                null, null, null,
                ragweedMax?.roundToInt(), ragweedLevel, pollenDescription(context, ragweedLevel),
                tree?.first?.roundToInt(), tree?.second, pollenDescription(context, tree?.second)
            )
        }
        return pollenMap
    }

    /** The model has one tree slot, the API three species: the day is rated by whichever
     * species reaches the highest level, and that species' peak concentration goes with it. */
    private fun treeSlot(alder: Float?, birch: Float?, olive: Float?): Pair<Float, Int>? {
        val candidates = ArrayList<Pair<Float, Int>>()
        alder?.let { candidates.add(it to pollenLevel(it, ALDER_LIMITS)) }
        birch?.let { candidates.add(it to pollenLevel(it, BIRCH_LIMITS)) }
        olive?.let { candidates.add(it to pollenLevel(it, OLIVE_LIMITS)) }
        return candidates.maxByOrNull { it.second }
    }

    private fun pollenLevel(concentration: Float, limits: IntArray): Int =
        limits.indexOfLast { concentration >= it }.coerceAtLeast(0)

    private fun pollenDescription(context: Context, level: Int?): String? = when (level) {
        null -> null
        0 -> context.getString(R.string.pollen_level_none)
        1 -> context.getString(R.string.pollen_level_low)
        2 -> context.getString(R.string.pollen_level_moderate)
        3 -> context.getString(R.string.pollen_level_high)
        else -> context.getString(R.string.pollen_level_very_high)
    }

    private fun maxSoFar(current: Float?, values: List<Double>?, index: Int): Float? {
        val value = values?.getOrNull(index)?.toFloat() ?: return current
        return if (current == null || value > current) value else current
    }

    private fun convertHourlyList(context: Context, result: OpenMeteoResult): List<Hourly> {
        val hourly = result.hourly ?: return emptyList()
        val times = hourly.time ?: return emptyList()
        val list = ArrayList<Hourly>()

        for (i in times.indices) {
            val date = parseDateTime(times[i]) ?: continue
            val weatherCode = getVal(hourly.weatherCode, i)
            val temperature = getIntVal(hourly.temperature, i)
            val feelsLike = getNullableIntVal(hourly.apparentTemperature, i)
            val precipitation = getFloatVal(hourly.precipitation, i)
            val precipitationProbability = getFloatVal(hourly.precipitationProbability, i)
            val windSpeed = getFloatValOrZero(hourly.windSpeed, i)
            val windDirection = getIntVal(hourly.windDirection, i)
            val uvIndex = getNullableIntVal(hourly.uvIndex, i)
            val isDay = getVal(hourly.isDay, i) == 1

            list.add(
                Hourly(
                    date,
                    date.time,
                    isDay,
                    getWeatherText(weatherCode),
                    convertWeatherCode(weatherCode),
                    Temperature(temperature, feelsLike, null, null, null, null, null),
                    Precipitation(precipitation, null, null, null, null),
                    PrecipitationProbability(precipitationProbability, null, null, null, null),
                    Wind(
                        getWindDirection(windDirection),
                        WindDegree(windDirection.toFloat(), false),
                        windSpeed,
                        CommonConverter.getWindLevel(context, windSpeed.toDouble())
                    ),
                    UV(uvIndex, null, null)
                )
            )
        }
        return list
    }

    // Current/HalfDay declare weatherCode @NonNull, so a missing code cannot stay null the way the
    // Java version left it — it falls through to the same CLEAR default as any unmapped code.
    private fun convertWeatherCode(code: Int?): WeatherCode = when (code) {
        0 -> WeatherCode.CLEAR
        1, 2, 3 -> WeatherCode.CLOUDY
        45, 48 -> WeatherCode.FOG
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherCode.RAIN
        56, 57, 66, 67 -> WeatherCode.SLEET
        71, 73, 75, 77, 85, 86 -> WeatherCode.SNOW
        95 -> WeatherCode.THUNDER
        96, 99 -> WeatherCode.THUNDERSTORM
        else -> WeatherCode.CLEAR
    }

    private fun getWeatherText(code: Int?): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    private fun getWindDirection(degree: Int): String = when {
        degree < 23 || degree >= 338 -> "N"
        degree < 68 -> "NE"
        degree < 113 -> "E"
        degree < 158 -> "SE"
        degree < 203 -> "S"
        degree < 248 -> "SW"
        degree < 293 -> "W"
        else -> "NW"
    }

    private fun parseDate(value: String?): Date? = parse(value, "yyyy-MM-dd")

    private fun parseDateTime(value: String?): Date? = parse(value, "yyyy-MM-dd'T'HH:mm")

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

    private fun <T> getVal(list: List<T>?, index: Int): T? =
        if (list == null || index >= list.size) null else list[index]

    private fun getIntVal(list: List<Double>?, index: Int): Int =
        getVal(list, index)?.toInt() ?: 0

    private fun getNullableIntVal(list: List<Double>?, index: Int): Int? =
        getVal(list, index)?.toInt()

    private fun getFloatVal(list: List<Double>?, index: Int): Float? =
        getVal(list, index)?.toFloat()

    private fun getFloatValOrZero(list: List<Double>?, index: Int): Float =
        getVal(list, index)?.toFloat() ?: 0f
}
