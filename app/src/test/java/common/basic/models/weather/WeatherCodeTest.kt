package common.basic.models.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import wangdaye.com.geometricweather.common.basic.models.weather.WeatherCode

/**
 * Pins the root cause behind "fog is painted as a clear sky": Room stores a code as [Enum.name]
 * (uppercase) and reads it back through [WeatherCode.getInstance], which matches by lowercase
 * substring. That round trip must be lossless for *every* code, or a source's code silently
 * degrades to CLEAR the moment its weather is cached and re-read (the normal cold-start path).
 * FOG was the sole casualty — its branch was missing, so "FOG" fell through to CLEAR for all
 * sources on read-back, and for Caiyun even at conversion time.
 */
class WeatherCodeTest {

    /** The name()→getInstance round trip Room relies on (RoomTypeConverters). Catches any future
     *  enum member added without a matching branch — remove the fog branch and FOG fails here. */
    @Test
    fun everyCodeSurvivesTheNameRoundTrip() {
        WeatherCode.values().forEach { code ->
            assertEquals(code, WeatherCode.getInstance(code.name))
        }
    }

    @Test
    fun fogIsRecognisedRegardlessOfCase() {
        assertEquals(WeatherCode.FOG, WeatherCode.getInstance("FOG"))
        assertEquals(WeatherCode.FOG, WeatherCode.getInstance("fog"))
        assertEquals(WeatherCode.FOG, WeatherCode.getInstance("LIGHT_FOG"))
        assertNotEquals(WeatherCode.CLEAR, WeatherCode.getInstance("FOG"))
    }

    /** No dust/sand code exists; both fall back to the nearest low-visibility sky (HAZE), never
     *  CLEAR. Only Caiyun feeds these raw strings (DUST/SAND skycons). */
    @Test
    fun dustAndSandFallBackToHazeNotClear() {
        assertEquals(WeatherCode.HAZE, WeatherCode.getInstance("DUST"))
        assertEquals(WeatherCode.HAZE, WeatherCode.getInstance("SAND"))
    }
}
