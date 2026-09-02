package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.weather.apis.OpenMeteoAirQualityApi
import wangdaye.com.geometricweather.weather.apis.OpenMeteoApi
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter
import java.util.TimeZone
import javax.inject.Inject

/**
 * Open-Meteo service. Free and key-less, and it answers current, hourly and daily in one call.
 *
 * Air quality (including the European pollen columns) lives on a separate host and is the only
 * optional call: its failure must not sink the refresh, coverage outside Europe simply comes
 * back with null columns.
 *
 * There is no place search: the API is coordinate-only, so [requestLocation] echoes the location
 * back and the caller keeps whatever name it already had.
 */
class OpenMeteoWeatherService @Inject constructor(
    private val api: OpenMeteoApi,
    private val airQualityApi: OpenMeteoAirQualityApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        val lat = location.latitude.toDouble()
        val lon = location.longitude.toDouble()
        val timezone = TimeZone.getDefault().id

        requests.launch {
            val forecast = async {
                requests.execute(
                    api.getForecast(
                        lat,
                        lon,
                        CURRENT_FIELDS,
                        HOURLY_FIELDS,
                        DAILY_FIELDS,
                        timezone,
                        FORECAST_DAYS,
                        PAST_DAYS
                    )
                )
            }
            val airQuality = async {
                requests.execute(
                    airQualityApi.getAirQuality(
                        lat,
                        lon,
                        AIR_QUALITY_CURRENT_FIELDS,
                        AIR_QUALITY_HOURLY_FIELDS,
                        timezone,
                        AIR_QUALITY_FORECAST_DAYS
                    )
                )
            }
            val forecastResult = forecast.await()
            val airQualityResult = airQuality.await()

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            val weather = forecastResult?.let {
                OpenMeteoResultConverter.convert(context, location, it, airQualityResult)
            }

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

        private const val FORECAST_DAYS = 16

        private const val AIR_QUALITY_CURRENT_FIELDS =
                "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"

        // Pollen only: the hourly pm columns are consumed by nothing, and the aqi endpoint's
        // own european_aqi/us_aqi are grade numbers that must never be filed as a 0-500 index.
        private const val AIR_QUALITY_HOURLY_FIELDS =
                "alder_pollen,birch_pollen,grass_pollen,olive_pollen,ragweed_pollen"

        // The air quality API rejects forecast_days > 7 outright instead of clamping.
        private const val AIR_QUALITY_FORECAST_DAYS = 7

        // Never ask for past days. The day-over-day comparison this once claimed to serve is fed by
        // the history table (DatabaseHelper.readWeather), not by the response — and the converter
        // never split a past day off, so with past_days=1 yesterday sat at dailyForecast[0] and its
        // 24 hours sat at the head of hourlyForecast. The whole app reads index 0 as "today"/"now"
        // (76 sites, plus HistoryEntityGenerator, which dated yesterday's temperatures as today's).
        private const val PAST_DAYS = 0
    }
}
