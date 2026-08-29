package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
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
import wangdaye.com.geometricweather.weather.json.atmoaura.AtmoAuraQAResult
import wangdaye.com.geometricweather.weather.json.mf.MfCurrentResult
import wangdaye.com.geometricweather.weather.json.mf.MfEphemerisResult
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result
import wangdaye.com.geometricweather.weather.json.mf.MfLocationResult
import wangdaye.com.geometricweather.weather.json.mf.MfRainResult
import wangdaye.com.geometricweather.weather.json.mf.MfWarningsResult
import wangdaye.com.geometricweather.weather.services.WeatherService

/**
 * Météo France serves GeoJSON features (everything under "properties") with ISO-8601 timestamps.
 * Provider fields are frequently null — guard every one of them, since the Weather model's @NonNull
 * assertions throw and the outer catch would turn that into a silent "no data".
 *
 * Fields off the Mf* results are Java platform types: Kotlin will not force a null check on them,
 * so the explicit guards below carry their weight and must stay.
 */
object MfResultConverter {

    private const val ONE_HOUR = 3600 * 1000L

    /** Result of a coordinates search. */
    @JvmStatic
    fun convert(location: Location?, result: MfForecastV2Result): Location {
        val properties = result.properties
        val coordinates = result.geometry?.coordinates
        val longitude = coordinates?.getOrNull(0)
        val latitude = coordinates?.getOrNull(1)

        val address = keptAddress(location)

        return Location(
            properties.insee, // cityId
            latitude ?: location?.latitude ?: 0f,
            longitude ?: location?.longitude ?: 0f,
            TimeZone.getTimeZone(properties.timezone ?: "Europe/Paris"),
            properties.country ?: "",
            address?.province ?: properties.frenchDepartment ?: "",
            address?.city ?: properties.name ?: "",
            address?.district ?: "",
            null,
            WeatherSource.MF,
            false,
            false,
            isChinese(properties.country)
        )
    }

    /** Result of a query string search. */
    @JvmStatic
    fun convert(location: Location?, result: MfLocationResult): Location {
        val address = keptAddress(location)
        val postCodeSuffix = if (result.postCode == null) "" else " (${result.postCode})"

        return Location(
            result.postCode, // cityId
            result.lat.toFloat(),
            result.lon.toFloat(),
            // Météo France serves France only; MfLocationResult has no tz (the real one comes with
            // the forecast).
            TimeZone.getTimeZone("Europe/Paris"),
            result.country ?: "",
            address?.province ?: result.admin2 ?: "", // Domain (département)
            address?.city ?: result.name?.plus(postCodeSuffix) ?: "",
            address?.district ?: "",
            null,
            WeatherSource.MF,
            false,
            false,
            isChinese(result.country)
        )
    }

    /** The caller's own address fields, when it already carries a complete set worth keeping. */
    private fun keptAddress(location: Location?): Location? = location?.takeIf {
        it.province.isNotEmpty() && it.city.isNotEmpty() && it.district.isNotEmpty()
    }

