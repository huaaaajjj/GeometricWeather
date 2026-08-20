package wangdaye.com.geometricweather.settings.utils

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

    const val RELEASES_PAGE = "https://github.com/huaaaajjj/GeometricWeather/releases/latest"

    class Latest(val version: String, val url: String)

    /** Null on any failure — no network, rate limit, no release yet, malformed answer. */
    fun fetchLatest(): Latest? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
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
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (tag.isEmpty()) {
                null
            } else {
                Latest(
                    version = normalize(tag),
                    url = json.optString("html_url").ifEmpty { RELEASES_PAGE }
                )
            }
        } catch (ignored: Exception) {
            null
        } finally {
            connection?.disconnect()
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
