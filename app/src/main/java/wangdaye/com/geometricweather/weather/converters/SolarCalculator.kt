package wangdaye.com.geometricweather.weather.converters

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import wangdaye.com.geometricweather.common.basic.models.weather.Astro

/**
 * Sunrise/sunset for one local calendar day, from NOAA's general solar position equations —
 * the sources without an astro block in their responses (MET Norway, CMA) compute it here instead
 * of showing nothing while `Weather.isDaylight()` degrades to a hardcoded 06:00-18:00 clock.
 *
 * The disc's upper edge touching the horizon — zenith 90.833°, i.e. 0.833° of refraction plus
 * semidiameter — is the same convention almanacs use, so published times are the ground truth
 * (agreement is within a couple of minutes, far below what a weather card can render).
 *
 * Polar day and night have no rise and no set; that is returned as **null** rather than an empty
 * astro, so the caller keeps its "no data" behaviour instead of pinning times that never happen —
 * the model and every consumer already tolerate an absent astro.
 *
 * All instants are absolute epoch arithmetic. The declination and equation of time are evaluated
 * at local noon of the requested day; the events themselves are anchored on the UTC midnight of
 * the UTC day holding that noon and may land on a neighbouring UTC day — Tianjin's sunrise is the
 * previous UTC evening, so day-clamping here would be a 24-hour bug, not a rounding one.
 */
object SolarCalculator {

    /** Upper limb on the horizon: refraction (0.583°) + solar semidiameter (0.25°). */
    private const val ZENITH_DEGREES = 90.833

    private const val MS_PER_DAY = 86400000.0
    private const val MS_PER_MINUTE = 60000.0
    private const val MINUTES_PER_DEGREE = 4.0

    /** Unix epoch 1970-01-01T00:00Z is Julian Day 2440587.5. */
    private const val JULIAN_DAY_OF_EPOCH = 2440587.5

    fun sunTimes(date: Date, latitude: Double, longitude: Double, zone: TimeZone): Astro? {
        val calendar = Calendar.getInstance(zone)
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val noonMillis = calendar.timeInMillis

        val t = julianCentury(noonMillis / MS_PER_DAY + JULIAN_DAY_OF_EPOCH)
        val declination = solarDeclination(t)
        val equationOfTime = equationOfTimeMinutes(t)

        // UTC midnight of the UTC day containing local noon; rise/set are minute offsets from
        // there and are *not* clamped back into the day (see class doc).
        val anchorMinutes = (noonMillis / MS_PER_DAY).toLong() * 1440.0
        val solarNoonMinutes = anchorMinutes + 720.0 - MINUTES_PER_DEGREE * longitude - equationOfTime

        // The sun stays up (or down) around the clock when the hour-angle equation has no root.
        val hourAngleCos = (cos(Math.toRadians(ZENITH_DEGREES))
                - sin(Math.toRadians(latitude)) * sin(Math.toRadians(declination))) /
                (cos(Math.toRadians(latitude)) * cos(Math.toRadians(declination)))
        if (hourAngleCos < -1.0 || hourAngleCos > 1.0) {
            return null
        }
        val halfDayMinutes = Math.toDegrees(acos(hourAngleCos)) * MINUTES_PER_DEGREE

        return Astro(
            Date(((solarNoonMinutes - halfDayMinutes) * MS_PER_MINUTE).toLong()),
            Date(((solarNoonMinutes + halfDayMinutes) * MS_PER_MINUTE).toLong())
        )
    }

    /** Julian centuries since J2000.0. */
    private fun julianCentury(julianDay: Double): Double = (julianDay - 2451545.0) / 36525.0

    private fun solarDeclination(t: Double): Double {
        val meanObliquity = 23.439291 - t * (0.0130042 + t * (1.639e-7 - t * 5.036e-7))
        val correctedObliquity = meanObliquity + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * t))
        val apparentLongitude = solarApparentLongitude(t)
        return Math.toDegrees(
            Math.asin(sin(Math.toRadians(correctedObliquity)) * sin(Math.toRadians(apparentLongitude)))
        )
    }

    private fun solarApparentLongitude(t: Double): Double {
        val meanLongitude = 280.46646 + t * (36000.76983 + 0.0003032 * t)
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val centre = sin(Math.toRadians(meanAnomaly)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(Math.toRadians(2 * meanAnomaly)) * (0.019993 - 0.000101 * t) +
                sin(Math.toRadians(3 * meanAnomaly)) * 0.000289
        val node = 125.04 - 1934.136 * t
        // Obliquity correction takes the sine of the node, the obliquity itself the cosine.
        return meanLongitude + centre - 0.00569 - 0.00478 * sin(Math.toRadians(node))
    }

    private fun equationOfTimeMinutes(t: Double): Double {
        val meanLongitude = 280.46646 + t * (36000.76983 + 0.0003032 * t)
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val meanObliquity = 23.439291 - t * (0.0130042 + t * (1.639e-7 - t * 5.036e-7))
        val y = tan(Math.toRadians(meanObliquity / 2)).let { it * it }

        return 4.0 * Math.toDegrees(
            y * sin(Math.toRadians(2 * meanLongitude))
                    - 2 * eccentricity * sin(Math.toRadians(meanAnomaly))
                    + 4 * eccentricity * y * sin(Math.toRadians(meanAnomaly)) * cos(Math.toRadians(2 * meanLongitude))
                    - 0.5 * y * y * sin(Math.toRadians(4 * meanLongitude))
                    - 1.25 * eccentricity * eccentricity * sin(Math.toRadians(2 * meanAnomaly))
        )
    }
}
