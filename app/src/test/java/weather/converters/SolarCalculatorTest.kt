package weather.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import wangdaye.com.geometricweather.common.basic.models.weather.Astro
import wangdaye.com.geometricweather.weather.converters.SolarCalculator
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * The reference times below come from the sunrise-sunset.org API (the standard 90.833° zenith,
 * same convention as printed almanacs), fetched for the exact coordinates and dates asserted here.
 * Agreement within three minutes is plenty: the consumer is a weather card, not navigation, and
 * the same convention means a systematic error would have to come from the formulas themselves —
 * which is exactly what these values are here to catch.
 *
 * Cases were chosen to fail on the classic sign/anchor mistakes, not to be pleasant:
 * - Oslo in a DST zone, southern enough to be ordinary.
 * - Tianjin, whose sunrise falls on the *previous UTC day* — a UTC-midnight clamp shows up as a
 *   24-hour error here, not as a rounding one.
 * - Sydney in June, southern hemisphere, to pin the latitude sign.
 * - Tromsø on both solstices, where the honest answer is "no such event".
 */
class SolarCalculatorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun localMidnight(
        year: Int, month: Int, day: Int, zone: TimeZone
    ): Date = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }.time

    private fun assertNear(actual: Date?, expectedMillis: Long, label: String) {
        assertNotNull(label, actual)
        val delta = Math.abs(requireNotNull(actual).time - expectedMillis)
        assertEquals(label, 0.0, delta / 1000.0, 180.0)
    }

    private fun assertAlmanac(
        astro: Astro?, riseUtc: Long, setUtc: Long, label: String
    ) {
        assertNear(astro?.riseDate, riseUtc, "$label rise")
        assertNear(astro?.setDate, setUtc, "$label set")
    }

    @Test
    fun osloInAugustMatchesTheAlmanac() {
        val astro = SolarCalculator.sunTimes(
            localMidnight(2026, 8, 23, TimeZone.getTimeZone("Europe/Oslo")),
            59.9139, 10.7522, TimeZone.getTimeZone("Europe/Oslo")
        )
        assertAlmanac(
            astro,
            utcMillis(2026, 8, 23, 3, 49, 0),
            utcMillis(2026, 8, 23, 18, 50, 16),
            "Oslo 2026-08-23"
        )
    }

    @Test
    fun tianjinSunriseFallsOnThePreviousUtcDay() {
        val astro = SolarCalculator.sunTimes(
            localMidnight(2026, 8, 14, TimeZone.getTimeZone("Asia/Shanghai")),
            39.08, 117.22, TimeZone.getTimeZone("Asia/Shanghai")
        )
        assertAlmanac(
            astro,
            utcMillis(2026, 8, 13, 21, 21, 53),
            utcMillis(2026, 8, 14, 11, 9, 51),
            "Tianjin 2026-08-14"
        )
    }

    @Test
    fun sydneyInJunePinsTheLatitudeSign() {
        val astro = SolarCalculator.sunTimes(
            localMidnight(2026, 6, 21, TimeZone.getTimeZone("Australia/Sydney")),
            -33.8688, 151.2093, TimeZone.getTimeZone("Australia/Sydney")
        )
        assertAlmanac(
            astro,
            utcMillis(2026, 6, 20, 20, 58, 34),
            utcMillis(2026, 6, 21, 6, 55, 13),
            "Sydney 2026-06-21"
        )
    }

    @Test
    fun polarNightAndMidnightSunHaveNoEvents() {
        val tromso = 69.6492 to 18.9553
        val zone = TimeZone.getTimeZone("Europe/Oslo")

        // Winter solstice: the sun never rises. Summer solstice: it never sets.
        assertNull(
            SolarCalculator.sunTimes(
                localMidnight(2026, 12, 21, zone), tromso.first, tromso.second, zone
            )
        )
        assertNull(
            SolarCalculator.sunTimes(
                localMidnight(2026, 6, 21, zone), tromso.first, tromso.second, zone
            )
        )
    }
}
