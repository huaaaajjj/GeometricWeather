package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.BuildConfig
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.weather.apis.WeatherApiApi
import wangdaye.com.geometricweather.weather.converters.WeatherApiResultConverter
import javax.inject.Inject

/**
 * WeatherAPI service — the default source since 3.5.2, so a new install lands here first.
 *
 * One call answers current, hourly, daily, air quality and alerts, keyed by "lat,lon". There is no
 * place search: [requestLocation] echoes the location back, since the API resolves the city itself
 * from the coordinates.
 */
class WeatherApiWeatherService @Inject constructor(
    private val api: WeatherApiApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        val query = "${location.latitude},${location.longitude}"

        requests.launch {
            val result = requests.execute(
                api.getForecast(BuildConfig.WEATHERAPI_KEY, query, FORECAST_DAYS, YES, YES)
            )
            val weather = result?.let { WeatherApiResultConverter.convert(context, location, it) }

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

    override fun requestLocation(context: Context, query: String): List<Location> = emptyList()

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        callback.requestLocationSuccess(location.getCityName(context), listOf(location))
    }

    override fun cancel() = requests.cancel()

    companion object {
        private const val FORECAST_DAYS = 14

        /** The API takes its opt-in flags as "yes"/"no" rather than booleans. */
        private const val YES = "yes"
    }
}
