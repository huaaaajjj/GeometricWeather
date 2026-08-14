package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.settings.SettingsManager
import wangdaye.com.geometricweather.weather.apis.AtmoAuraIqaApi
import wangdaye.com.geometricweather.weather.apis.MfWeatherApi
import wangdaye.com.geometricweather.weather.converters.MfResultConverter
import wangdaye.com.geometricweather.weather.json.mf.MfForecastV2Result
import javax.inject.Inject

/**
 * Météo France service (France only).
 *
 * The observation and the forecast are required; ephemeris, rain, warnings and air quality each
 * cover an optional block and are allowed to fail on their own.
 *
 * Warnings are fetched *after* the forecast rather than alongside it: they are keyed by the French
 * department number, and the forecast response is the only thing that carries it.
 */
class MfWeatherService @Inject constructor(
    private val mfApi: MfWeatherApi,
    private val atmoAuraApi: AtmoAuraIqaApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        val settings = SettingsManager.getInstance(context)
        val language = settings.language.code
        val token = settings.providerMfWsftKey
        val lat = location.latitude.toDouble()
        val lon = location.longitude.toDouble()

        requests.launch {
            val current = async { requests.execute(mfApi.getCurrent(lat, lon, language, token)) }
            val forecastAndWarnings = async {
                val forecast = requests.execute(mfApi.getForecast(lat, lon, language, token))
                val department = departmentOf(forecast, location)
                val warnings = department?.let {
                    requests.execute(mfApi.getWarnings(it, null, token))
                }
                forecast to warnings
            }
            val ephemeris = async { requests.execute(mfApi.getEphemeris(lat, lon, "en", token)) }
            val rain = async { requests.execute(mfApi.getRain(lat, lon, language, token)) }
            val airQuality = async {
                if (isAtmoAuraDepartment(location.province)) {
                    requests.execute(atmoAuraApi.getQAFull(settings.providerIqaAtmoAuraKey, lat, lon))
                } else {
                    null
                }
            }

            val currentResult = current.await()
            val (forecastResult, warningsResult) = forecastAndWarnings.await()
            val ephemerisResult = ephemeris.await()
            val rainResult = rain.await()
            val airQualityResult = airQuality.await()

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            if (currentResult == null || forecastResult == null) {
                callback.requestWeatherFailed(location)
                return@launch
            }

            val weather = MfResultConverter.convert(
                context, location, currentResult, forecastResult,
                ephemerisResult, rainResult, warningsResult, airQualityResult
            ).result
            if (weather != null) {
                callback.requestWeatherSuccess(Location.copy(location, weather))
            } else {
                callback.requestWeatherFailed(location)
            }
        }
    }

    override fun requestLocation(context: Context, query: String): List<Location> {
        val token = SettingsManager.getInstance(context).providerMfWsftKey
        val results = requests.execute(
            mfApi.callWeatherLocation(query, PARIS_LAT, PARIS_LON, token)
        ) ?: return emptyList()
        // The post code doubles as the cityId, so an entry without one is not addressable.
        return results.filter { it.postCode != null }.map { MfResultConverter.convert(null, it) }
    }

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        val settings = SettingsManager.getInstance(context)
        val language = settings.language.code
        val token = settings.providerMfWsftKey
        val coordinates = "${location.latitude},${location.longitude}"

        requests.launch {
            val result = requests.execute(
                mfApi.getForecast(location.latitude.toDouble(), location.longitude.toDouble(),
                    language, token)
            )

            if (!isActive) {
                return@launch
            }
            // The forecast doubles as the location lookup: it carries insee, timezone and department.
            if (result?.properties?.insee != null) {
                callback.requestLocationSuccess(
                    coordinates, listOf(MfResultConverter.convert(null, result))
                )
            } else {
                callback.requestLocationFailed(coordinates)
            }
        }
    }

    override fun cancel() = requests.cancel()

    companion object {
        // The place search is ranked by distance from a reference point; Paris keeps it national.
        private const val PARIS_LAT = 48.86
        private const val PARIS_LON = 2.34

        /** Warnings are keyed by department number ("75"); only the forecast reports it. */
        private fun departmentOf(forecast: MfForecastV2Result?, location: Location): String? {
            val reported = forecast?.properties?.frenchDepartment
            if (!reported.isNullOrEmpty()) {
                return reported
            }
            // Fall back to the stored province only when it already looks like a department number.
            val province = location.province
            return if (province.matches(DEPARTMENT.toRegex())) province else null
        }

        private const val DEPARTMENT = "\\d{2,3}[AB]?"

        /** Atmo Aura publishes air quality for the Auvergne-Rhône-Alpes departments only. */
        private val ATMO_AURA_DEPARTMENTS = setOf(
            "Auvergne-Rhône-Alpes",
            "01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74"
        )

        private fun isAtmoAuraDepartment(province: String?): Boolean =
            !province.isNullOrEmpty() && province in ATMO_AURA_DEPARTMENTS
    }
}
