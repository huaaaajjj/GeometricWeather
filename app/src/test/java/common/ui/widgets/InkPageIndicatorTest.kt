package common.ui.widgets

import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.R
import wangdaye.com.geometricweather.common.ui.widgets.InkPageIndicator

/**
 * The alert card drives these dots directly — `setPageCount` plus `onPageSelected` — instead of
 * handing over a `SwipeSwitchLayout` the way the location switcher does. Without an owner to ask,
 * the indicator has to remember which page it is on itself: it re-reads that on every measure and
 * at the end of every page-change animation, and used to answer "the first one" there, which
 * dragged the filled dot back to page 1 the moment the swipe settled.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class InkPageIndicatorTest {

    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.GeometricWeatherTheme
    )

    /** No getter for it, and none is worth adding just to be able to look. */
    private val InkPageIndicator.currentPage: Int
        get() = InkPageIndicator::class.java
            .getDeclaredField("mCurrentPage")
            .also { it.isAccessible = true }
            .getInt(this)

    private fun indicator(pages: Int) = InkPageIndicator(context).apply {
        // It only animates a page change while attached; this is the callback it registers on
        // itself in its constructor, so calling it is exactly what a real attach does.
        onViewAttachedToWindow(this)
        setPageCount(pages)
        measureAndLayout()
    }

    private fun View.measureAndLayout() {
        // Without this the second call is a no-op: View caches the result when the specs match and
        // nothing asked for a layout, and it is onMeasure that re-reads the current page.
        requestLayout()
        measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test
    fun `a fresh page count starts on the first page`() {
        assertEquals(0, indicator(4).currentPage)
    }

    @Test
    fun `the selected page survives a re-measure`() {
        val indicator = indicator(4)

        indicator.onPageSelected(2)
        assertEquals(2, indicator.currentPage)

        // Anything that re-lays out the header block runs this, as does the end of the dot's own
        // move animation: either way the indicator must still be on page 3 of 4.
        indicator.measureAndLayout()
        assertEquals(2, indicator.currentPage)
    }

    @Test
    fun `a new page count puts it back on the first page`() {
        val indicator = indicator(4)
        indicator.onPageSelected(3)

        // Another location's alerts: the pager shows its first one, so the dots must agree.
        indicator.setPageCount(2)
        indicator.measureAndLayout()
        assertEquals(0, indicator.currentPage)
    }
}
