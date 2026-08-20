package settings.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wangdaye.com.geometricweather.settings.utils.UpdateHelper

/**
 * The update check compares dotted versions, and string order is wrong for exactly the pair this app
 * ships: "3.5.9" > "3.5.12" lexicographically. It also has to survive the two decorations the real
 * inputs carry — a `v` on the git tag and the flavour suffix on `BuildConfig.VERSION_NAME`.
 */
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
}
