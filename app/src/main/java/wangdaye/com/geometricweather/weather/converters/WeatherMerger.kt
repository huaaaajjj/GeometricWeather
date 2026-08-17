package wangdaye.com.geometricweather.weather.converters

import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality
import wangdaye.com.geometricweather.common.basic.models.weather.Alert
import wangdaye.com.geometricweather.common.basic.models.weather.Astro
import wangdaye.com.geometricweather.common.basic.models.weather.Current
import wangdaye.com.geometricweather.common.basic.models.weather.Daily
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase
import wangdaye.com.geometricweather.common.basic.models.weather.Pollen
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Folds several providers' answers for the same place into one [Weather].
 *
 * The rule is **block-granular, never field-granular**. Whichever provider comes first in `results`
 * leads, and its entries travel whole: one day's temperature, condition text, precipitation and
 * wind always come from the same provider. Mixing those is how you end up rendering "clear sky"
 * over 5 mm of rain — an answer worse than either provider gave on its own.
 *
 * Only blocks that describe something *independent* of the forecast narrative are taken from the
 * others, and only where the leader left them empty: air quality, UV, pollen, sunrise/sunset, moon
 * phase, hours of sun. Days and hours the leader does not cover at all are appended whole, so the
 * series runs as long as the longest provider rather than as long as the leader.
 *
 * Alignment is by calendar day (in [timeZone]) and by absolute hour. Both hold as long as every
 * provider dates its entries the same way — today they all parse into the device time zone, so a
 * location in a *different* zone than the device inherits each provider's own pre-existing skew and
 * the union simply grows a few extra days. Nothing here can crash on that; it just gets vaguer.
 */
object WeatherMerger {

    @JvmStatic
    fun merge(results: List<Weather>, timeZone: TimeZone): Weather? {
        if (results.isEmpty()) {
            return null
        }
        val leader = results[0]
        if (results.size == 1) {
            return leader
        }
        return Weather(
            leader.base,
            mergeCurrent(leader.current, results.drop(1).map { it.current }),
            results.firstNotNullOfOrNull { it.yesterday },
            mergeDaily(results, timeZone),
            mergeHourly(results),
            results.firstOrNull { it.minutelyForecast.isNotEmpty() }?.minutelyForecast ?: emptyList(),
            mergeAlerts(results)
        )
    }

    /**
     * "Now" is a single instant every provider is describing at once, so the independent scalar
     * readings (humidity, pressure, visibility, …) are safe to take from whoever has them.
     */
    private fun mergeCurrent(leader: Current, others: List<Current>) = Current(
        leader.weatherText,
        leader.weatherCode,
        leader.temperature,
        leader.precipitation,
        leader.precipitationProbability,
        leader.wind,
        pick(listOf(leader.uv) + others.map { it.uv }, UV::isValid) ?: leader.uv,
        pick(listOf(leader.airQuality) + others.map { it.airQuality }, AirQuality::isValid)
            ?: leader.airQuality,
        firstNonNull(leader.relativeHumidity, others.map { it.relativeHumidity }),
        firstNonNull(leader.pressure, others.map { it.pressure }),
        firstNonNull(leader.visibility, others.map { it.visibility }),
        firstNonNull(leader.dewPoint, others.map { it.dewPoint }),
        firstNonNull(leader.cloudCover, others.map { it.cloudCover }),
        firstNonNull(leader.ceiling, others.map { it.ceiling }),
        firstNonNull(leader.dailyForecast, others.map { it.dailyForecast }),
        firstNonNull(leader.hourlyForecast, others.map { it.hourlyForecast })
    )

    private fun mergeDaily(results: List<Weather>, timeZone: TimeZone): List<Daily> {
        val format = SimpleDateFormat("yyyyMMdd", Locale.US).apply { this.timeZone = timeZone }
        val keyOf = { date: Date? -> date?.let { format.format(it) } }

        val others = results.drop(1).map { weather ->
            weather.dailyForecast.mapNotNull { d -> keyOf(d.date)?.let { it to d } }.toMap()
        }

        val merged = LinkedHashMap<String, Daily>()
        for (day in results[0].dailyForecast) {
            val key = keyOf(day.date) ?: continue
            merged[key] = fillDaily(day, others.mapNotNull { it[key] })
        }
        // Days beyond the leader's range: take the whole entry from the best source that has one.
        for (source in others) {
            for ((key, day) in source) {
                if (!merged.containsKey(key)) {
                    merged[key] = day
                }
            }
        }
        return merged.values.sortedBy { it.time }
    }

    private fun fillDaily(leader: Daily, others: List<Daily>) = Daily(
        leader.date,
        leader.time,
        leader.day(),
        leader.night(),
        pick(listOf(leader.sun()) + others.map { it.sun() }, Astro::isValid),
        pick(listOf(leader.moon()) + others.map { it.moon() }, Astro::isValid),
        pick(listOf(leader.moonPhase) + others.map { it.moonPhase }, MoonPhase::isValid),
        pick(listOf(leader.airQuality) + others.map { it.airQuality }, AirQuality::isValid),
        pick(listOf(leader.pollen) + others.map { it.pollen }, Pollen::isValid),
        pick(listOf(leader.uv) + others.map { it.uv }, UV::isValid),
        if (leader.hoursOfSun > 0) leader.hoursOfSun
        else others.firstOrNull { it.hoursOfSun > 0 }?.hoursOfSun ?: leader.hoursOfSun
    )

    /**
     * Hours are bucketed by absolute time, not by wall clock: providers all report on the hour, so
     * the same instant lands in the same bucket whatever offset the place is at.
     */
    private fun mergeHourly(results: List<Weather>): List<Hourly> {
        val others = results.drop(1).map { weather ->
            weather.hourlyForecast.associateBy { it.time / HOUR_MS }
        }

        val merged = LinkedHashMap<Long, Hourly>()
        for (hour in results[0].hourlyForecast) {
            val key = hour.time / HOUR_MS
            val alternatives = others.mapNotNull { it[key] }
            merged[key] = Hourly(
                hour.date,
                hour.time,
                hour.isDaylight,
                hour.weatherText,
                hour.weatherCode,
                hour.temperature,
                hour.precipitation,
                hour.precipitationProbability,
                hour.wind,
                pick(listOf(hour.uv) + alternatives.map { it.uv }, UV::isValid)
            )
        }
        for (source in others) {
            for ((key, hour) in source) {
                if (!merged.containsKey(key)) {
                    merged[key] = hour
                }
            }
        }
        return merged.values.sortedBy { it.time }
    }

    /** Union rather than pick-one: two providers rarely carry the same warning for a place. */
    private fun mergeAlerts(results: List<Weather>): List<Alert> {
        val seen = HashSet<String>()
        return results
            .flatMap { it.alertList }
            .filter { seen.add("${it.description}|${it.content}") }
    }

    /** The first candidate carrying data, falling back to the leader's own — empty or not. */
    private fun <T : Any> pick(candidates: List<T?>, valid: (T) -> Boolean): T? =
        candidates.firstOrNull { it != null && valid(it) } ?: candidates.firstOrNull()

    private fun <T : Any> firstNonNull(leader: T?, others: List<T?>): T? =
        leader ?: others.firstOrNull { it != null }

    private const val HOUR_MS = 60 * 60 * 1000L
}
