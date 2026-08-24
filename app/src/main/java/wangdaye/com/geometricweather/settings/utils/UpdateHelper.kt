package wangdaye.com.geometricweather.settings.utils

import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub whether this fork has published a newer release than the running build.
 *
 * No Retrofit interface and no Gson DTO on purpose: this is one call with two fields, it belongs to
 * no weather provider, and a DTO here would need its own `-keep` rule to survive R8 (the existing one
 * only covers `weather.json.**`). [JSONObject] reads the two fields reflection-free.
 */
object UpdateHelper {

    /** The fork's own releases — the upstream project does not ship this build. */
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/huaaaajjj/GeometricWeather/releases/latest"

    /**
     * The newest release of any kind. `releases/latest` skips prereleases by design, and this fork
     * publishes its daily builds *as* prereleases, so that channel has to read the list instead —
     * it comes back newest-first, and `per_page=1` keeps it to the single entry we compare against.
     */
    private const val NEWEST_RELEASE_API =
        "https://api.github.com/repos/huaaaajjj/GeometricWeather/releases?per_page=1"

    const val RELEASES_PAGE = "https://github.com/huaaaajjj/GeometricWeather/releases/latest"

    class Latest(val version: String, val url: String)

    /**
     * Null on any failure — no network, rate limit, no release yet, malformed answer.
     *
     * @param prerelease include prereleases, i.e. report the newest release whatever its kind.
     */
    fun fetchLatest(prerelease: Boolean): Latest? {
        var connection: HttpURLConnection? = null
        return try {
            val api = if (prerelease) NEWEST_RELEASE_API else LATEST_RELEASE_API
            connection = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // GitHub answers 403 to requests without one.
                setRequestProperty("User-Agent", "GeometricWeather")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (ignored: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Reads the tag out of either shape GitHub answers with: `releases/latest` is one object,
     * `releases` is an array. Sniffing the shape rather than taking a flag keeps the two impossible
     * to desync — no caller can ask for the list and then read it as an object.
     */
    @VisibleForTesting
    fun parse(body: String): Latest? {
        val json = if (body.trimStart().startsWith("[")) {
            JSONArray(body).optJSONObject(0)
        } else {
            JSONObject(body)
        } ?: return null

        val tag = json.optString("tag_name")
        return if (tag.isEmpty()) {
            null
        } else {
            Latest(
                version = normalize(tag),
                url = json.optString("html_url").ifEmpty { RELEASES_PAGE }
            )
        }
    }

    /**
     * Compares dotted versions numerically, which is the whole point: 3.5.9 and 3.5.12 order the
     * wrong way round as strings, and that is exactly the pair this app ships. Accepts a `v` prefix
     * (git tags carry one) and a flavour suffix (`BuildConfig.VERSION_NAME` is "3.5.12_pub").
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty()) {
            return false
        }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) {
                return x > y
            }
        }
        return false
    }

    fun normalize(version: String) = version.trim().removePrefix("v").removePrefix("V")

    private fun parts(version: String) = normalize(version)
        .substringBefore('_')
        .substringBefore('-')
        .split('.')
        .mapNotNull { it.trim().toIntOrNull() }

    private const val TIMEOUT_MS = 10_000
}
