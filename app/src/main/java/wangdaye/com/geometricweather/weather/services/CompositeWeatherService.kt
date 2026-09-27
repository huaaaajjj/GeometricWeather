package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.CompositeBlock
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.weather.converters.WeatherMerger
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Composite source: asks several providers at once and folds their answers into one.
 *
 * Every provider here is incomplete in a different way, so no single one is the best answer, and no
 * single one leads everything either — each block is assigned to whoever is best at it. The
 * assignment itself lives in [CompositeBlock], shared with the cards that print it:
 *
 * - **hourly** → 小米天气: its ~23 hours carry a temperature, a condition text *and* a real wind
 *   vector measured together, which is what the hourly card draws; the hours past its range are
 *   appended from the other domestic sources first and only then Open-Meteo, so the series still
 *   runs the full 384. 小米's hourly entries carry no chance of rain and no amount, and those two
 *   are grafted in from whoever does have them ([WeatherMerger]).
 * - **daily overview** → 小米天气: a domestic 15-day forecast that carries the daily precip, chance
 *   of rain and wind natively (unlike 中国天气网, whose 7 days have none), so the card draws full
 *   days without grafting. Day 16 (only Open-Meteo reaches it) is appended after; 中国天气网 and the
 *   others are the fallback if 小米 declines.
 * - **air quality** and the **"now" reading with its detail scalars** → 彩云: measured Chinese AQI
 *   (Open-Meteo carries none at all) plus feels-like, humidity, pressure and visibility.
 * - **warnings** → the union of everyone; WeatherAPI is the one that reliably has them.
 * - **minute-by-minute rain** → the first member that has any, which inside China is 小米天气: none
 *   of the other four carries a minutely block (彩云 would, but only on a paid token).
 *
 * [WeatherMerger] does the folding — see it for what may and may not be mixed. The order is a
 * preference, not a binding: a provider that fails, answers with nothing for its block, or never
 * comes back within [SOURCE_TIMEOUT_MS] simply drops through to the next one. The refresh succeeds
 * on whoever is left and only fails if nobody answered — so a place outside China, where APIHZ and
 * 彩云 have nothing to say, still gets a full forecast from Open-Meteo and WeatherAPI.
 *
 * The cost is one network round trip per member per refresh. That is the trade: more complete data
 * for more data used.
 */
class CompositeWeatherService @Inject constructor(
    private val openMeteo: OpenMeteoWeatherService,
    private val apihz: ApihzWeatherService,
    private val caiyun: CaiYunWeatherService,
    private val weatherApi: WeatherApiWeatherService,
    private val xiaomi: XiaomiWeatherService
) : WeatherService() {

    /**
     * The members, keyed by the source they are, so [CompositeBlock] can hand out the same
     * assignment the cards print. Iteration order is the fallback order: for each block the assigned
     * leader is tried first (see [CompositeBlock]), then these in order fill the leader's gaps and
     * append the days/hours it does not reach.
     *
     * **Domestic sources first, Open-Meteo last.** Open-Meteo is a foreign model and reads
     * inaccurately inside China, so it must not be the one that fills a domestic leader's gaps (the
     * daily precip/wind 中国天气网 lacks) nor the one that extends the range while a domestic source
     * still has days/hours to give. With it last, 中国天气网's seventh-day tail is extended first
     * from 小米's 15-day list and the near hours from the other domestic sources; Open-Meteo only
     * supplies what nothing domestic covers (day 16, the far hours) or a place abroad where the
     * domestic sources decline. Trade-off: abroad the daily leader is now a domestic source's short
     * range (小米 5 days / WeatherAPI 3) with Open-Meteo appended, rather than Open-Meteo leading.
     */
    private val members = linkedMapOf<WeatherSource, WeatherService>(
        WeatherSource.APIHZ to apihz,
        WeatherSource.CAIYUN to caiyun,
        WeatherSource.XIAOMI to xiaomi,
        WeatherSource.WEATHERAPI to weatherApi,
        WeatherSource.OPEN_METEO to openMeteo
    )

    private val sources = members.values.toList()

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            val answers = members
                .map { (source, service) -> async { source to request(context, location, service) } }
                .awaitAll()
                .mapNotNull { (source, weather) -> weather?.let { source to it } }
                .toMap()

            // The block's own provider first, then everyone else as fallback.
            val order = members.keys.toList()
            val preferring = { block: CompositeBlock ->
                (listOf(block.source) + order).distinct().mapNotNull { answers[it] }
            }

            // The location's own zone, not the device's: the day keys below decide which providers'
            // days are the same day, and for a place a few hours away the device's calendar splits
            // one local day across two keys (and folds two into one at the other end).
            val zone = location.timeZone
            val weather = WeatherMerger.merge(
                results = order.mapNotNull { answers[it] },
                timeZone = zone,
                daily = preferring(CompositeBlock.DAILY),
                hourly = preferring(CompositeBlock.HOURLY),
                current = preferring(CompositeBlock.CURRENT),
                airQuality = preferring(CompositeBlock.AIR_QUALITY),
                // So each card can name the provider its numbers really came from, which is not the
                // assignment whenever one fell through — outside China, for instance, where two of
                // the five decline to answer at all.
                providers = answers.entries.associate { (source, weather) -> weather to source }
            )
                // The daily leader can lag: a domestic source still serves yesterday as its first
                // day for hours after midnight, and everything downstream reads day 0 as today.
                ?.withDaysFrom(System.currentTimeMillis(), zone)

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            if (weather != null) {
                callback.requestWeatherSuccess(Location.copy(location, weather))
            } else {
                callback.requestWeatherFailed(location)
            }
        }
    }

    /** One provider's answer, or null if it failed, was cancelled, or never came back. */
    private suspend fun request(
        context: Context,
        location: Location,
        service: WeatherService
    ): Weather? = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { service.cancel() }
            service.requestWeather(context, location.copy(), object : RequestWeatherCallback {

                override fun requestWeatherSuccess(requestLocation: Location) {
                    if (continuation.isActive) {
                        continuation.resume(requestLocation.weather)
                    }
                }

                override fun requestWeatherFailed(requestLocation: Location) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            })
        }
    }

    /**
     * No place search. The members disagree on what a place even is — a coordinate for Open-Meteo
     * and 彩云, a province/city name for APIHZ — and there is no answer that is right for all of
     * them, so the composite serves whatever location it is handed (the resolved current position,
     * or one saved under another source) and never rewrites it.
     */
    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        callback.requestLocationSuccess(location.getCityName(context), listOf(location))
    }

    override fun cancel() {
        // These are this service's own instances, not the ones WeatherServiceSet holds, so nothing
        // else cancels them for us.
        sources.forEach { it.cancel() }
        requests.cancel()
    }

    companion object {
        /** A provider that never calls back must not hold the refresh open; the rest still merge. */
        private const val SOURCE_TIMEOUT_MS = 30_000L
    }
}
