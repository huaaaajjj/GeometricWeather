package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.weather.apis.MetNoApi
import wangdaye.com.geometricweather.weather.converters.MetNoResultConverter
import javax.inject.Inject

/**
 * MET Norway service — the second keyless global source, so it is what remains when Open-Meteo
 * rate-limits and every keyed provider is out of quota.
 *
 * Four parallel calls, of which only the forecast is required: air quality is Norway-only, nowcast
 * is Nordics-only, and alerts are Norwegian. All three fail or come back empty elsewhere, which
 * [RequestScope.execute] degrades to null rather than letting it sink the refresh.
 *
 * There is no place search — the API is coordinate-only — so [requestLocation] echoes the location
 * back and the caller keeps the name it already had.
 */
class MetNoWeatherService @Inject constructor(
    private val api: MetNoApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        val lat = location.latitude.toDouble()
        val lon = location.longitude.toDouble()

        requests.launch {
            val forecast = async { requests.execute(api.getForecast(lat, lon)) }
            val airQuality = async { requests.execute(api.getAirQuality(lat, lon)) }
            val alerts = async { requests.execute(api.getAlerts(lat, lon, ALERT_LANGUAGE)) }
            val nowcast = async { requests.execute(api.getNowcast(lat, lon)) }

            val forecastResult = forecast.await()
            val airQualityResult = airQuality.await()
            val alertsResult = alerts.await()
            val nowcastResult = nowcast.await()

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            val weather = MetNoResultConverter.convert(
                context, location, forecastResult, airQualityResult, alertsResult, nowcastResult
            )
            if (weather != null) {
                callback.requestWeatherSuccess(Location.copy(location, weather))
            } else {
                callback.requestWeatherFailed(location)
            }
        }
    }

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        callback.requestLocationSuccess(location.getCityName(context), listOf(location))
    }

    override fun cancel() = requests.cancel()

    companion object {
        /** metalerts speaks only English and Norwegian, and this app ships no Norwegian locale. */
        private const val ALERT_LANGUAGE = "en"
    }
}
