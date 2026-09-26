package wangdaye.com.geometricweather.main.adapters.main.holder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The minutely nowcast countdown is a live clock, not a frozen index: [HeaderViewHolder.minutesUntil]
 * turns "time left" into whole minutes and [HeaderViewHolder.nextMinuteTickDelay] says when that
 * number next changes. Pins the ceil rounding, the clamp, and — the one that would busy-loop or skip
 * a minute if wrong — the tick delay landing exactly on the boundary.
 */
class HeaderViewHolderCountdownTest {

    @Test
    fun minutesRoundUpAndClamp() {
        assertEquals(35, HeaderViewHolder.minutesUntil(35 * 60000L))       // exact
        assertEquals(35, HeaderViewHolder.minutesUntil(35 * 60000L - 1))   // ceil, not 34
        assertEquals(1, HeaderViewHolder.minutesUntil(1))                  // <1 min still reads 1
        assertEquals(1, HeaderViewHolder.minutesUntil(0))                  // passed → clamp, never 0
        assertEquals(1, HeaderViewHolder.minutesUntil(-5 * 60000L))        // overdue → clamp
    }

    @Test
    fun tickDelayIsAWholeMinuteOnTheBoundary() {
        assertEquals(60000L, HeaderViewHolder.nextMinuteTickDelay(60000L))
        assertEquals(60000L, HeaderViewHolder.nextMinuteTickDelay(35 * 60000L))
        assertEquals(59000L, HeaderViewHolder.nextMinuteTickDelay(35 * 60000L - 1000L))
        assertEquals(1L, HeaderViewHolder.nextMinuteTickDelay(1L))
        assertEquals(1L, HeaderViewHolder.nextMinuteTickDelay(60001L))
    }

    /** After waiting the scheduled delay, the displayed minute must drop by exactly one. */
    @Test
    fun oneTickAdvancesExactlyOneMinute() {
        for (delta in longArrayOf(60001L, 90000L, 35 * 60000L, 2 * 60000L + 137L)) {
            val delay = HeaderViewHolder.nextMinuteTickDelay(delta)
            assertTrue("delay in (0,60000]", delay in 1L..60000L)
            assertEquals(
                HeaderViewHolder.minutesUntil(delta) - 1,
                HeaderViewHolder.minutesUntil(delta - delay)
            )
        }
    }
}
