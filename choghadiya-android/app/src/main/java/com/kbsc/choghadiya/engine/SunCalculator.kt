package com.kbsc.choghadiya.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise / sunset calculator based on the NOAA Solar Calculator equations
 * (Jean Meeus, "Astronomical Algorithms"). Accuracy is typically within
 * ±1 minute for latitudes between ±60°.
 *
 * Panchang convention: sunrise/sunset is the moment the UPPER LIMB of the Sun
 * touches the horizon, after allowing for atmospheric refraction. This equals a
 * zenith of 90° 50' (= 90.8333°): 34' refraction + 16' solar semi-diameter.
 * This is the convention followed by most modern Gujarati/Hindu panchangs.
 */
object SunCalculator {

    /** Upper limb + standard refraction (default for Panchang). */
    const val ZENITH_UPPER_LIMB = 90.8333

    /** Centre of the solar disc touching the horizon, refraction ignored (older "Madhya Bimba" convention). */
    const val ZENITH_DISC_CENTRE = 90.0

    data class SunTimes(val sunrise: Instant, val sunset: Instant)

    /**
     * @param date local civil date at the place
     * @param latitude degrees, north positive
     * @param longitude degrees, east positive
     * @param utcOffsetHours offset of the place's time zone for that date (used only for the first guess)
     */
    fun sunTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        utcOffsetHours: Double,
        zenith: Double = ZENITH_UPPER_LIMB,
    ): SunTimes? {
        val rise = event(date, latitude, longitude, utcOffsetHours, zenith, rising = true) ?: return null
        val set = event(date, latitude, longitude, utcOffsetHours, zenith, rising = false) ?: return null
        return SunTimes(rise, set)
    }

    private fun event(
        date: LocalDate,
        lat: Double,
        lon: Double,
        utcOffsetHours: Double,
        zenith: Double,
        rising: Boolean,
    ): Instant? {
        // Julian Day of 00:00 UT of the calendar date.
        val jd0 = julianDay(date)
        // First guess: local 06:00 / 18:00 expressed in UT minutes from 00:00 UT of the date.
        var utMinutes = (if (rising) 6.0 else 18.0) * 60.0 - utcOffsetHours * 60.0

        repeat(4) {
            val jd = jd0 + utMinutes / 1440.0
            val t = (jd - 2451545.0) / 36525.0
            val (decl, eqTime) = declinationAndEquationOfTime(t)
            val latR = Math.toRadians(lat)
            val cosH = cos(Math.toRadians(zenith)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
            if (abs(cosH) > 1.0) return null // Sun never rises / never sets (polar regions)
            val hourAngleDeg = Math.toDegrees(acos(cosH))
            val solarNoon = 720.0 - 4.0 * lon - eqTime
            utMinutes = if (rising) solarNoon - 4.0 * hourAngleDeg else solarNoon + 4.0 * hourAngleDeg
        }
        val midnightUtc = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return midnightUtc.plusMillis((utMinutes * 60_000.0).toLong())
    }

    /** Returns solar declination (radians) and equation of time (minutes). */
    private fun declinationAndEquationOfTime(t: Double): Pair<Double, Double> {
        val l0 = normalizeDeg(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mR = Math.toRadians(m)
        val c = sin(mR) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mR) * (0.019993 - 0.000101 * t) +
            sin(3 * mR) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(Math.toRadians(omega))
        val epsR = Math.toRadians(eps)
        val decl = asin(sin(epsR) * sin(Math.toRadians(lambda)))

        val y = tan(epsR / 2).let { it * it }
        val l0R = Math.toRadians(l0)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * l0R) - 2 * e * sin(mR) + 4 * e * y * sin(mR) * cos(2 * l0R) -
                0.5 * y * y * sin(4 * l0R) - 1.25 * e * e * sin(2 * mR),
        )
        return decl to eqTime
    }

    private fun normalizeDeg(d: Double): Double = d - 360.0 * floor(d / 360.0)

    /** Julian Day number at 00:00 UT for a Gregorian calendar date. */
    internal fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }
}
