package wangdaye.com.geometricweather.weather.services

import android.content.Context
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.BuildConfig
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.weather.apis.ApihzApi
import wangdaye.com.geometricweather.weather.converters.ApihzResultConverter
import wangdaye.com.geometricweather.weather.json.apihz.ApihzWeatherResult
import java.util.TimeZone
import javax.inject.Inject

/**
 * apihz.cn (中国天气网) weather service.
 *
 * The by-place endpoint (tqyb.php, province + place) is the primary path, so a saved city gets its
 * own weather; the by-IP endpoint (tqybip.php) is only a fallback for a location whose place the
 * API does not recognise. Both return the same shape.
 *
 * Name handling comes from the API's quirks: a trailing 区 on a place, or 市 on a municipality
 * province, makes the lookup fail, while a province-agnostic place lookup is the most tolerant.
 * District coverage is partial (海淀 resolves, 天河 and 渝中 do not), so [fetchForLocation] walks
 * district -> city, each with and without the province, before falling back to IP. Search uses the
 * same place-only lookup, which is why Chinese city and district names resolve directly.
 */
class ApihzWeatherService @Inject constructor(
    private val api: ApihzApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        // A domestic-only source has nothing to say about a place abroad, and its two lookups both
        // answer the wrong place rather than nothing: a Chinese village shares the name 东京 with
        // Tokyo, and the IP endpoint answers "where this request came from" (Beijing for a foreign
        // IP). An answer that looks valid is worse than none — the composite hands the daily block,
        // sunrise and sunset included, to whoever answered.
        if (!location.isChina) {
            callback.requestWeatherFailed(location)
            return
        }
        requests.launch {
            val result = fetchForLocation(location)
            val weather = if (result.usable()) {
                ApihzResultConverter.convert(context, location, result)
            } else {
                null
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

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        // Keep the location's identity as-is; requestWeather resolves it by province/place.
        callback.requestLocationSuccess(location.getCityName(context), listOf(location))
    }

    override fun cancel() = requests.cancel()

    // ---- fetching ----

    private fun fetchForLocation(location: Location): ApihzWeatherResult? {
        val province = normaliseProvince(location.province)
        // The more specific district first, then the city: an unknown district must fall back to the
        // city before IP, since a carrier's egress can sit in another province entirely.
        val places = listOf(normalisePlace(location.district), normalisePlace(location.city))
            .filter { it.isNotEmpty() }
            .distinct()

        for (place in places) {
            if (province.isNotEmpty()) {
                tryPlace(province, place).let { if (it.usable()) return it }
            }
            tryPlace(null, place).let { if (it.usable()) return it }
        }
        return tryIp()
    }

    private fun tryPlace(province: String?, place: String): ApihzWeatherResult? = requests.execute(
        api.getWeatherByPlace(
            BuildConfig.APIHZ_ID, BuildConfig.APIHZ_KEY, province, place,
            FORECAST_DAYS, HOURLY_PERIODS, SUN_TIMES
        )
    )

    private fun tryIp(): ApihzWeatherResult? = requests.execute(
        api.getWeatherByIp(
            BuildConfig.APIHZ_ID, BuildConfig.APIHZ_KEY,
            FORECAST_DAYS, HOURLY_PERIODS, SUN_TIMES
        )
    )

    /** The API answers an unknown place with HTTP 200 and a non-200 code in the body. */
    private fun ApihzWeatherResult?.usable(): Boolean = this != null && code == 200

    // ---- location building / name normalisation ----

    private fun ApihzWeatherResult.toLocation(): Location {
        val country = guo?.takeIf { it.isNotEmpty() } ?: "中国"
        val province = sheng ?: ""
        val city = shi?.takeIf { it.isNotEmpty() } ?: name.orEmpty()
        val isChina = country == "中国"
        return Location(
            (province + city).ifEmpty { city }, // cityId
            lat.toFloatOrZero(),
            lon.toFloatOrZero(),
            if (isChina) CN_TIME_ZONE else TimeZone.getDefault(),
            country,
            province,
            city,
            "",
            null,
            WeatherSource.APIHZ,
            false,
            false,
            isChina
        )
    }

    companion object {
        private val CN_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

        private const val FORECAST_DAYS = 7
        private const val HOURLY_PERIODS = 1 // 3-hourly
        private const val SUN_TIMES = 1

        /** A trailing 区 breaks the place lookup ("海淀区" -> 400, "海淀" -> ok). */
        private fun normalisePlace(value: String): String =
            value.trim().removeSuffix("区")

        /**
         * A municipality province with 市 breaks the lookup ("北京市" -> 400, "北京" -> ok);
         * 省/自治区 are accepted as-is.
         */
        private fun normaliseProvince(value: String): String =
            value.trim().removeSuffix("市")

        private fun String?.toFloatOrZero(): Float =
            this?.trim()?.toFloatOrNull() ?: 0f
    }
}
