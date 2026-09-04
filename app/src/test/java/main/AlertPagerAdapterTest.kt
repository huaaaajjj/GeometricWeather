package main

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.basic.models.weather.Alert
import wangdaye.com.geometricweather.main.adapters.main.holder.HeaderViewHolder.AlertPagerAdapter
import java.util.Date

/**
 * The alert card pages sideways and has to wrap: past the last alert comes the first one again. That
 * is done by repeating the list, so what matters is that a page still shows the alert its position
 * stands for however far the pager has travelled, and that it starts far enough in to swipe back.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class AlertPagerAdapterTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.GeometricWeatherTheme
    )

    private fun alerts(count: Int) = (0 until count).map {
        Alert(it.toLong(), Date(1_756_000_000_000L), 1_756_000_000_000L, "alert $it", "", "type $it", 1, 0)
    }

    private fun adapter(count: Int) = AlertPagerAdapter(alerts(count), 0, 0, null)

    /** What the page at this position actually says. */
    private fun AlertPagerAdapter.headlineAt(position: Int): String {
        val holder = onCreateViewHolder(FrameLayout(context), 0)
        onBindViewHolder(holder, position)
        return holder.itemView.findViewById<TextView>(R.id.item_main_alert_description).text.toString()
    }

    @Test
    fun `the pages run on well past the last alert`() {
        val adapter = adapter(4)

        assertTrue("should be more pages than alerts, was ${adapter.itemCount}", adapter.itemCount > 4)
        // Whole laps only: the wrap arithmetic reads a position modulo the alert count.
        assertEquals(0, adapter.itemCount % 4)
    }

    @Test
    fun `it opens far enough in to swipe backwards, on the first alert`() {
        val adapter = adapter(4)

        assertTrue("should not open at the very start", adapter.firstPage() > 0)
        assertTrue("should not open at the very end", adapter.firstPage() < adapter.itemCount - 4)
        assertEquals("alert 0", adapter.headlineAt(adapter.firstPage()))
    }

    @Test
    fun `a page shows the alert it stands for, whichever lap it is on`() {
        val adapter = adapter(4)
        val start = adapter.firstPage()

        assertEquals("alert 1", adapter.headlineAt(start + 1))
        assertEquals("alert 3", adapter.headlineAt(start + 3))
        // Round the corner: the page after the last alert is the first one again.
        assertEquals("alert 0", adapter.headlineAt(start + 4))
        // And backwards, off the front of the lap it opened on.
        assertEquals("alert 3", adapter.headlineAt(start - 1))
    }

    @Test
    fun `a lone alert does not loop`() {
        val adapter = adapter(1)

        assertEquals(1, adapter.itemCount)
        assertEquals(0, adapter.firstPage())
    }
}
