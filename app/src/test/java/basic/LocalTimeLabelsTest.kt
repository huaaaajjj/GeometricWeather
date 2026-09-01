package basic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.common.basic.models.weather.Daily
import wangdaye.com.geometricweather.common.basic.models.weather.HalfDay
import wangdaye.com.geometricweather.common.basic.models.weather.Hourly
import wangdaye.com.geometricweather.common.basic.models.weather.Precipitation
import wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationProbability
import wangdaye.com.geometricweather.common.basic.models.weather.Temperature
import wangdaye.com.geometricweather.common.basic.models.weather.UV
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode
import wangdaye.com.geometricweather.common.basic.models.weather.Wind
import wangdaye.com.geometricweather.common.basic.models.weather.WindDegree
import java.util.Date
import java.util.TimeZone

/**
 * A day and an hour belong to a place, and the labels drawn off them — weekday name, date, "15时" —
 * have to read as they do *there*. They used to be formatted with a bare `Calendar.getInstance()`,
 * i.e. on the phone's clock: a Tokyo forecast on a +08:00 phone had every hour labelled an hour
 * early, and around midnight the weekday names were a day off.
 *
 * The zone is carried on the models themselves because the ~50 places that format them never hold
 * the location; it is filled in at the two funnels every weather passes through
 * ([wangdaye.com.geometricweather.weather.WeatherHelper] for a fresh answer,
 * [wangdaye.com.geometricweather.db.DatabaseHelper] for a cached one), each covered by its own test.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class LocalTimeLabelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 2026-09-01 22:00 UTC: 07:00 of the 2nd in Tokyo (+09:00), 15:00 of the 1st in Los Angeles. */
    private val instant = Date(1788300000000L)

    @Test
    fun anHourIsLabelledOnThePlacesClock() {
        val hourly = hourly()

        hourly.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        assertEquals(7, hourly.hourIn24Format)
        val tokyoLabel = hourly.getHour(context)

        hourly.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals(15, hourly.hourIn24Format)
        // The label itself follows the 12/24-hour setting, so what is pinned is that it moved.
        assertNotEquals(tokyoLabel, hourly.getHour(context))
    }

    @Test
    fun aWeekdayAndDateAreReadOnThePlacesCalendar() {
        val daily = daily()

        daily.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        val tokyoWeek = daily.getWeek(context)
        val tokyoDate = daily.getDate("MM-dd")

        daily.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals("09-01", daily.getDate("MM-dd"))
        assertEquals("09-02", tokyoDate)
        // The 1st is a Tuesday, the 2nd a Wednesday — different names, not a formatting detail.
        assertEquals(context.getString(wangdaye.com.geometricweather.R.string.week_3), tokyoWeek)
        assertEquals(context.getString(wangdaye.com.geometricweather.R.string.week_2),
                daily.getWeek(context))
    }

    /** Nothing filled it in — cached rows written by an older build, say — reads as it always did. */
    @Test
    fun anUnfilledZoneFallsBackToTheDevice() {
        assertEquals(TimeZone.getDefault(), daily().timeZone)
        assertEquals(TimeZone.getDefault(), hourly().timeZone)
    }

    private fun daily() = Daily(
        instant, instant.time,
        halfDay(), halfDay(), null, null, null, null, null, UV(null, null, null), 0f
    )

    private fun hourly() = Hourly(
        instant, instant.time, true, "晴", WeatherCode.CLEAR,
        Temperature(20, null, null, null, null, null, null),
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(null, null, null, null, null),
        Wind("N", WindDegree(0f, false), 1f, "一级"),
        UV(null, null, null)
    )

    private fun halfDay() = HalfDay(
        "晴", "晴", WeatherCode.CLEAR,
        Temperature(20, null, null, null, null, null, null),
        Precipitation(null, null, null, null, null),
        PrecipitationProbability(null, null, null, null, null),
        wangdaye.com.geometricweather.common.basic.models.weather.PrecipitationDuration(
            null, null, null, null, null
        ),
        Wind("N", WindDegree(0f, false), 1f, "一级"),
        50
    )
}
