package wangdaye.com.geometricweather.weather.converters

import wangdaye.com.geometricweather.common.basic.models.weather.AirQuality
import wangdaye.com.geometricweather.common.basic.models.weather.Alert
import wangdaye.com.geometricweather.common.basic.models.weather.Astro
import wangdaye.com.geometricweather.common.basic.models.weather.Current
import wangdaye.com.geometricweather.common.basic.models.weather.Daily
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly
import wangdaye.com.geometricweather.common.basic.models.weather.MoonPhase
import wangdaye.com.geometricweather.common.basic.models.weather.Pollen
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.common.basic.models.weather.Wind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Folds several providers' answers for the same place into one [Weather].
 *
 * The rule is **block-granular by default**. Each block — the daily overview, the hourly series, the
 * "now" reading, the air quality — has its own leader, and that leader's entries travel whole: a
 * day's temperature and condition text always come from the same provider, because those two are
 * what a forecast *is*. Mixing them is how you end up rendering "clear sky" at another provider's
 * 31° — an answer worse than either gave on its own.
 *
 * Within a block, only readings that are *independent* of the forecast narrative are taken from the
 * others, and only where the leader left them empty: air quality, UV, pollen, sunrise/sunset, moon
 * phase, hours of sun, and — inside a half day — the chance of rain, the amount and the wind (see
 * [fillHalfDay], which is where this rule is deliberately bent and why). Days and hours the leader
 * does not cover at all are appended whole, so a series runs as long as the longest provider rather
 * than as long as its leader.
 *
 * Alignment is by calendar day (in [timeZone]) and by absolute hour. Both hold as long as every
 * provider dates its entries the same way — today they all parse into the device time zone, so a
 * location in a *different* zone than the device inherits each provider's own pre-existing skew and
 * the union simply grows a few extra days. Nothing here can crash on that; it just gets vaguer.
 */
object WeatherMerger {

    /**
     * @param results every provider that answered, in fallback order. Supplies the base timestamp,
     *   the warnings and the minutely rain, and backs every block below.
     * @param daily the same weathers reordered so whoever should lead the daily overview comes
     *   first; likewise [hourly], [current] (the "now" reading and its detail scalars) and
     *   [airQuality]. Preference, not binding — a provider that failed to answer, or that carries
     *   nothing for the block, falls through to the next one, so a block still fills if anyone has
     *   it. Omitting a list means "same order as [results]".
     */
    @JvmStatic
    @JvmOverloads
    fun merge(
        results: List<Weather>,
        timeZone: TimeZone,
        daily: List<Weather> = results,
        hourly: List<Weather> = results,
        current: List<Weather> = results,
        airQuality: List<Weather> = results
    ): Weather? {
        if (results.isEmpty()) {
            return null
        }
        if (results.size == 1) {
            return results[0]
        }
        // A caller that hands over an empty preference list means "no preference", not "no data".
        val dailyOrder = daily.ifEmpty { results }
        val hourlyOrder = hourly.ifEmpty { results }
        val currentOrder = current.ifEmpty { results }
        val airOrder = airQuality.ifEmpty { results }
        return Weather(
            results[0].base,
            mergeCurrent(currentOrder, airOrder),
            results.firstNotNullOfOrNull { it.yesterday },
            mergeDaily(dailyOrder, airOrder, timeZone),
            mergeHourly(hourlyOrder),
            results.firstOrNull { it.minutelyForecast.isNotEmpty() }?.minutelyForecast ?: emptyList(),
            mergeAlerts(results)
        )
    }

    /**
     * "Now" is a single instant every provider is describing at once, so the independent scalar
     * readings (humidity, pressure, visibility, …) are safe to take from whoever has them.
     */
    private fun mergeCurrent(current: List<Weather>, air: List<Weather>): Current {
        val leader = current[0].current
        val others = current.drop(1).map { it.current }
        val airCandidates = air.map { it.current.airQuality }
        return Current(
            leader.weatherText,
            leader.weatherCode,
            leader.temperature,
            leader.precipitation,
            leader.precipitationProbability,
            leader.wind,
            pick(listOf(leader.uv) + others.map { it.uv }, UV::isValid) ?: leader.uv,
            pick(airCandidates + leader.airQuality + others.map { it.airQuality },
                AirQuality::isValid) ?: leader.airQuality,
            firstNonNull(leader.relativeHumidity, others.map { it.relativeHumidity }),
            firstNonNull(leader.pressure, others.map { it.pressure }),
            firstNonNull(leader.visibility, others.map { it.visibility }),
            firstNonNull(leader.dewPoint, others.map { it.dewPoint }),
            firstNonNull(leader.cloudCover, others.map { it.cloudCover }),
            firstNonNull(leader.ceiling, others.map { it.ceiling }),
            firstNonNull(leader.dailyForecast, others.map { it.dailyForecast }),
            firstNonNull(leader.hourlyForecast, others.map { it.hourlyForecast })
        )
    }

