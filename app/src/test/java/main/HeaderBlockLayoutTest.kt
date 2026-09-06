package main

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.main.adapters.main.holder.HeaderViewHolder
import wangdaye.com.geometricweather.theme.weatherView.materialWeatherView.MaterialWeatherView

/**
 * The header block is the first list item and the card list starts where it ends, so its measured
 * height *is* the y the first card opens at. The alert and minutely cards hang at the bottom of that
 * block, in the space the temperature text does not use, precisely so the first card keeps that y
 * whether or not there is an alert to show — that is what is locked here.
 *
 * Measured the way `RecyclerView` measures a `wrap_content` item in a vertical list: width EXACTLY,
 * height UNSPECIFIED. `HeaderViewHolder` supplies the minimum height at bind time.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class HeaderBlockLayoutTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.GeometricWeatherTheme
    )

    private val width = 1080

    /** Stands in for HeaderViewHolder's own adapter: same page layout, one page per alert. */
    private class Pages(private val count: Int) : RecyclerView.Adapter<Pages.Page>() {

        class Page(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Page(
            LayoutInflater.from(parent.context).inflate(R.layout.item_main_alert, parent, false)
        )

        override fun onBindViewHolder(holder: Page, position: Int) {
            holder.itemView.findViewById<TextView>(R.id.item_main_alert_description).text =
                "台江发布地质灾害气象风险橙色预警 $position"
            holder.itemView.findViewById<TextView>(R.id.item_main_alert_date).text =
                "2026年9月3日 17:37:00"
        }

        override fun getItemCount() = count
    }

    private fun headerBlock(
        minHeight: Int,
        alert: Boolean,
        minutely: Boolean,
        alertCount: Int = 3,
    ): LinearLayout {
        val header = LayoutInflater
            .from(context)
            .inflate(R.layout.container_main_header, null) as LinearLayout

        header.minimumHeight = minHeight
        header.findViewById<View>(R.id.container_main_alert).visibility =
            if (alert) View.VISIBLE else View.GONE
        header.findViewById<View>(R.id.container_main_minutely).visibility =
            if (minutely) View.VISIBLE else View.GONE

        val pager = header.findViewById<RecyclerView>(R.id.container_main_alert_pager)
        pager.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        pager.adapter = Pages(alertCount)

        header.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
        return header
    }

    private fun alertCard(alertCount: Int): View = headerBlock(
        minHeight = 0,
        alert = true,
        minutely = false,
        alertCount = alertCount,
    ).findViewById(R.id.container_main_alert)

    @Test
    fun `with nothing to warn about the block is exactly the header height`() {
        assertEquals(1200, headerBlock(1200, alert = false, minutely = false).measuredHeight)
    }

    @Test
    fun `an alert does not push the first card down`() {
        val header = headerBlock(1200, alert = true, minutely = false)
        assertEquals(1200, header.measuredHeight)

        // Hanging at the bottom of the block, not sitting right under the temperature text.
        val alert = header.findViewById<View>(R.id.container_main_alert)
        assertEquals(1200, alert.bottom)
        assertTrue(
            "alert should hang in the lower half, was ${alert.top}",
            alert.top > 1200 / 2
        )
    }

    @Test
    fun `both cards at once do not push the first card down either`() {
        val header = headerBlock(1200, alert = true, minutely = true)
        assertEquals(1200, header.measuredHeight)

        val alert = header.findViewById<View>(R.id.container_main_alert)
        val minutely = header.findViewById<View>(R.id.container_main_minutely)
        assertEquals(1200, minutely.bottom)
        assertTrue("alert should sit above minutely", alert.bottom <= minutely.top)
    }

    @Test
    fun `when the cards genuinely do not fit the block grows instead of clipping them`() {
        val header = headerBlock(200, alert = true, minutely = true)
        assertTrue("block should grow past 200, was ${header.measuredHeight}", header.measuredHeight > 200)

        // Nothing hangs outside the block, so no card gets cut off.
        val minutely = header.findViewById<View>(R.id.container_main_minutely)
        assertEquals(header.measuredHeight, minutely.bottom)
    }

    /**
     * The card lives in the header's empty space, so its height may not follow the alert count —
     * a place with several alerts would otherwise take the block past the header height and push
     * the first card off the first screen. One alert is on show at a time; the others are a swipe
     * away, which is also why every page has to be the same height.
     */
    @Test
    fun `the alert card is one page tall whatever the alert count`() {
        val one = alertCard(1).measuredHeight
        assertEquals("three alerts must not make the card taller", one, alertCard(3).measuredHeight)
        assertEquals("nine either", one, alertCard(9).measuredHeight)
    }

    /** A page as wide as the pager is what makes it page: exactly one alert stops on screen. */
    @Test
    fun `each alert fills the width so only one is on screen`() {
        val pager = alertCard(3).findViewById<RecyclerView>(R.id.container_main_alert_pager)
        val page = pager.getChildAt(0)

        assertTrue("the pager should have laid out a page", page != null)
        assertEquals(pager.measuredWidth, page.measuredWidth)
        assertEquals(0, page.left)
    }

    /** Builds a real holder (the pin lives on it), laid out the way the existing harness does. */
    private fun pinnedHolder(minHeight: Int, alert: Boolean, minutely: Boolean): HeaderViewHolder {
        val holder = HeaderViewHolder(
            FrameLayout(context),
            MaterialWeatherView(context)
        )
        val header = holder.itemView as LinearLayout
        header.minimumHeight = minHeight
        header.findViewById<View>(R.id.container_main_alert).visibility =
            if (alert) View.VISIBLE else View.GONE
        header.findViewById<View>(R.id.container_main_minutely).visibility =
            if (minutely) View.VISIBLE else View.GONE

        val pager = header.findViewById<RecyclerView>(R.id.container_main_alert_pager)
        pager.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        pager.adapter = Pages(3)

        header.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
        return holder
    }

    /**
     * The text block pins while the list scrolls: its translation grows with the scroll so it
     * holds still on screen, and once the card list reaches its bottom the translation freezes at
     * exactly that distance — the block being part of the same item then simply scrolls away with
     * the list, glued to the card that touched it.
     */
    @Test
    fun `the text block pins until the card list reaches it`() {
        val holder = pinnedHolder(minHeight = 1200, alert = false, minutely = false)
        val textBlock = holder.itemView.findViewById<View>(R.id.container_main_header_textBlock)

        val distance = holder.getTextPinDistance()
        assertEquals(1200 - textBlock.bottom, distance)

        holder.pinTextBlock(100)
        assertEquals("pinning grows with the scroll", 100f, textBlock.translationY)
        holder.pinTextBlock(distance + 1000)
        assertEquals("past the collision the translation freezes", distance.toFloat(), textBlock.translationY)
    }

    /** Whichever card comes first defines the collision: an alert card sits closer than the card
     *  list, so it must shorten the pin distance rather than slide over the pinned text. */
    @Test
    fun `an alert card reaches the text block before the card list does`() {
        val holder = pinnedHolder(minHeight = 1200, alert = true, minutely = false)
        val textBlock = holder.itemView.findViewById<View>(R.id.container_main_header_textBlock)
        val alert = holder.itemView.findViewById<View>(R.id.container_main_alert)

        assertEquals(alert.top - textBlock.bottom, holder.getTextPinDistance())
        assertTrue(
            "the alert card must shorten the pin distance",
            holder.getTextPinDistance() < 1200 - textBlock.bottom
        )
    }
}
