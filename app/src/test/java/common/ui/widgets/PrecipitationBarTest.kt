package common.ui.widgets

import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.models.weather.Minutely
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.common.ui.widgets.PrecipitationBar
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The chart marks each :00 / :30 clock minute with a dashed vertical guide + its time. The pick
 * ("which slots") is the risky part — it runs off each sample's wall-clock minute, not its index,
 * so a window that doesn't start on the hour still lands its marks on the real half-hours.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class PrecipitationBarTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.GeometricWeatherTheme
    )

    private fun bar() = PrecipitationBar(context)

    private fun markIndices(bar: PrecipitationBar): IntArray =
        PrecipitationBar::class.java.getDeclaredField("mMarkIndices")
            .also { it.isAccessible = true }
            .get(bar) as IntArray

    private fun markLabels(bar: PrecipitationBar): Array<String> =
        @Suppress("UNCHECKED_CAST")
        (PrecipitationBar::class.java.getDeclaredField("mMarkLabels")
            .also { it.isAccessible = true }
            .get(bar) as Array<String>)

    /** One sample per minute for [count] minutes starting at the given wall clock, in [tz]. */
    private fun minuteList(tz: TimeZone, startHour: Int, startMinute: Int, count: Int): List<Minutely> {
        val cal = Calendar.getInstance(tz).apply {
            set(2020, Calendar.JANUARY, 1, startHour, startMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val base = cal.timeInMillis
        return (0 until count).map { i ->
            Minutely(Date(base + i * 60_000L), 0, true, "rain", WeatherCode.RAIN, 1, null, null, null)
        }
    }

    @Test
    fun `marks the 00 and 30 minute slots even when the window starts off the hour`() {
        val tz = TimeZone.getTimeZone("UTC")
        val prevTz = TimeZone.getDefault()
        val prevLocale = Locale.getDefault()
        TimeZone.setDefault(tz)
        // Pin the locale too: the label formatter uses Locale.getDefault(), and a non-Latin
        // numbering locale (ar/fa) would emit non-ASCII digits the `:(00|30)$` regex can't match.
        Locale.setDefault(Locale.US)
        try {
            // 13:17 for 90 minutes → :30 at i=13 (13:30), :00 at i=43 (14:00), :30 at i=73 (14:30).
            val bar = bar().apply { setMinutelyList(minuteList(tz, 13, 17, 90)) }

            assertEquals(listOf(13, 43, 73), markIndices(bar).toList())
            val labels = markLabels(bar)
            assertEquals(3, labels.size)
            // Every label names a real half-hour, whatever the device 12/24h setting.
            labels.forEach { assertTrue("`$it` is not on a half hour", Regex(":(00|30)$").containsMatchIn(it)) }
        } finally {
            TimeZone.setDefault(prevTz)
            Locale.setDefault(prevLocale)
        }
    }

    @Test
    fun `an empty window draws no marks`() {
        val bar = bar().apply { setMinutelyList(emptyList()) }
        assertEquals(0, markIndices(bar).size)
        assertEquals(0, markLabels(bar).size)
    }
}
