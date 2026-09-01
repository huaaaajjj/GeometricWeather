package basic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wangdaye.com.geometricweather.common.basic.models.Location
import wangdaye.com.geometricweather.common.basic.models.options.provider.WeatherSource
import wangdaye.com.geometricweather.common.basic.models.weather.Weather
import wangdaye.com.geometricweather.weather.converters.OpenMeteoResultConverter
import wangdaye.com.geometricweather.weather.json.openmeteo.OpenMeteoResult
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.TimeZone

/**
 * Three trims, one reason: what a provider sends is not what is still ahead of you.
 *
 * Providers answer in whole days, so an hourly series still opens at 00:00 at 11 pm and the card
 * reads as a log of hours nobody can act on — [Weather.withHoursFrom] is what the hourly card uses
 * to open at the hour it is now. And a 16-day source answers 384 of those hours, which past day
 * three is scroll length, not information — [Weather.withHoursUntil] caps the tail. A provider can
 * also lag a whole day: a domestic source serves yesterday as its first day for hours after
 * midnight, which matters far more, since the header, the widgets and the notifications all read
 * day 0 as today — [Weather.withDaysFrom] drops it.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12.2 ships no SDK 35 sandbox; targetSdk is 35, so pin the runtime to 34.
@Config(sdk = [34])
class WeatherTrimTest {

    private lateinit var weather: Weather
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val location = Location(
            "54517_tj",
            39.113019f, 117.150738f,
            TimeZone.getTimeZone("Asia/Shanghai"),
            "中国", "天津市", "天津市", "南开区",
            null,
            WeatherSource.OPEN_METEO,
            false, false, true
        )
        val stream = javaClass.classLoader!!.getResourceAsStream("openmeteo/forecast.json")
        assertNotNull("fixture missing: openmeteo/forecast.json", stream)
        weather = OpenMeteoResultConverter.convert(
            context, location,
            Gson().fromJson(
                InputStreamReader(stream, StandardCharsets.UTF_8), OpenMeteoResult::class.java
            )
        )!!
        assertTrue(weather.hourlyForecast.size > 24)
    }

    @Test
    fun theSeriesStartsAtTheHourItIsNow() {
        val tenth = weather.hourlyForecast[10]
        // Mid-hour, to prove the boundary is the hour and not the exact instant.
        val trimmed = weather.withHoursFrom(tenth.time + 31 * 60 * 1000L)

        assertEquals(weather.hourlyForecast.size - 10, trimmed.hourlyForecast.size)
        // The hour under way is kept — it is now, not the past.
        assertEquals(tenth.time, trimmed.hourlyForecast[0].time)
        // And the rest of the weather is untouched.
        assertEquals(weather.dailyForecast.size, trimmed.dailyForecast.size)
        assertSame(weather.current, trimmed.current)
    }

    @Test
    fun anHourThatEndedExactlyNowIsAlreadyPast() {
        val second = weather.hourlyForecast[1]
        val trimmed = weather.withHoursFrom(second.time)

        assertEquals(second.time, trimmed.hourlyForecast[0].time)
        assertEquals(weather.hourlyForecast.size - 1, trimmed.hourlyForecast.size)
    }

    /** Nothing to drop, or everything to drop: hand back the same object either way. */
    @Test
    fun aSeriesWithNothingElapsedAndOneFullyElapsedBothComeBackUnchanged() {
        val first = weather.hourlyForecast[0]
        val last = weather.hourlyForecast.last()

        assertSame(weather, weather.withHoursFrom(first.time))
        // A cache stale enough that every hour has passed is still worth showing.
        assertSame(weather, weather.withHoursFrom(last.time + 2 * 60 * 60 * 1000L))
    }

    @Test
    fun theHourAtTheHorizonGoesAndTheOneBeforeItStays() {
        // The fixture answers exactly three days: index 71 starts 72 h after index 0, which puts
        // it on the far side of a horizon drawn at its own start.
        val horizon = weather.hourlyForecast[71]
        val trimmed = weather.withHoursUntil(horizon.time)

        assertEquals(weather.hourlyForecast.size - 1, trimmed.hourlyForecast.size)
        assertEquals(weather.hourlyForecast[70].time, trimmed.hourlyForecast.last().time)
        // The rest of the weather is untouched.
        assertEquals(weather.dailyForecast.size, trimmed.dailyForecast.size)
        assertSame(weather.current, trimmed.current)
    }

    @Test
    fun aSeriesFullyInsideAndFullyOutsideTheHorizonBothComeBackUnchanged() {
        val first = weather.hourlyForecast[0]
        val last = weather.hourlyForecast.last()

        assertSame(weather, weather.withHoursUntil(last.time + 60 * 60 * 1000L))
        // An entirely over-horizon cache would be emptied by the cap — still not worth an empty card.
        assertSame(weather, weather.withHoursUntil(first.time))
    }

    @Test
    fun theDayListStartsToday() {
        val second = weather.dailyForecast[1]
        // Mid-afternoon on the second day: the first one is over, this one is not.
        val trimmed = weather.withDaysFrom(second.time + 15 * 60 * 60 * 1000L, zone)

        assertEquals(weather.dailyForecast.size - 1, trimmed.dailyForecast.size)
        assertEquals(second.time, trimmed.dailyForecast[0].time)
        // Nothing else moves — the hours are trimmed separately, by their own clock.
        assertEquals(weather.hourlyForecast.size, trimmed.hourlyForecast.size)
    }

    /**
     * The cut is the *location's* midnight, not the device's — the same instant is 23:30 of one day
     * in Shanghai and 00:30 of the next in Tokyo, so a day is over in one and not in the other. This
     * is the offset that showed up in production: the phone sits at +8 and the saved place at +9.
     */
    @Test
    fun theCutIsTheLocationsMidnightNotTheDevices() {
        val second = weather.dailyForecast[1]
        val instant = second.time - 30 * 60 * 1000L

        // 23:30 of day one where the days are anchored: day one is still running.
        assertSame(weather, weather.withDaysFrom(instant, zone))
        // 00:30 of day two an hour east: day one is over there.
        val tokyo = weather.withDaysFrom(instant, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(weather.dailyForecast.size - 1, tokyo.dailyForecast.size)
        assertEquals(second.time, tokyo.dailyForecast[0].time)
    }

    @Test
    fun aDayListWithNothingOverAndOneEntirelyOverBothComeBackUnchanged() {
        val first = weather.dailyForecast[0]
        val last = weather.dailyForecast.last()

        assertSame(weather, weather.withDaysFrom(first.time + 60 * 1000L, zone))
        assertSame(weather, weather.withDaysFrom(last.time + 48 * 60 * 60 * 1000L, zone))
    }
}
