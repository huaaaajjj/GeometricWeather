package wangdaye.com.geometricweather.search

import android.content.Context
import kotlinx.coroutines.isActive
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.utils.LanguageUtils
import wangdaye.com.geometricweather.common.utils.helpers.AsyncHelper
import wangdaye.com.geometricweather.db.DatabaseHelper
import wangdaye.com.geometricweather.settings.SettingsManager
import wangdaye.com.geometricweather.weather.apis.OpenMeteoGeocodingApi
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoGeocodingResult
import wangdaye.com.geometricweather.weather.services.RequestScope
import java.util.TimeZone
import javax.inject.Inject

/**
 * Place search, decoupled from the weather source.
 *
 * A search answers one question — *where is this name* — and returns a name plus a coordinate. The
 * forecast for it then comes from whatever source is selected in the settings, which is why every
 * result is stamped with that source before it leaves here. Nine of the eleven sources are
 * coordinate-driven anyway, and the two that are not resolve themselves from one: CMA picks its
 * nearest station from the national station map, APIHZ walks its district → city → IP ladder.
 *
 * This replaces asking the weather sources to search. That never worked from a standing start:
 * five of them — including COMPOSITE and the default WEATHERAPI — return an empty list for a text
 * query, so the screen came up empty until the user ticked some *other* provider by hand, and
 * whatever they ticked became the added location's source (a location is keyed
 * `cityId & weatherSource`), stranding it on a source the rest of the app was not using.
 *
 * Two tiers, in this order, because neither covers the other's ground:
 *
 * 1. **The bundled Chinese city table** for a Chinese query — 3216 prefectures and districts,
 *    offline, with exact coordinates and the province/city/district names APIHZ wants.
 * 2. **Open-Meteo's geocoder** otherwise, or when the table has nothing.
 *
 * The order is measured, not assumed: queried in Chinese the geocoder finds nothing at all for
 * 舒城, and ranks 长沙 behind three villages of the same name in other provinces. Queried in Latin
 * script it is good (Changsha, Tokyo, Paris all come first). So the table leads for Chinese and the
 * geocoder covers the rest of the world — and a Chinese query that misses the table still falls
 * through to it rather than dead-ending.
 */
class LocationSearchHelper @Inject constructor(
    private val api: OpenMeteoGeocodingApi
) {

    interface Callback {
        fun searchSucceeded(query: String, locationList: List<Location>)
        fun searchFailed(query: String)
    }

    private val requests = RequestScope()

    fun search(context: Context, query: String, callback: Callback) {
        val trimmed = query.trim()
        // The source is read here, on the caller's thread, so a settings change mid-search cannot
        // hand back results stamped half one way and half the other.
        val source = SettingsManager.getInstance(context).weatherSource

        requests.launch {
            // A tier, not a union: a hit in the table is the answer, and the geocoder is neither
            // asked nor allowed to mix its same-named villages into it.
            val found = fromCityTable(context, trimmed)
                .ifEmpty { geocode(context, trimmed) }
                .map { it.copy(weatherSource = source) }
                // The list adapter diffs on formattedId; two rows sharing one would confuse it.
                .distinctBy { it.formattedId }

            // Nothing below this point may reach the caller once cancel() has been called.
            if (!isActive) {
                return@launch
            }
            // Hop back to the main thread: the caller sets LiveData from these callbacks, and
            // LiveData.setValue() throws when it is called off the main thread.
            AsyncHelper.delayRunOnUI({
                if (found.isEmpty()) {
                    callback.searchFailed(query)
                } else {
                    callback.searchSucceeded(query, found)
                }
            }, 0)
        }
    }

    fun cancel() = requests.cancel()

    /** The offline table. Chinese queries only — it holds nothing else. */
    private fun fromCityTable(context: Context, query: String): List<Location> {
        if (!LanguageUtils.isChinese(query)) {
            return emptyList()
        }
        val database = DatabaseHelper.getInstance(context)
        database.ensureChineseCityList(context)
        return database.readChineseCityList(query).map { it.toLocation() }
    }

    private fun geocode(context: Context, query: String): List<Location> {
        if (query.isEmpty()) {
            return emptyList()
        }
        val result = requests.execute(
            api.getLocations(query, SEARCH_RESULTS, geocodingLanguage(context), "json")
        )
        return result?.results.orEmpty().mapNotNull { convert(it) }
    }

    /**
     * The endpoint takes a bare two-letter language and quietly ignores anything else, answering in
     * the local script instead — so "zh-tw" would silently cost the Chinese place names.
     */
    private fun geocodingLanguage(context: Context) =
        SettingsManager.getInstance(context).language.code.substringBefore('-')

    companion object {

        private const val SEARCH_RESULTS = 20

        private val CHINA_COUNTRY_CODES = setOf("CN", "HK", "TW", "MO")

        private fun convert(result: OpenMeteoGeocodingResult.Result): Location? {
            val latitude = result.latitude ?: return null
            val longitude = result.longitude ?: return null

            // The place's own name is the most specific thing the response has, so it takes the
            // district slot — unless the prefecture is already that same name, which is how a
            // prefecture-level hit avoids being printed twice.
            val city = result.admin2.orEmpty().ifEmpty { result.name.orEmpty() }
            val district = result.name.orEmpty().takeIf { it != city }.orEmpty()

            return Location(
                cityId = "$latitude,$longitude",
                latitude = latitude.toFloat(),
                longitude = longitude.toFloat(),
                timeZone = zoneOf(result.timezone),
                country = result.country.orEmpty(),
                province = result.admin1.orEmpty(),
                city = city,
                district = district,
                weather = null,
                weatherSource = WeatherSource.OPEN_METEO,
                isCurrentPosition = false,
                isResidentPosition = false,
                isChina = result.countryCode?.uppercase() in CHINA_COUNTRY_CODES
            )
        }

        /**
         * [TimeZone.getTimeZone] answers GMT for an id it does not know instead of failing, and a
         * silently wrong zone moves every sunrise, day boundary and hourly bucket. Compare the id
         * back to tell a real match from that fallback.
         */
        private fun zoneOf(id: String?): TimeZone {
            if (id.isNullOrEmpty()) {
                return TimeZone.getDefault()
            }
            val zone = TimeZone.getTimeZone(id)
            return if (zone.id == id) zone else TimeZone.getDefault()
        }
    }
}
