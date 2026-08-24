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
import java.util.TimeZone
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
 *   vector measured together, which is what the hourly card draws; Open-Meteo's hours are appended
 *   after them, so the series still runs the full 384. 小米's hourly entries carry no chance of rain
 *   and no amount, and those two are grafted in from whoever does have them ([WeatherMerger]).
 * - **daily overview** → 中国天气网 (APIHZ): a domestic forecast for a domestic place; it reaches
 *   7 days, and Open-Meteo's days 8..16 are appended after it so the range is not lost.
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
     * assignment the cards print. Iteration order is the fallback order for anything unassigned.
     *
     * 小米天气 sits last on purpose. It leads the hourly block by assignment, and putting it there
     * too would have changed what fills everything *un*assigned — the days appended past 中国天气网's
     * seventh would come from its 15-day list instead of Open-Meteo's 16-day one, which is not what
     * this change was about.
     */
    private val members = linkedMapOf<WeatherSource, WeatherService>(
        WeatherSource.OPEN_METEO to openMeteo,
        WeatherSource.APIHZ to apihz,
        WeatherSource.CAIYUN to caiyun,
        WeatherSource.WEATHERAPI to weatherApi,
        WeatherSource.XIAOMI to xiaomi
    )

    private val sources = members.values.toList()

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            val answers = sources
                .map { service -> async { service to request(context, location, service) } }
                .awaitAll()
                .mapNotNull { (service, weather) -> weather?.let { service to it } }
                .toMap()

            // The block's own provider first, then everyone else as fallback.
            val preferring = { block: CompositeBlock ->
                (listOfNotNull(members[block.source]) + sources).distinct().mapNotNull { answers[it] }
            }

            // Device time zone, not the location's: that is the one every converter currently
            // parses its dates into, so it is the one the day keys have to line up in.
            val weather = WeatherMerger.merge(
                results = sources.mapNotNull { answers[it] },
                timeZone = TimeZone.getDefault(),
                daily = preferring(CompositeBlock.DAILY),
                hourly = preferring(CompositeBlock.HOURLY),
                current = preferring(CompositeBlock.CURRENT),
                airQuality = preferring(CompositeBlock.AIR_QUALITY)
            )
                // The daily leader can lag: a domestic source still serves yesterday as its first
                // day for hours after midnight, and everything downstream reads day 0 as today.
                ?.withDaysFrom(System.currentTimeMillis())

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
    override fun requestLocation(context: Context, query: String): List<Location> = emptyList()

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
