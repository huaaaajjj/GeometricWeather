package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.weather.apis.OpenMeteoApi
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter
import java.util.TimeZone
import javax.inject.Inject

/**
 * Open-Meteo service. Free and key-less, and it answers current, hourly and daily in one call.
 *
 * There is no place search: the API is coordinate-only, so [requestLocation] echoes the location
 * back and the caller keeps whatever name it already had.
 */
class OpenMeteoWeatherService @Inject constructor(
    private val api: OpenMeteoApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            val result = requests.execute(
                api.getForecast(
                    location.latitude.toDouble(),
                    location.longitude.toDouble(),
                    CURRENT_FIELDS,
                    HOURLY_FIELDS,
                    DAILY_FIELDS,
                    TimeZone.getDefault().id,
                    FORECAST_DAYS,
                    PAST_DAYS
                )
            )
            val weather = result?.let { OpenMeteoResultConverter.convert(context, location, it) }

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
        private const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m," +
                "apparent_temperature,precipitation,weather_code,cloud_cover,pressure_msl," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m"

        private const val HOURLY_FIELDS = "temperature_2m,relative_humidity_2m," +
                "apparent_temperature,precipitation_probability,precipitation,weather_code," +
                "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "uv_index,visibility,is_day"

        private const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min," +
                "apparent_temperature_max,apparent_temperature_min,sunrise,sunset," +
                "precipitation_sum,precipitation_probability_max,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max,sunshine_duration"

        private const val FORECAST_DAYS = 15

        // Yesterday, for the day-over-day comparison on the main screen.
        private const val PAST_DAYS = 1
    }
}
