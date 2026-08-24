package settings.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.settings.utils.UpdateHelper

/**
 * The update check compares dotted versions, and string order is wrong for exactly the pair this app
 * ships: "3.5.9" > "3.5.12" lexicographically. It also has to survive the two decorations the real
 * inputs carry — a `v` on the git tag and the flavour suffix on `BuildConfig.VERSION_NAME`.
 *
 * Robolectric is here for `parse` only: `org.json` is a stub in android.jar, and with
 * `unitTests.returnDefaultValues` those stubs answer null instead of throwing, so a plain JVM test
 * would "pass" against an empty parser. The runner supplies the real implementation.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class UpdateHelperTest {

    @Test
    fun aLaterPatchIsNewer() {
        assertTrue(UpdateHelper.isNewer("v3.5.13", "3.5.12_pub"))
        // The pair that breaks a string comparison.
        assertTrue(UpdateHelper.isNewer("v3.5.12", "3.5.9_pub"))
        assertFalse(UpdateHelper.isNewer("v3.5.9", "3.5.12_pub"))
    }

    @Test
    fun theSameVersionIsNotNewer() {
        assertFalse(UpdateHelper.isNewer("v3.5.12", "3.5.12_pub"))
        assertFalse(UpdateHelper.isNewer("3.5.12", "3.5.12"))
        assertFalse(UpdateHelper.isNewer("v3.5.12", "3.5.12_gplay"))
        assertFalse(UpdateHelper.isNewer("v3.5.12", "3.5.12_fdroid"))
    }

    @Test
    fun minorAndMajorBumpsCount() {
        assertTrue(UpdateHelper.isNewer("v3.6.0", "3.5.12_pub"))
        assertTrue(UpdateHelper.isNewer("v4.0.0", "3.5.12_pub"))
        assertFalse(UpdateHelper.isNewer("v3.4.99", "3.5.0_pub"))
    }

    /** A shorter version is not automatically older: 3.6 == 3.6.0. */
    @Test
    fun missingComponentsCountAsZero() {
        assertFalse(UpdateHelper.isNewer("v3.6", "3.6.0_pub"))
        assertTrue(UpdateHelper.isNewer("v3.6.1", "3.6_pub"))
        assertFalse(UpdateHelper.isNewer("v3.6", "3.6.1_pub"))
    }

    /** Nonsense from the API must never claim an update. */
    @Test
    fun garbageIsNotAnUpdate() {
        assertFalse(UpdateHelper.isNewer("", "3.5.12_pub"))
        assertFalse(UpdateHelper.isNewer("nightly", "3.5.12_pub"))
        assertFalse(UpdateHelper.isNewer("v", "3.5.12_pub"))
    }

    @Test
    fun normalizeDropsTheTagPrefix() {
        assertTrue(UpdateHelper.normalize("v3.5.12") == "3.5.12")
        assertTrue(UpdateHelper.normalize(" 3.5.12 ") == "3.5.12")
    }

    /** The stable channel: `releases/latest` answers with a single object. */
    @Test
    fun parseReadsTheObjectShape() {
        val latest = UpdateHelper.parse(
            """
            {"tag_name": "v3.6.0", "prerelease": false,
             "html_url": "https://github.com/huaaaajjj/GeometricWeather/releases/tag/v3.6.0"}
            """.trimIndent()
        )
        assertEquals("3.6.0", latest?.version)
        assertEquals(
            "https://github.com/huaaaajjj/GeometricWeather/releases/tag/v3.6.0",
            latest?.url
        )
    }

    /**
     * The prerelease channel: `releases?per_page=1` answers with an array, and a prerelease entry is
     * the whole point of asking that endpoint — it must not be filtered out here.
     */
    @Test
    fun parseReadsTheArrayShape() {
        val latest = UpdateHelper.parse(
            """
            [{"tag_name": "v3.6.1", "prerelease": true,
              "html_url": "https://github.com/huaaaajjj/GeometricWeather/releases/tag/v3.6.1"}]
            """.trimIndent()
        )
        assertEquals("3.6.1", latest?.version)
        assertTrue(latest?.url?.endsWith("/tag/v3.6.1") == true)
    }

    /** GitHub returns the list newest-first, so only the head entry is the candidate. */
    @Test
    fun parseTakesTheFirstArrayEntry() {
        val latest = UpdateHelper.parse(
            """[{"tag_name": "v3.6.1"}, {"tag_name": "v3.9.9"}]"""
        )
        assertEquals("3.6.1", latest?.version)
    }

    /** Leading whitespace must not make an array look like an object. */
    @Test
    fun parseSniffsTheShapePastWhitespace() {
        assertEquals("3.6.1", UpdateHelper.parse("\n  [{\"tag_name\": \"v3.6.1\"}]")?.version)
    }

    /** A repo with no releases yet, and an entry with nothing to compare against. */
    @Test
    fun parseReturnsNullWhenThereIsNoTag() {
        assertNull(UpdateHelper.parse("[]"))
        assertNull(UpdateHelper.parse("{}"))
        assertNull(UpdateHelper.parse("""{"prerelease": true}"""))
        assertNull(UpdateHelper.parse("""[{"prerelease": true}]"""))
    }

    /** No page in the answer still has to lead somewhere downloadable. */
    @Test
    fun parseFallsBackToTheReleasesPage() {
        assertEquals(
            UpdateHelper.RELEASES_PAGE,
            UpdateHelper.parse("""{"tag_name": "v3.6.0"}""")?.url
        )
    }
}
