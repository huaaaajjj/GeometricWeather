package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.weather.converters.WeatherMerger
import java.util.TimeZone
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Composite source: asks several providers at once and folds their answers into one.
 *
 * Every provider here is incomplete in a different way, so no single one is the best answer.
 * Open-Meteo reaches furthest (16 days / 384 hours, with UV) but carries no air quality and no
 * warnings at all; WeatherAPI is shorter but has both. Together they cover each other, and
 * [WeatherMerger] does the folding — see it for what may and may not be mixed.
 *
 * The mix is [sources], in priority order, and that list is the whole configuration: the first
 * provider leads and supplies whole entries, the rest only fill blocks it left empty and extend the
 * series past its range. A provider that fails, or never answers within [SOURCE_TIMEOUT_MS], simply
 * drops out — the refresh succeeds on whoever is left, and only fails if none of them answered.
 *
 * The cost is one extra network round trip per refresh. That is the trade: more complete data for
 * more data used.
 */
class CompositeWeatherService @Inject constructor(
    openMeteo: OpenMeteoWeatherService,
    weatherApi: WeatherApiWeatherService
) : WeatherService() {

    private val sources = listOf<WeatherService>(openMeteo, weatherApi)

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            val answers = sources
                .map { async { request(context, location, it) } }
                .awaitAll()
                .filterNotNull()

            // Device time zone, not the location's: that is the one every converter currently
            // parses its dates into, so it is the one the day keys have to line up in.
            val weather = WeatherMerger.merge(answers, TimeZone.getDefault())

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

    /** Both providers are coordinate-only, so there is no place search to delegate. */
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
