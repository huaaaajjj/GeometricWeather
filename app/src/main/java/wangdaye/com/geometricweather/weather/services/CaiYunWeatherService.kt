package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.BuildConfig
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.utils.LanguageUtils
import wangdaye.com.geometricweather.db.DatabaseHelper
import wangdaye.com.geometricweather.weather.apis.CaiYunApi
import wangdaye.com.geometricweather.weather.converters.CaiyunResultConverter
import javax.inject.Inject

/**
 * CaiYun (彩云天气) service, on the official v2.6 token endpoint.
 *
 * Weather is one call, keyed by "{lon},{lat}" in the URL path — longitude first. Location search
 * does not go to the network at all: it reads the bundled Chinese city table out of Room, which is
 * also how a current position is matched to a city.
 */
class CaiYunWeatherService @Inject constructor(
    private val api: CaiYunApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            val result = requests.execute(
                api.getWeather(
                    BuildConfig.CAIYUN_WEATHER_KEY,
                    location.longitude.toString(),
                    location.latitude.toString(),
                    true
                )
            )
            val weather = result?.let {
                CaiyunResultConverter.convert(context, location, it).result
            }

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

    override fun requestLocation(context: Context, query: String): List<Location> {
        if (!LanguageUtils.isChinese(query)) {
            return emptyList()
        }
        val database = DatabaseHelper.getInstance(context)
        database.ensureChineseCityList(context)
        return database.readChineseCityList(query).map { it.toLocation() }
    }

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        val hasGeocodeInformation = location.hasGeocodeInformation()

        requests.launch {
            val database = DatabaseHelper.getInstance(context)
            database.ensureChineseCityList(context)

            // Prefer the reverse-geocoded names; fall back to matching the raw coordinates.
            val city = if (hasGeocodeInformation) {
                database.readChineseCity(
                    formatLocationString(convertChinese(location.province)),
                    formatLocationString(convertChinese(location.city)),
                    formatLocationString(convertChinese(location.district))
                )
            } else {
                null
            } ?: database.readChineseCity(location.latitude, location.longitude)

            if (!isActive) {
                return@launch
            }
            if (city != null) {
                callback.requestLocationSuccess(location.formattedId, listOf(city.toLocation()))
            } else {
                callback.requestLocationFailed(location.formattedId)
            }
        }
    }

    override fun cancel() = requests.cancel()
}