    private fun mergeDaily(
        results: List<Weather>,
        air: List<Weather>,
        timeZone: TimeZone
    ): List<Daily> {
        val format = SimpleDateFormat("yyyyMMdd", Locale.US).apply { this.timeZone = timeZone }
        val keyOf = { date: Date? -> date?.let { format.format(it) } }
        val byDay = { weather: Weather ->
            weather.dailyForecast.mapNotNull { d -> keyOf(d.date)?.let { it to d } }.toMap()
        }

        val others = results.drop(1).map(byDay)
        val airByDay = air.map(byDay)

        val merged = LinkedHashMap<String, Daily>()
        for (day in results[0].dailyForecast) {
            val key = keyOf(day.date) ?: continue
            merged[key] = fillDaily(
                day,
                others.mapNotNull { it[key] },
                airByDay.mapNotNull { it[key] }
            )
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

    private fun fillDaily(leader: Daily, others: List<Daily>, air: List<Daily>) = Daily(
        leader.date,
        leader.time,
        fillHalfDay(leader.day(), others.map { it.day() }),
        fillHalfDay(leader.night(), others.map { it.night() }),
        pick(listOf(leader.sun()) + others.map { it.sun() }, Astro::isValid),
        pick(listOf(leader.moon()) + others.map { it.moon() }, Astro::isValid),
        pick(listOf(leader.moonPhase) + others.map { it.moonPhase }, MoonPhase::isValid),
        pick(air.map { it.airQuality } + leader.airQuality + others.map { it.airQuality },
            AirQuality::isValid),
        pick(listOf(leader.pollen) + others.map { it.pollen }, Pollen::isValid),
        pick(listOf(leader.uv) + others.map { it.uv }, UV::isValid),
        if (leader.hoursOfSun > 0) leader.hoursOfSun
        else others.firstOrNull { it.hoursOfSun > 0 }?.hoursOfSun ?: leader.hoursOfSun
    )

    /**
     * The three readings a day's card can draw that a leader may simply not carry: the chance of
     * rain, how much of it, and the wind. 中国天气网 leads the daily overview and has none of them —
     * its half days are built with `PrecipitationProbability(null, …)`, `Precipitation(null, …)` and
     * a wind speed hard-coded to `0f` — so its seven days drew a bare temperature curve while the
     * appended days 8..16, whole entries from Open-Meteo, drew all three. Each is filled from the
     * first other provider that has one, and only where the leader has nothing.
     *
     * Each sub-reading travels whole: a wind is a direction *and* a speed measured together, so
     * grafting the vector is right and stitching one provider's bearing onto another's speed is not.
     *
     * **This is the one place the block rule is bent, deliberately.** The class comment warns that
     * mixing a day's readings is how you render "clear sky" over another provider's 5 mm — and with
     * the amount grafted, that combination is now reachable: 中国天气网 can say 晴 for a day
     * Open-Meteo gives rain for. The judgement is that a mostly-empty card is the worse of the two
     * failures, so completeness wins here; the condition text and the temperature, which are what a
     * forecast *is*, still always come from the leader alone.
     */
    private fun fillHalfDay(leader: HalfDay, others: List<HalfDay>) = HalfDay(
        leader.weatherText,
        leader.weatherPhase,
        leader.weatherCode,
        leader.temperature,
        pick(listOf(leader.precipitation) + others.map { it.precipitation }, Precipitation::isValid)
            ?: leader.precipitation,
        pick(listOf(leader.precipitationProbability) + others.map { it.precipitationProbability },
            PrecipitationProbability::isValid) ?: leader.precipitationProbability,
        leader.precipitationDuration,
        pick(listOf(leader.wind) + others.map { it.wind }, Wind::isValidSpeed) ?: leader.wind,
        firstNonNull(leader.cloudCover, others.map { it.cloudCover })
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
