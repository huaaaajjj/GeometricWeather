package wangdaye.com.geometricweather.weather.services

import android.content.Context
import android.os.Build
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.weather.apis.CmaApi
import wangdaye.com.geometricweather.weather.converters.CmaResultConverter
import wangdaye.com.geometricweather.weather.json.cma.CmaWeatherResult
import java.util.TimeZone
import javax.inject.Inject

/**
 * China Meteorological Administration (weather.cma.cn) weather service.
 *
 * Station-ID based (cityId = CMA stationId). No API key. Notes:
 * - The autocomplete search matches pinyin/English, so Chinese queries are transliterated first.
 * - CMA has no coordinates->station endpoint, so a current position — or a location re-sourced from
 *   another provider, whose cityId is not a CMA station — is resolved to the geographically nearest
 *   station via the national station map. IP-based resolution is only a last resort, because a
 *   mobile IP can be far from the user (a carrier egress in another province).
 * - An unknown station id comes back as `"data": ""` — an empty *string* where an object belongs —
 *   so decoding throws. That failure must stay contained, or the retry below never runs.
 */
class CmaWeatherService @Inject constructor(
    private val api: CmaApi
) : WeatherService() {

    private val requests = RequestScope()

    override fun requestWeather(
        context: Context,
        location: Location,
        callback: RequestWeatherCallback
    ) {
        requests.launch {
            var result = getWeather(location.cityId)

            // The stored cityId may not be a CMA station. Resolve a real one and ask again.
            if (!result.usable()) {
                val stationId = resolveStation(context, location)?.cityId
                if (!stationId.isNullOrEmpty() && stationId != location.cityId) {
                    result = getWeather(stationId)
                }
            }

            val weather = if (result.usable()) {
                // Scrape the hourly page for the station the data actually came from.
                val stationId = result!!.data.location?.id?.takeIf { it.isNotEmpty() }
                    ?: location.cityId
                CmaResultConverter.convert(context, location, result, getHourlyHtml(stationId))
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

    override fun requestLocation(context: Context, query: String): List<Location> {
        val pinyin = toPinyinQuery(query)
        if (pinyin.isEmpty()) {
            return emptyList()
        }
        val result = requests.execute(api.getLocation(pinyin, SEARCH_RESULTS))
        return result?.data.orEmpty().mapNotNull { entry ->
            // The autocomplete entry carries only the station id, so the placeholder coordinates
            // are (0, 0) — and any coordinate-based provider the location is later switched to
            // (Open-Meteo, Xiaomi, OWM...) would quietly fetch weather for the Gulf of Guinea
            // while the CMA readout for the same place looked perfectly correct. The station's
            // own weather/view answers with real coordinates, so every search result is
            // re-anchored before it is offered; one small call per result on a low-frequency
            // screen, and a failed view keeps the placeholder rather than dropping the result.
            parseSearchEntry(entry)?.let { location ->
                getWeather(location.cityId)?.data?.location?.let { buildLocationFromView(it) } ?: location
            }
        }
    }

    override fun requestLocation(
        context: Context,
        location: Location,
        callback: RequestLocationCallback
    ) {
        requests.launch {
            val resolved = resolveStation(context, location)

            if (!isActive) {
                return@launch
            }
            if (resolved != null) {
                callback.requestLocationSuccess(location.formattedId, listOf(resolved))
            } else {
                callback.requestLocationFailed(location.formattedId)
            }
        }
    }

    override fun cancel() = requests.cancel()

    // ---- fetching ----

    /** Decoding throws for an unknown station id, so failure is contained rather than propagated. */
    private fun getWeather(stationId: String): CmaWeatherResult? =
        requests.execute(api.getWeather(stationId))

    /** Best effort: without the page there is simply no hourly strip. */
    private fun getHourlyHtml(stationId: String): String? = try {
        requests.execute(api.getHourlyHtml(stationId))?.string()
    } catch (e: Exception) {
        null
    }

    private fun CmaWeatherResult?.usable(): Boolean =
        this != null && data != null && !data.daily.isNullOrEmpty()

    // ---- station resolution ----

    /**
     * Nearest station by GPS first (most reliable for a current position), else the place name via
     * pinyin autocomplete, else CMA's own IP-based resolution. Null when all three fail.
     */
    private fun resolveStation(context: Context, location: Location): Location? {
        findNearestStation(location.latitude, location.longitude)?.let {
            return it.toLocation(location.latitude, location.longitude)
        }

        val name = listOf(location.district, location.city, location.province)
            .firstOrNull { it.isNotEmpty() }
        if (!name.isNullOrEmpty()) {
            requestLocation(context, name).firstOrNull()?.let { return it }
        }

        // Last resort: an empty station id makes CMA pick one from the caller's IP.
        val byIp = getWeather("")
        return byIp?.data?.location?.let { buildLocationFromView(it) }
    }

    private fun findNearestStation(latitude: Float, longitude: Float): Station? {
        // Ignore the (0,0) placeholder coordinates search results carry.
        if (Math.abs(latitude) < COORDINATE_EPSILON && Math.abs(longitude) < COORDINATE_EPSILON) {
            return null
        }
        // Squared degrees: only the ordering matters, and it is a national list.
        return loadStations().minByOrNull { station ->
            val dLat = station.latitude - latitude
            val dLon = station.longitude - longitude
            dLat * dLat + dLon * dLon
        }
    }

    private fun loadStations(): List<Station> {
        stations?.let { return it }
        synchronized(STATIONS_LOCK) {
            stations?.let { return it }

            val result = requests.execute(api.getNationalMap(TODAY, System.currentTimeMillis()))
            val loaded = result?.data?.city.orEmpty().mapNotNull { row ->
                try {
                    if (row == null || row.size < STATION_ROW_FIELDS) {
                        null
                    } else {
                        val id = row[0].asString
                        if (id.isNullOrEmpty()) {
                            null
                        } else {
                            Station(id, row[1].asString, row[4].asDouble, row[5].asDouble)
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            // Only cache a real list; a failed fetch must be retried on the next request.
            if (loaded.isNotEmpty()) {
                stations = loaded
            }
            return loaded
        }
    }

    private class Station(
        val id: String,
        val name: String?,
        val latitude: Double,
        val longitude: Double
    ) {
        fun toLocation(latitude: Float, longitude: Float) = Location(
            id,
            latitude,
            longitude,
            CN_TIME_ZONE,
            "中国",
            "",
            name.orEmpty(),
            "",
            null,
            WeatherSource.CMA,
            false,
            false,
            true
        )
    }

    companion object {
        private val CN_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

        private const val SEARCH_RESULTS = 20
        private const val TODAY = 1
        private const val STATION_ROW_FIELDS = 6
        private const val COORDINATE_EPSILON = 0.01f

        // The national list is ~2400 entries and never changes within a run, so it is fetched once
        // per process rather than per resolution.
        private val STATIONS_LOCK = Any()

        @Volatile
        private var stations: List<Station>? = null

        /** "stationId|中文名|英文名|国家" -> Location (cityId = stationId). */
        private fun parseSearchEntry(entry: String?): Location? {
            if (entry.isNullOrEmpty()) {
                return null
            }
            val parts = entry.split("|")
            val stationId = parts.getOrNull(0)
            if (parts.size < 2 || stationId.isNullOrEmpty()) {
                return null
            }
            val country = parts.getOrElse(3) { "" }
            val isChina = country == "中国"
            return Location(
                stationId,
                0f,
                0f,
                if (isChina) CN_TIME_ZONE else TimeZone.getDefault(),
                country,
                "",
                parts.getOrElse(1) { "" },
                "",
                null,
                WeatherSource.CMA,
                false,
                false,
                isChina
            )
        }

        /** The weather/view location block, used for the IP-based fallback. */
        private fun buildLocationFromView(view: CmaWeatherResult.Location): Location {
            val path = view.path?.split(",")?.map { it.trim() }.orEmpty()
            val country = path.getOrElse(0) { "" }
            val province = path.getOrElse(1) { "" }
            val city = path.getOrElse(2) { view.name.orEmpty() }
            val isChina = country == "中国"

            return Location(
                view.id.orEmpty(),
                view.latitude?.toFloat() ?: 0f,
                view.longitude?.toFloat() ?: 0f,
                view.timezone?.let { TimeZone.getTimeZone(String.format("GMT%+d", it)) }
                    ?: if (isChina) CN_TIME_ZONE else TimeZone.getDefault(),
                country,
                province,
                city,
                "",
                null,
                WeatherSource.CMA,
                false,
                false,
                isChina
            )
        }

        /** CMA autocomplete matches pinyin/English, so Chinese is transliterated first (API 24+). */
        private fun toPinyinQuery(input: String?): String {
            val trimmed = input?.trim().orEmpty()
            if (trimmed.isEmpty() || !containsHan(trimmed)) {
                return trimmed
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val transliterated = android.icu.text.Transliterator
                        .getInstance("Han-Latin/Names; Latin-ASCII; Lower")
                        .transliterate(trimmed)
                    // Keep only latin letters so "bei jing" matches the station name "beijing".
                    val letters = transliterated.replace(NON_LETTERS, "")
                    if (letters.isNotEmpty()) {
                        return letters.lowercase()
                    }
                } catch (e: Throwable) {
                    // Fall through to the original text.
                }
            }
            return trimmed
        }

        private val NON_LETTERS = "[^a-zA-Z]".toRegex()

        private fun containsHan(value: String): Boolean =
            value.any { it in '一'..'鿿' }
    }
}
