package wangdaye.com.geometricweather.weather.converters

import android.content.Context
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
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
import wangdaye.com.geometricweather.weather.json.metno.MetNoAirQualityResult
import wangdaye.com.geometricweather.weather.json.metno.MetNoAlertResult
import wangdaye.com.geometricweather.weather.json.metno.MetNoForecastResult

/**
 * MET Norway converter. Two absences shape the whole file:
 *
 * 1. **No daily block.** locationforecast answers one flat timeseries — hourly for ~2.5 days, then
 *    6-hourly — so the daily list is folded out of it by local calendar day.
 * 2. **No sunrise/sunset, anywhere in the API.** So `Daily.sun()` stays null, which
 *    `Weather.isDaylight()` already degrades to `DisplayUtils.isDaylight(timeZone)` (Weather.java:146)
 *    instead of crashing. The cost is an empty 日出 column and a coarser day/night background.
 *    Per-hour daylight is read off `symbol_code`'s `_day`/`_night` suffix instead — the API's own
 *    answer, and better than a clock.
 */
object MetNoResultConverter {

    private const val DAY_START = 6
    private const val NIGHT_START = 18

    @JvmStatic
    fun convert(
        context: Context,
        location: Location,
        forecast: MetNoForecastResult?,
        airQuality: MetNoAirQualityResult?,
        alerts: MetNoAlertResult?,
        nowcast: MetNoForecastResult?
    ): Weather? {
        val series = pointsOf(forecast)
        if (series.isEmpty()) {
            return null
        }
        return try {
            val now = System.currentTimeMillis()
            Weather(
                Base(location.cityId, now, Date(), now, Date(), now),
                convertCurrent(context, series[0], airQuality),
                null,
                convertDailyList(context, series, location.timeZone),
                convertHourlyList(context, series, location.timeZone),
                convertMinutelyList(pointsOf(nowcast), location.timeZone),
                convertAlertList(alerts)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** One usable timeseries entry: a parsed instant plus the forecast blocks hanging off it. */
    private class Point(val date: Date, private val data: MetNoForecastResult.Data) {

        val instant: MetNoForecastResult.Details? = data.instant?.details

        /**
         * The block matching this point's own step. `next_1_hours` is present exactly in the hourly
         * stretch and `next_6_hours` in the 6-hourly tail, so preferring the shorter one keeps the
         * per-point periods non-overlapping — which is what makes summing them honest.
         */
        val period: MetNoForecastResult.Details? =
            data.next1Hours?.details ?: data.next6Hours?.details

        /** Null on the very last point, which carries instant details and no block at all. */
        val symbol: String? =
            (data.next1Hours ?: data.next6Hours ?: data.next12Hours)?.summary?.symbolCode

        /** The 6-hourly tail states its own min/max, which beats sampling one instant. */
        val minTemperature: Double?
            get() = listOfNotNull(
                instant?.airTemperature, data.next6Hours?.details?.airTemperatureMin
            ).minOrNull()

        val maxTemperature: Double?
            get() = listOfNotNull(
                instant?.airTemperature, data.next6Hours?.details?.airTemperatureMax
            ).maxOrNull()
    }

    private fun pointsOf(result: MetNoForecastResult?): List<Point> {
        val timeseries = result?.properties?.timeseries ?: return emptyList()
        val points = ArrayList<Point>(timeseries.size)
        for (entry in timeseries) {
            val date = parseIso(entry.time) ?: continue
            val data = entry.data ?: continue
            points.add(Point(date, data))
        }
        return points
    }

    /** MET Norway has no "current" endpoint; the first timeseries point is the current hour. */
    private fun convertCurrent(
        context: Context,
        point: Point,
        airQuality: MetNoAirQualityResult?
    ): Current {
        val instant = point.instant
        return Current(
            weatherTextOf(point.symbol),
            weatherCodeOf(point.symbol),
            // No apparent temperature is reported, so realFeel stays null.
            Temperature(
                instant?.airTemperature?.roundToInt() ?: 0,
                null, null, null, null, null, null
            ),
            Precipitation(point.period?.precipitationAmount?.toFloat(), null, null, null, null),
            PrecipitationProbability(
                point.period?.probabilityOfPrecipitation?.toFloat(),
                point.period?.probabilityOfThunder?.toFloat(),
                null, null, null
            ),
            windOf(context, instant?.windFromDirection, instant?.windSpeed),
            UV(instant?.ultravioletIndexClearSky?.roundToInt(), null, null),
            convertAirQuality(context, airQuality, point.date),
            instant?.relativeHumidity?.toFloat(),
            instant?.airPressureAtSeaLevel?.toFloat(),
            null,
            instant?.dewPointTemperature?.roundToInt(),
            instant?.cloudAreaFraction?.roundToInt(),
            null,
            null,
            null
        )
    }

    /**
     * Norway-only endpoint, hourly. Its own `AQI` field is deliberately ignored (see
     * [MetNoAirQualityResult]): `aqiIndex` here is the 0-500 Chinese scale, so the index is derived
     * from the concentrations exactly as every other source derives it. No SO2 or CO is reported.
     */
    private fun convertAirQuality(
        context: Context,
        result: MetNoAirQualityResult?,
        now: Date
    ): AirQuality {
        val variables = result?.data?.time
            ?.mapNotNull { hour -> parseIso(hour.from)?.let { it to hour } }
            ?.minByOrNull { abs(it.first.time - now.time) }
            ?.second?.variables
            ?: return AirQuality(null, null, null, null, null, null, null, null)

        val pm25 = variables.pm25?.value?.toFloat()
        val pm10 = variables.pm10?.value?.toFloat()
        val index = CommonConverter.getAqiIndexFromConcentration(pm25, pm10)
        return AirQuality(
            CommonConverter.getAqiQuality(context, index),
            index,
            pm25,
            pm10,
            null,
            variables.no2?.value?.toFloat(),
            variables.o3?.value?.toFloat(),
            null
        )
    }

    private class DayBucket(val date: Date) {
        val day = ArrayList<Point>()
        val night = ArrayList<Point>()
    }

    /**
     * Folds the flat timeseries into local calendar days: 06:00-18:00 is the day half, the rest of
     * the same date is the night half.
     *
     * Deliberately *not* yr.no's own 18:00 -> 06:00-next-morning night. That convention files a
     * refresh made between midnight and 06:00 under the previous date, which would put yesterday at
     * `dailyForecast[0]` — and 76 sites in this app read index 0 as "today" (the same trap as
     * `OpenMeteoWeatherService.PAST_DAYS`). Same-date bucketing cannot shift the index.
     */
    private fun convertDailyList(
        context: Context,
        series: List<Point>,
        zone: TimeZone
    ): List<Daily> {
        val calendar = Calendar.getInstance(zone)
        val buckets = LinkedHashMap<Long, DayBucket>()

        for (point in series) {
            calendar.time = point.date
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val bucket = buckets.getOrPut(calendar.timeInMillis) { DayBucket(calendar.time) }
            if (hour in DAY_START until NIGHT_START) {
                bucket.day.add(point)
            } else {
                bucket.night.add(point)
            }
        }

        val list = ArrayList<Daily>(buckets.size)
        for (bucket in buckets.values) {
            // Refreshing after 18:00 leaves today with no 06:00-18:00 points, and the last day of
            // the forecast can be night-only. Falling back to the other half keeps both HalfDays
            // non-null — every consumer dereferences day()/night() unguarded — rather than dropping
            // the day and shifting the index.
            val dayPoints = bucket.day.ifEmpty { bucket.night }
            val nightPoints = bucket.night.ifEmpty { bucket.day }
            val uvIndex = (bucket.day + bucket.night)
                .mapNotNull { it.instant?.ultravioletIndexClearSky }
                .maxOrNull()
                ?.roundToInt()

            list.add(
                Daily(
                    bucket.date, bucket.date.time,
                    halfDayOf(context, dayPoints, "Day", true),
                    halfDayOf(context, nightPoints, "Night", false),
                    // sun/moon/moonPhase/airQuality/pollen: not reported. Daily coerces the nulls.
                    null, null, null, null, null,
                    UV(uvIndex, null, null),
                    0f
                )
            )
        }
        return list
    }

    private fun halfDayOf(
        context: Context,
        points: List<Point>,
        phase: String,
        day: Boolean
    ): HalfDay {
        // The half's headline is its worst weather, not its midpoint: a rainy afternoon must not
        // read as sunny because 12:00 happened to be clear.
        val worst = points.maxByOrNull { severityOf(weatherCodeOf(it.symbol)) }
        val temperature = if (day) {
            points.mapNotNull { it.maxTemperature }.maxOrNull()
        } else {
            points.mapNotNull { it.minTemperature }.minOrNull()
        }
        val amounts = points.mapNotNull { it.period?.precipitationAmount }
        val windiest = points.maxByOrNull { it.instant?.windSpeed ?: -1.0 }

        return HalfDay(
            weatherTextOf(worst?.symbol),
            phase,
            weatherCodeOf(worst?.symbol),
            Temperature(temperature?.roundToInt() ?: 0, null, null, null, null, null, null),
            // Null rather than 0 when nothing reported a period, so "no data" stays distinguishable
            // from "no rain".
            Precipitation(
                amounts.takeIf { it.isNotEmpty() }?.sum()?.toFloat(),
                null, null, null, null
            ),
            PrecipitationProbability(
                points.mapNotNull { it.period?.probabilityOfPrecipitation }.maxOrNull()?.toFloat(),
                points.mapNotNull { it.period?.probabilityOfThunder }.maxOrNull()?.toFloat(),
                null, null, null
            ),
            PrecipitationDuration(null, null, null, null, null),
            windOf(context, windiest?.instant?.windFromDirection, windiest?.instant?.windSpeed),
            worst?.instant?.cloudAreaFraction?.roundToInt()
        )
    }

    /** Ranking for "worst weather in the period"; only the relative order matters. */
    private fun severityOf(code: WeatherCode): Int = when (code) {
        WeatherCode.THUNDERSTORM -> 8
        WeatherCode.THUNDER -> 7
        WeatherCode.HAIL -> 6
        WeatherCode.SLEET -> 5
        WeatherCode.SNOW -> 4
        WeatherCode.RAIN -> 3
        WeatherCode.FOG -> 2
        WeatherCode.CLOUDY -> 1
        else -> 0
    }

    /**
     * One entry per timeseries point. The tail is 6-hourly rather than hourly, so this list is ~90
     * points spanning ~10 days, not 240 hours — the app plots whatever it is given.
     */
    private fun convertHourlyList(
        context: Context,
        series: List<Point>,
        zone: TimeZone
    ): List<Hourly> {
        val list = ArrayList<Hourly>(series.size)
        for (point in series) {
            val instant = point.instant ?: continue
            list.add(
                Hourly(
                    point.date,
                    point.date.time,
                    isDaylight(point.symbol, point.date, zone),
                    weatherTextOf(point.symbol),
                    weatherCodeOf(point.symbol),
                    Temperature(
                        instant.airTemperature?.roundToInt() ?: 0,
                        null, null, null, null, null, null
                    ),
                    Precipitation(
                        point.period?.precipitationAmount?.toFloat(), null, null, null, null
                    ),
                    PrecipitationProbability(
                        point.period?.probabilityOfPrecipitation?.toFloat(),
                        point.period?.probabilityOfThunder?.toFloat(),
                        null, null, null
                    ),
                    windOf(context, instant.windFromDirection, instant.windSpeed),
                    UV(instant.ultravioletIndexClearSky?.roundToInt(), null, null)
                )
            )
        }
        return list
    }

    /**
     * The API answers this itself: `symbol_code` is suffixed `_day` / `_night` / `_polartwilight`.
     * Bases with no day/night variant (cloudy, fog, plain rain) carry no suffix, and neither does
     * polar twilight, so those fall back to the clock — there is no sunrise/sunset to compare to.
     */
    private fun isDaylight(symbol: String?, date: Date, zone: TimeZone): Boolean {
        if (symbol != null) {
            if (symbol.endsWith("_night")) {
                return false
            }
            if (symbol.endsWith("_day")) {
                return true
            }
        }
        val calendar = Calendar.getInstance(zone)
        calendar.time = date
        return calendar.get(Calendar.HOUR_OF_DAY) in DAY_START until NIGHT_START
    }

    /**
     * Nowcast: radar, 5-minute steps, ~2 hours out, Nordics only — everywhere else the call fails
     * and this gets an empty list. `minuteInterval` is minutes since the first step, matching what
     * Accu and MF store. `dbz` stays null: nothing reads it, so converting mm/h to reflectivity
     * would be dead arithmetic.
     */
    private fun convertMinutelyList(series: List<Point>, zone: TimeZone): List<Minutely> {
        if (series.isEmpty()) {
            return ArrayList()
        }
        val start = series[0].date.time
        val list = ArrayList<Minutely>(series.size)
        for (point in series) {
            list.add(
                Minutely(
                    point.date,
                    point.date.time,
                    isDaylight(point.symbol, point.date, zone),
                    weatherTextOf(point.symbol),
                    weatherCodeOf(point.symbol),
                    ((point.date.time - start) / 60000L).toInt(),
                    null,
                    null
                )
            )
        }
        return list
    }

    /**
     * `awareness_level` is "\<n\>; \<colour\>; \<word\>", e.g. "2; yellow; Moderate" — the rank and
     * the colour both live in that one string, running 2 (yellow) to 4 (red).
     *
     * The endpoint already filters by the lat/lon it was called with, so there is no geometry test
     * to do here; outside Norway it simply answers an empty feature list.
     */
    private fun convertAlertList(result: MetNoAlertResult?): List<Alert> {
        val features = result?.features ?: return ArrayList()
        val list = ArrayList<Alert>(features.size)
        for (feature in features) {
            val properties = feature.properties ?: continue
            val description = properties.eventAwarenessName
                ?: properties.title
                ?: properties.event
                ?: continue
            val start = parseIso(feature.`when`?.interval?.firstOrNull()) ?: Date()
            val level = properties.awarenessLevel?.split(";")?.map { it.trim() } ?: emptyList()

            list.add(
                Alert(
                    // The CAP id is a String and alertId is a long; it only has to be stable and
                    // distinct within one refresh.
                    (properties.id ?: description).hashCode().toLong(),
                    start,
                    start.time,
                    description,
                    listOfNotNull(
                        properties.description, properties.consequences, properties.instruction
                    ).joinToString("\n\n"),
                    properties.event ?: "",
                    level.getOrNull(0)?.toIntOrNull() ?: 1,
                    alertColorOf(level.getOrNull(1))
                )
            )
        }
        return list
    }

    private fun alertColorOf(colour: String?): Int = when (colour?.lowercase()) {
        "red" -> 0xFFF44336.toInt()
        "orange" -> 0xFFFF9800.toInt()
        "yellow" -> 0xFFFFC107.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    /** MET Norway reports m/s; the app stores km/h (see `Wind.WIND_SPEED_*`), hence the 3.6. */
    private fun windOf(context: Context, degree: Double?, metresPerSecond: Double?): Wind {
        val speed = metresPerSecond?.times(3.6)?.toFloat()
        val deg = degree?.toFloat()
        return Wind(
            if (deg == null) "" else windDirectionOf(deg),
            WindDegree(deg ?: 0f, deg == null),
            speed,
            if (speed == null) "" else CommonConverter.getWindLevel(context, speed.toDouble())
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

    /**
     * `symbol_code` is "\<base\>[_day|_night|_polartwilight]" over ~41 bases: clearsky, fair,
     * partlycloudy, cloudy, fog, and light/plain/heavy × rain|sleet|snow × optional showers ×
     * optional andthunder.
     *
     * Matching on the parts rather than enumerating all ~100 spellings also absorbs the one typo in
     * MET Norway's own published list ("lightssleetshowersandthunder").
     *
     * `WeatherCode.getInstance` is not reused here: it looks for "partly_cloudy" with an underscore,
     * which this API never writes, so every partlycloudy* would collapse to plain CLOUDY.
     */
    private fun weatherCodeOf(symbol: String?): WeatherCode {
        val base = baseOf(symbol) ?: return WeatherCode.CLEAR
        return when {
            base.contains("thunder") -> WeatherCode.THUNDERSTORM
            base.contains("sleet") -> WeatherCode.SLEET
            base.contains("snow") -> WeatherCode.SNOW
            base.contains("rain") -> WeatherCode.RAIN
            base.contains("fog") -> WeatherCode.FOG
            // "partlycloudy" contains "cloudy", so it has to be tested first.
            base.contains("partlycloudy") || base.contains("fair") -> WeatherCode.PARTLY_CLOUDY
            base.contains("cloudy") -> WeatherCode.CLOUDY
            else -> WeatherCode.CLEAR
        }
    }

    /** Empty rather than "Unknown" when there is no symbol, so the UI shows nothing, not a guess. */
    private fun weatherTextOf(symbol: String?): String {
        val base = baseOf(symbol) ?: return ""
        val intensity = when {
            base.startsWith("heavy") -> "Heavy "
            base.startsWith("light") -> "Light "
            else -> ""
        }
        val core = when {
            base.contains("sleet") -> "sleet"
            base.contains("snow") -> "snow"
            base.contains("rain") -> "rain"
            base.contains("fog") -> "fog"
            base.contains("partlycloudy") -> "partly cloudy"
            base.contains("cloudy") -> "cloudy"
            base.contains("fair") -> "fair"
            base.contains("clearsky") -> "clear sky"
            else -> base
        }
        val showers = if (base.contains("showers")) " showers" else ""
        val thunder = if (base.contains("thunder")) " and thunder" else ""
        return (intensity + core + showers + thunder).replaceFirstChar { it.uppercaseChar() }
    }

    private fun baseOf(symbol: String?): String? =
        if (symbol.isNullOrEmpty()) null else symbol.substringBefore("_")

    /**
     * Times arrive either UTC-with-Z ("2026-08-23T15:00:00Z", forecast and air quality) or with an
     * explicit offset ("2026-08-05T08:30:00+00:00", alerts). minSdk 21 has no ISO-8601 'X' pattern,
     * so both are normalised into the "+0000" form that 'Z' parses — the same problem
     * `ApiModule.provideMfWeatherApi` solves for Météo France.
     */
    private fun parseIso(value: String?): Date? {
        if (value.isNullOrEmpty()) {
            return null
        }
        val text = when {
            value.endsWith("Z") -> value.dropLast(1) + "+0000"
            // Six from the end is where the sign of "+00:00" sits; drop that offset's colon.
            value.length >= 6 && (value[value.length - 6] == '+' || value[value.length - 6] == '-') ->
                value.removeRange(value.length - 3, value.length - 2)
            else -> "$value+0000"
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply {
                isLenient = false
            }.parse(text)
        } catch (e: ParseException) {
            null
        }
    }
}
