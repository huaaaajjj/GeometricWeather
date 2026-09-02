package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.settings.SettingsManager
import wangdaye.com.geometricweather.weather.apis.OwmApi
import wangdaye.com.geometricweather.weather.converters.OwmResultConverter
import javax.inject.Inject

/**
 * OpenWeather service, on the free data/2.5 endpoints.
 *
 * Current conditions and the forecast are required; air pollution is a separate OpenWeather product
 * that goes down on its own, so losing it costs the AQI reading and nothing else.
 */
class OwmWeatherService @Inject constructor(
    private val api: OwmApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        val settings = SettingsManager.getInstance(context)
        val language = settings.language.code
        val key = settings.providerOwmKey
        val lat = location.latitude.toDouble()
        val lon = location.longitude.toDouble()

        requests.launch {
            val current = async {
                requests.execute(api.getCurrentWeather(key, lat, lon, UNITS, language))
            }
            val forecast = async {
                requests.execute(api.getForecast(key, lat, lon, UNITS, language, FORECAST_STEPS))
            }
            val airPollution = async {
                requests.execute(api.getAirPollutionCurrent(key, lat, lon))
            }

            val currentResult = current.await()
            val forecastResult = forecast.await()
            val airPollutionResult = airPollution.await()

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            if (currentResult == null || forecastResult == null) {
                callback.requestWeatherFailed(location)
                return@launch
            }

            val weather = OwmResultConverter.convert(
                context, location, currentResult, forecastResult, airPollutionResult
            ).result
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
        val key = SettingsManager.getInstance(context).providerOwmKey
        val coordinates = "${location.latitude},${location.longitude}"

        requests.launch {
            val results = requests.execute(
                api.getWeatherLocationByGeoPosition(key, location.latitude.toDouble(),
                    location.longitude.toDouble(), 1)
            )

            if (!isActive) {
                return@launch
            }
            val resolved = results?.firstOrNull()?.let { OwmResultConverter.convert(it) }
            if (resolved != null) {
                callback.requestLocationSuccess(coordinates, listOf(resolved))
            } else {
                callback.requestLocationFailed(coordinates)
            }
        }
    }

    override fun cancel() = requests.cancel()

    companion object {
        private const val UNITS = "metric"

        // The free forecast endpoint serves 3-hour steps; 40 of them is its five-day maximum.
        private const val FORECAST_STEPS = 40
    }
}