    private fun isChinese(country: String?): Boolean {
        if (country.isNullOrEmpty()) {
            return false
        }
        val code = country.uppercase()
        return code.startsWith("CN") || code.startsWith("HK") || code.startsWith("TW")
    }

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        currentResult: MfCurrentResult?,
        forecastResult: MfForecastV2Result?,
        ephemerisResult: MfEphemerisResult?,
        rainResult: MfRainResult?,
        warningsResult: MfWarningsResult?,
        aqiAtmoAuraResult: AtmoAuraQAResult?
    ): WeatherService.WeatherResultWrapper {
        return try {
            val properties = forecastResult?.properties
                ?: return WeatherService.WeatherResultWrapper(null)
            val hourlyForecast = properties.forecast ?: emptyList()
            val dailyForecast = properties.dailyForecast ?: emptyList()

            val hourly = getHourlyList(context, hourlyForecast, properties.probabilityForecast)
            val daily = getDailyList(
                context, dailyForecast, hourlyForecast, hourly, ephemerisResult, aqiAtmoAuraResult
            )
            if (daily.isEmpty()) {
                // Every consumer assumes at least one day; an empty list crashes the UI downstream.
                return WeatherService.WeatherResultWrapper(null)
            }

            val updateTime = forecastResult.updateTime?.time ?: System.currentTimeMillis()

            // v2/observation only carries T / wind / weather text — no humidity, pressure or
            // precipitation. Backfill those from the hour nearest the observation.
            val observation = currentResult?.properties?.gridded
            val observedAt = observation?.time?.time ?: System.currentTimeMillis()

            val weather = Weather(
                Base(
                    location.cityId,
                    System.currentTimeMillis(),
                    Date(updateTime),
                    updateTime,
                    Date(),
                    System.currentTimeMillis()
                ),
                getCurrent(
                    context, observation,
                    getNearestHour(hourlyForecast, observedAt), aqiAtmoAuraResult
                ),
                null, // TODO: Fill in with observation data instead
                daily,
                hourly,
                getMinutelyList(daily[0].sun(), rainResult),
                getWarningsList(warningsResult)
            )
            WeatherService.WeatherResultWrapper(weather)
        } catch (ignored: Exception) {
            WeatherService.WeatherResultWrapper(null)
        }
    }

    /** The hourly step closest to [time]; MF's first step is the current hour. */
    private fun getNearestHour(
        hourlyForecast: List<MfForecastV2Result.ForecastProperties.HourForecast>,
        time: Long
    ): MfForecastV2Result.ForecastProperties.HourForecast? {
        var nearest: MfForecastV2Result.ForecastProperties.HourForecast? = null
        var nearestDelta = Long.MAX_VALUE
        for (hour in hourlyForecast) {
            val hourTime = hour.time ?: continue
            val delta = Math.abs(hourTime.time - time)
            if (delta < nearestDelta) {
                nearestDelta = delta
                nearest = hour
            }
        }
        return nearest
    }

    private fun getCurrent(
        context: Context,
        observation: MfCurrentResult.Properties.Observation?,
        nearestHour: MfForecastV2Result.ForecastProperties.HourForecast?,
        aqiAtmoAuraResult: AtmoAuraQAResult?
    ): Current {
        val windSpeed = observation?.windSpeed?.let { it * 3.6f }

        // Temperature and weather text come from the observation, falling back to the nearest hour
        // so a failed observation shows a real forecast value rather than 0°.
        val temperature = observation?.temperature ?: nearestHour?.t
        val weatherText = observation?.weatherDescription
            ?: nearestHour?.weatherDescription
            ?: ""
        val weatherIcon = observation?.weatherIcon ?: nearestHour?.weatherIcon

        return Current(
            weatherText,
            getWeatherCode(weatherIcon),
            Temperature(
                toInt(temperature),
                // MF's T_windchill is the "température ressentie" it shows to users.
                toIntOrNull(nearestHour?.tWindchill),
                null, null,
                toIntOrNull(nearestHour?.tWindchill),
                null, null
            ),
            if (nearestHour != null) {
                getHourlyPrecipitation(nearestHour)
            } else {
                Precipitation(null, null, null, null, null)
            },
            PrecipitationProbability(null, null, null, null, null),
            Wind(
                observation?.windIcon ?: "",
                getWindDegree(observation?.windDirection),
                windSpeed,
                if (windSpeed != null) {
                    CommonConverter.getWindLevel(context, windSpeed.toDouble())
                } else {
                    ""
                }
            ),
            UV(null, null, null),
            getAirQuality(Date(), aqiAtmoAuraResult),
            nearestHour?.relativeHumidity?.toFloat(),
            nearestHour?.pSea,
            null, null,
            nearestHour?.totalCloudCover,
            null, null, null
        )
    }

    /** MF reports -1 for a variable wind direction. */
    private fun getWindDegree(direction: Int?): WindDegree {
        if (direction == null || direction == -1) {
            return WindDegree(0f, true)
        }
        return WindDegree(direction.toFloat(), false)
    }

    private fun emptyAirQuality() = AirQuality(null, null, null, null, null, null, null, null)

    // This can be improved by adding Aqi results from other regions
    private fun getAirQuality(requestedDate: Date, aqiAtmoAuraResult: AtmoAuraQAResult?): AirQuality {
        val indexs = aqiAtmoAuraResult?.indexs ?: return emptyAirQuality()

        val fmt = SimpleDateFormat("yyyyMMdd")
        return when {
            matchesDay(fmt, requestedDate, indexs.yesterday) -> getAirQuality(indexs.yesterday)
            matchesDay(fmt, requestedDate, indexs.today) -> getAirQuality(indexs.today)
            matchesDay(fmt, requestedDate, indexs.tomorrow) -> getAirQuality(indexs.tomorrow)
            matchesDay(fmt, requestedDate, indexs.inTwoDays) -> getAirQuality(indexs.inTwoDays)
            else -> emptyAirQuality()
        }
    }

    private fun matchesDay(
        fmt: SimpleDateFormat,
        requestedDate: Date,
        index: AtmoAuraQAResult.MultiDaysIndexs.MultiIndex?
    ): Boolean = index?.date != null && fmt.format(requestedDate) == fmt.format(index.date)

    private fun getAirQuality(index: AtmoAuraQAResult.MultiDaysIndexs.MultiIndex): AirQuality =
        AirQuality(
            index.aggregatedIndex?.quali,
            index.aggregatedIndex?.let { Math.round(it.`val`).toInt() },
            null,
            index.pm10?.`val`?.toFloat(),
            null,
            index.no2?.`val`?.toFloat(),
            index.o3?.`val`?.toFloat(),
            null
        )

    private fun getHalfDay(
        context: Context,
        isDaytime: Boolean,
        hourly: List<Hourly>,
        hourlyForecast: List<MfForecastV2Result.ForecastProperties.HourForecast>,
        dailyForecast: MfForecastV2Result.ForecastProperties.ForecastV2
    ): HalfDay {
        var temp: Int? = if (isDaytime) toIntOrNull(dailyForecast.tMax) else toIntOrNull(dailyForecast.tMin)
        var tempWindChill: Int? = null

        var precipitationTotal = 0f
        var precipitationRain = 0f
        var precipitationSnow = 0f

        var probPrecipitationTotal = 0f
        var probPrecipitationRain = 0f
        var probPrecipitationSnow = 0f
        var probPrecipitationIce = 0f

        val dayStart = dailyForecast.time.time

        for (hour in hourly) {
            if (!isInHalfDay(hour.time, dayStart, isDaytime)) {
                continue
            }
            // Temperature
            val hourTemp = hour.temperature.temperature
            val windChill = hour.temperature.windChillTemperature
            val currentTemp = temp
            val currentWindChill = tempWindChill
            if (isDaytime) {
                if (currentTemp == null || hourTemp > currentTemp) {
                    temp = hourTemp
                }
                if (windChill != null && (currentWindChill == null || windChill > currentWindChill)) {
                    tempWindChill = windChill
                }
            } else {
                if (currentTemp == null || hourTemp < currentTemp) {
                    temp = hourTemp
                }
                if (windChill != null && (currentWindChill == null || windChill < currentWindChill)) {
                    tempWindChill = windChill
                }
            }

            // Precipitation
            precipitationTotal += orZero(hour.precipitation.total)
            precipitationRain += orZero(hour.precipitation.rain)
            precipitationSnow += orZero(hour.precipitation.snow)

            // Precipitation probability
            probPrecipitationTotal = max(probPrecipitationTotal, hour.precipitationProbability.total)
            probPrecipitationRain = max(probPrecipitationRain, hour.precipitationProbability.rain)
            probPrecipitationSnow = max(probPrecipitationSnow, hour.precipitationProbability.snow)
            probPrecipitationIce = max(probPrecipitationIce, hour.precipitationProbability.ice)
        }

        var cloudCover: Int? = null
        var windDirection = "Pas d’info"
        var windDegree = WindDegree(0f, false)
        var windSpeed: Float? = null
        var windLevel = "Pas d’info"

        for (hourForecast in hourlyForecast) {
            val time = hourForecast.time ?: continue
            if (!isInHalfDay(time.time, dayStart, isDaytime)) {
                continue
            }
            val cover = hourForecast.totalCloudCover
            val currentCover = cloudCover
            if (cover != null && (currentCover == null || cover > currentCover)) {
                cloudCover = cover
            }
            val speed = hourForecast.windSpeed
            if (speed != null) {
                val kph = speed * 3.6f
                val currentSpeed = windSpeed
                if (currentSpeed == null || kph > currentSpeed) {
                    windDirection = hourForecast.windIcon ?: windDirection
                    windDegree = getWindDegree(hourForecast.windDirection)
                    windSpeed = kph
                    windLevel = CommonConverter.getWindLevel(context, kph.toDouble())
                }
            }
        }

        return HalfDay(
            dailyForecast.dailyWeatherDescription ?: "",
            dailyForecast.dailyWeatherDescription ?: "",
            getWeatherCode(dailyForecast.dailyWeatherIcon),
            Temperature(temp ?: 0, null, null, null, tempWindChill, null, null),
            Precipitation(precipitationTotal, null, precipitationRain, precipitationSnow, null),
            PrecipitationProbability(
                probPrecipitationTotal, null, probPrecipitationRain,
                probPrecipitationSnow, probPrecipitationIce
            ),
            PrecipitationDuration(null, null, null, null, null),
            Wind(windDirection, windDegree, windSpeed, windLevel),
            cloudCover
        )
    }

    /** Day runs 06:00–18:00 after the day's midnight; night runs 18:00–06:00 the following day. */
    private fun isInHalfDay(time: Long, dayStart: Long, isDaytime: Boolean): Boolean =
        if (isDaytime) {
            time >= dayStart + 6 * ONE_HOUR && time < dayStart + 18 * ONE_HOUR
        } else {
            time >= dayStart + 18 * ONE_HOUR && time < dayStart + 30 * ONE_HOUR
        }

    private fun orZero(value: Float?): Float = value ?: 0f

    private fun max(current: Float, candidate: Float?): Float =
        if (candidate != null && candidate > current) candidate else current

    private fun getDailyList(
        context: Context,
        dailyForecasts: List<MfForecastV2Result.ForecastProperties.ForecastV2>,
        hourlyForecast: List<MfForecastV2Result.ForecastProperties.HourForecast>,
        hourly: List<Hourly>,
        ephemerisResult: MfEphemerisResult?,
        aqiAtmoAuraResult: AtmoAuraQAResult?
    ): List<Daily> {
        val ephemeris = ephemerisResult?.properties?.ephemeris

        val dailyList = ArrayList<Daily>(dailyForecasts.size)
        for (dailyForecast in dailyForecasts) {
            // Skip days without a date or temperature: both are non-nullable downstream, and MF
            // pads the tail of the range with entries that carry neither.
            if (dailyForecast.time == null
                || dailyForecast.tMin == null || dailyForecast.tMax == null) {
                continue
            }
            dailyList.add(
                Daily(
                    dailyForecast.time,
                    dailyForecast.time.time,
                    getHalfDay(context, true, hourly, hourlyForecast, dailyForecast),
                    getHalfDay(context, false, hourly, hourlyForecast, dailyForecast),
                    Astro(dailyForecast.sunriseTime, dailyForecast.sunsetTime),
                    // Note: the same moon data for all days, but since we only show the current
                    // day's in the app, this does not matter.
                    Astro(ephemeris?.moonriseTime, ephemeris?.moonsetTime),
                    MoonPhase(
                        ephemeris?.let {
                            CommonConverter.getMoonPhaseAngle(it.moonPhaseDescription)
                        },
                        ephemeris?.moonPhaseDescription
                    ),
                    getAirQuality(dailyForecast.time, aqiAtmoAuraResult),
                    null,
                    UV(dailyForecast.uvIndex, null, null),
                    getHoursOfDay(dailyForecast.sunriseTime, dailyForecast.sunsetTime)
                )
            )
        }
        return dailyList
    }

    /** MF publishes cumulative rain/snow over several windows; take the shortest one available. */
    private fun getShortestCumul(
        cumul1H: Float?,
        cumul3H: Float?,
        cumul6H: Float?,
        cumul12H: Float?,
        cumul24H: Float?
    ): Float? = cumul1H ?: cumul3H ?: cumul6H ?: cumul12H ?: cumul24H

    private fun getHourlyPrecipitation(
        hourlyForecast: MfForecastV2Result.ForecastProperties.HourForecast
    ): Precipitation {
        val rainCumul = getShortestCumul(
            hourlyForecast.rain1h, hourlyForecast.rain3h, hourlyForecast.rain6h,
            hourlyForecast.rain12h, hourlyForecast.rain24h
        )
        val snowCumul = getShortestCumul(
            hourlyForecast.snow1h, hourlyForecast.snow3h, hourlyForecast.snow6h,
            hourlyForecast.snow12h, hourlyForecast.snow24h
        )
        val totalCumul = when {
            rainCumul == null -> snowCumul
            snowCumul == null -> rainCumul
            else -> snowCumul + rainCumul
        }

        return Precipitation(totalCumul, null, rainCumul, snowCumul, null)
    }

    private fun getHourlyPrecipitationProbability(
        probabilityForecastResult:
            List<MfForecastV2Result.ForecastProperties.ProbabilityForecastV2>?,
        time: Long
    ): PrecipitationProbability {
        if (probabilityForecastResult == null) {
            return PrecipitationProbability(null, null, null, null, null)
        }

        var rainProbability: Float? = null
        var snowProbability: Float? = null
        var iceProbability: Float? = null

        for (probabilityForecast in probabilityForecastResult) {
            val start = probabilityForecast.time?.time ?: continue

            /*
             * Probablity are given every 3 hours, sometimes every 6 hours.
             * Sometimes every 3 hour-schedule give 3 hours probability AND 6 hours probability,
             * sometimes only one of them
             * It's not very clear but we take all hours in order.
             */
            if (time >= start && time < start + 3 * ONE_HOUR) {
                if (probabilityForecast.rainHazard3h != null) {
                    rainProbability = probabilityForecast.rainHazard3h.toFloat()
                } else if (probabilityForecast.rainHazard6h != null) {
                    rainProbability = probabilityForecast.rainHazard6h.toFloat()
                }
                if (probabilityForecast.snowHazard3h != null) {
                    snowProbability = probabilityForecast.snowHazard3h.toFloat()
                } else if (probabilityForecast.snowHazard6h != null) {
                    snowProbability = probabilityForecast.snowHazard6h.toFloat()
                }
                if (probabilityForecast.freezingHazard != null) {
                    iceProbability = probabilityForecast.freezingHazard.toFloat()
                }
            }

            /*
             * If it's found as part of the "6 hour schedule" and we find later a "3 hour schedule"
             * the "3 hour schedule" will overwrite the "6 hour schedule" below with the above
             */
            if (time >= start + 3 * ONE_HOUR && time < start + 6 * ONE_HOUR) {
                if (probabilityForecast.rainHazard6h != null) {
                    rainProbability = probabilityForecast.rainHazard6h.toFloat()
                }
                if (probabilityForecast.snowHazard6h != null) {
                    snowProbability = probabilityForecast.snowHazard6h.toFloat()
                }
                if (probabilityForecast.freezingHazard != null) {
                    iceProbability = probabilityForecast.freezingHazard.toFloat()
                }
            }
        }

        return PrecipitationProbability(
            maxOf(rainProbability ?: 0f, snowProbability ?: 0f, iceProbability ?: 0f),
            null,
            rainProbability,
            snowProbability,
            iceProbability
        )
    }

    private fun getHourlyList(
        context: Context,
        hourlyForecastResult: List<MfForecastV2Result.ForecastProperties.HourForecast>,
        probabilityForecastResult:
            List<MfForecastV2Result.ForecastProperties.ProbabilityForecastV2>?
    ): List<Hourly> {
        val hourlyList = ArrayList<Hourly>(hourlyForecastResult.size)
        for (hourlyForecast in hourlyForecastResult) {
            // Time and temperature are non-nullable downstream; MF leaves both out on padded entries.
            if (hourlyForecast.time == null || hourlyForecast.t == null) {
                continue
            }
            val windSpeed = hourlyForecast.windSpeed?.let { it * 3.6f }
            hourlyList.add(
                Hourly(
                    hourlyForecast.time,
                    hourlyForecast.time.time,
                    // TODO: Probably not the best way to check if it is daytime or nighttime
                    // Use CommonConverter.isDaylight(sunrise, sunset, hourlyForecast.time) instead
                    hourlyForecast.weatherIcon == null || !hourlyForecast.weatherIcon.endsWith("n"),
                    hourlyForecast.weatherDescription ?: "",
                    getWeatherCode(hourlyForecast.weatherIcon),
                    Temperature(
                        toInt(hourlyForecast.t),
                        // Same value in both slots: MF's T_windchill is what it labels
                        // "ressentie", and HourlyWeatherDialog reads realFeel only.
                        toIntOrNull(hourlyForecast.tWindchill),
                        null,
                        null,
                        toIntOrNull(hourlyForecast.tWindchill),
                        null,
                        null
                    ),
                    getHourlyPrecipitation(hourlyForecast),
                    getHourlyPrecipitationProbability(
                        probabilityForecastResult, hourlyForecast.time.time
                    ),
                    Wind(
                        hourlyForecast.windIcon ?: "",
                        getWindDegree(hourlyForecast.windDirection),
                        windSpeed,
                        if (windSpeed != null) {
                            CommonConverter.getWindLevel(context, windSpeed.toDouble())
                        } else {
                            ""
                        }
                    ),
                    UV(null, null, null)
                )
            )
        }
        return hourlyList
    }

    private fun getMinutelyList(sun: Astro, rainResult: MfRainResult?): List<Minutely> {
        val rainForecasts = rainResult?.properties?.rainForecasts
        if (rainForecasts.isNullOrEmpty()) {
            return ArrayList()
        }

        val minutelyList = ArrayList<Minutely>(rainForecasts.size)
        var minuteZero: Long? = null
        for (rainForecast in rainForecasts) {
            val time = rainForecast.time ?: continue
            val minute = time.time / 60000
            if (minuteZero == null) {
                minuteZero = minute
            }
            minutelyList.add(
                Minutely(
                    time,
                    time.time,
                    sun.riseDate != null && sun.setDate != null
                            && CommonConverter.isDaylight(sun.riseDate, sun.setDate, time),
                    rainForecast.rainIntensityDescription ?: "",
                    // 1 means "no rain"; anything above that is an actual rain step.
                    if (rainForecast.rainIntensity != null && rainForecast.rainIntensity > 1) {
                        WeatherCode.RAIN
                    } else {
                        getWeatherCode(null)
                    },
                    (minute - minuteZero).toInt(),
                    null,
                    null,
                    // rain_intensity is mm/h with 1 = "no rain"; only rain steps carry a column.
                    rainForecast.rainIntensity?.toFloat()?.div(60f)
                )
            )
        }
        return minutelyList
    }

    private fun getWarningsList(warningsResult: MfWarningsResult?): List<Alert> {
        val items = warningsResult?.phenomenonsItems ?: return ArrayList()

        val alertList = ArrayList<Alert>(items.size)
        for (phemononItem in items) {
            // Do not warn when there is nothing to warn (green alert)
            if (phemononItem.phenomenoMaxColorId > 1) {
                alertList.add(
                    Alert(
                        phemononItem.phenomenonId.toLong(),
                        // FIXME: Do not take updateTime but phenomonon time instead
                        Date(warningsResult.updateTime * 1000),
                        warningsResult.updateTime * 1000,
                        getWarningType(phemononItem.phenomenonId) + " — " +
                                getWarningText(phemononItem.phenomenoMaxColorId),
                        // TODO: Longer description (there is a report in the web service when the
                        // alert is orange or red)
                        "",
                        getWarningType(phemononItem.phenomenonId),
                        phemononItem.phenomenoMaxColorId, // TODO: Check Priority
                        getWarningColor(phemononItem.phenomenoMaxColorId)
                    )
                )
            }
        }
        Alert.deduplication(alertList)
        return alertList
    }

    private fun toInt(value: Float?): Int = if (value != null) (value + 0.5).toInt() else 0

    private fun toIntOrNull(value: Float?): Int? = if (value != null) (value + 0.5).toInt() else null

    private fun getWarningType(phemononId: Int): String = when (phemononId) {
        1 -> "Vent"
        2 -> "Pluie-Inondation"
        3 -> "Orages"
        4 -> "Crues"
        5 -> "Neige-Verglas"
        6 -> "Canicule"
        7 -> "Grand Froid"
        8 -> "Avalanches"
        9 -> "Vagues-Submersion"
        else -> "Divers"
    }

    private fun getWarningText(colorId: Int): String = when (colorId) {
        4 -> "Vigilance absolue"
        3 -> "Soyez très vigilant"
        2 -> "Soyez attentif"
        else -> "Pas de vigilance particulière"
    }

    private fun getWarningColor(colorId: Int): Int = when (colorId) {
        4 -> Color.rgb(204, 0, 0)
        3 -> Color.rgb(255, 184, 43)
        2 -> Color.rgb(255, 246, 0)
        else -> Color.rgb(49, 170, 53)
    }

    private fun getWeatherCode(icon: String?): WeatherCode {
        if (icon == null) {
            return WeatherCode.CLEAR
        }

        // Note: Météo France doesn't have icons for WIND
        return when {
            icon in CLEAR_ICONS -> WeatherCode.CLEAR
            icon in PARTLY_CLOUDY_ICONS -> WeatherCode.PARTLY_CLOUDY
            icon in CLOUDY_ICONS -> WeatherCode.CLOUDY
            icon in HAZE_ICONS -> WeatherCode.HAZE
            icon in FOG_ICONS -> WeatherCode.FOG
            // We can start using "startsWith" when there are 2 digits
            icon in RAIN_ICONS || icon.startsWith("p10") || icon.startsWith("p11")
                    || icon.startsWith("p12") || icon.startsWith("p13")
                    || icon.startsWith("p14") -> WeatherCode.RAIN
            icon.startsWith("p16") || icon.startsWith("p24")
                    || icon.startsWith("p25") -> WeatherCode.THUNDERSTORM
            icon.startsWith("p17") || icon.startsWith("p18") -> WeatherCode.SLEET
            icon.startsWith("p19") || icon.startsWith("p20") -> WeatherCode.HAIL
            icon.startsWith("p21") || icon.startsWith("p22")
                    || icon.startsWith("p23") -> WeatherCode.SNOW
            icon.startsWith("p26") || icon.startsWith("p27") || icon.startsWith("p28")
                    || icon.startsWith("p29") -> WeatherCode.THUNDER
            else -> WeatherCode.CLEAR
        }
    }

    private val CLEAR_ICONS = setOf("p1", "p1j", "p1n", "p1bis", "p1bisj", "p1bisn")
    private val PARTLY_CLOUDY_ICONS = setOf("p2", "p2j", "p2n", "p2bis", "p2bisj", "p2bisn")
    private val CLOUDY_ICONS = setOf("p3", "p3j", "p3n", "p3bis", "p3bisj", "p3bisn")
    private val HAZE_ICONS = setOf(
        "p4", "p4j", "p4n",
        "p5", "p5j", "p5n", "p5bis", "p5bisj", "p5bisn"
    )
    private val FOG_ICONS = setOf(
        "p6", "p6j", "p6n", "p6bis", "p6bisj", "p6bisn", "p6ter", "p6terj", "p6tern",
        "p7", "p7j", "p7n", "p7bis", "p7bisj", "p7bisn",
        "p8", "p8j", "p8n", "p8bis", "p8bisj", "p8bisn"
    )
    private val RAIN_ICONS = setOf("p9", "p9j", "p9n")

    private fun getHoursOfDay(sunrise: Date?, sunset: Date?): Float {
        if (sunrise == null || sunset == null) {
            return 0f
        }
        return (
            (sunset.time - sunrise.time) // get delta millisecond.
                / 1000 // second.
                / 60 // minutes.
                / 60.0 // hours.
            ).toFloat()
    }
}
