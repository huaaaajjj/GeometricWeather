package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.BuildConfig
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.weather.apis.XiaomiApi
import wangdaye.com.geometricweather.weather.converters.XiaomiResultConverter
import javax.inject.Inject

/**
 * Xiaomi Weather service — the MIUI weather app's own endpoints, no key and no registration.
 *
 * Inside China it is the richest source available here in one shot: current, 15 daily, 23 hourly,
 * air quality with real concentrations, alerts, and minute-by-minute precipitation for the next two
 * hours. Outside China the same endpoints fall through to AccuWeather and answer 5 days without air
 * quality, which makes this a usable global fallback too.
 *
 * **A refresh is two steps.** `weather/all` needs a `locationKey` that only `location/city/geo` can
 * produce, and there is nowhere to cache it — the Room schema is frozen at v63 and `Location` has no
 * per-source parameter map, so reusing `cityId` for it would poison the weather cache key (the
 * 3.4.13/3.4.14 bug). Resolving every time costs one small request and keeps the source stateless.
 *
 * There is no place search: [requestLocation] echoes the location back, since the resolve step does
 * the geocoding itself from the coordinates.
 */
class XiaomiWeatherService @Inject constructor(
    private val api: XiaomiApi
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
            // Step 1: coordinates -> locationKey. Its prefix also decides which backend answers.
            val locationKey = requests.execute(api.getLocation(lat, lon, LOCALE))
                ?.firstOrNull { it?.status == 0 && !it.locationKey.isNullOrEmpty() }
                ?.locationKey

            if (locationKey == null) {
                if (isActive) {
                    callback.requestWeatherFailed(location)
                }
                return@launch
            }
            // isGlobal has to agree with the key's prefix or the forecast comes back empty.
            val global = !locationKey.startsWith(CHINA_KEY_PREFIX)

            // Step 2: both data calls in parallel — verified to work concurrently on 2026-08-24.
            val forecast = async {
                requests.execute(
                    api.getForecast(
                        lat, lon, location.isCurrentPosition, locationKey, FORECAST_DAYS,
                        BuildConfig.XIAOMI_APP_KEY, BuildConfig.XIAOMI_SIGN, global, LOCALE
                    )
                )
            }
            val minutely = async {
                requests.execute(
                    api.getMinutely(
                        lat, lon, LOCALE, global,
                        BuildConfig.XIAOMI_APP_KEY, locationKey, BuildConfig.XIAOMI_SIGN
                    )
                )
            }
            val forecastResult = forecast.await()
            val minutelyResult = minutely.await()

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            val weather = XiaomiResultConverter.convert(
                context, location, forecastResult, minutelyResult
            )
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
        /** Capped by the backend anyway: 15 days in China, 5 abroad. */
        private const val FORECAST_DAYS = 15

        private const val CHINA_KEY_PREFIX = "weathercn:"

        /**
         * Fixed at Chinese rather than following the app language. This is the market (China) host,
         * which Xiaomi's own client only uses for Chinese locales because its English text is wrong;
         * asking it for Chinese is the honest option, and matches CMA and APIHZ, which are Chinese
         * text either way.
         */
        private const val LOCALE = "zh_cn"
    }
}
