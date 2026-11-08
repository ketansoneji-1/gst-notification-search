package com.kbsc.choghadiya.engine

import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Positional astronomy for the Panchang. Everything is computed on the device from
 * published series, so there are no data tables that expire.
 *
 * - Sun: VSOP87 (truncated series of Meeus, "Astronomical Algorithms", ch. 25/32 and App. III).
 *   Within 1 arc-second of Swiss Ephemeris for 2026-2046.
 * - Moon: ELP-2000/82 main terms (Meeus ch. 47). Within about 12 arc-seconds.
 * - Ayanamsa: Lahiri (Chitrapaksha), matching Swiss Ephemeris SIDM_LAHIRI to < 1".
 *
 * Longitudes are referred to the mean equinox of date (nutation omitted on both the
 * planet and the ayanamsa, so it cancels in sidereal values).
 */
object Astro {

    // ---------------------------------------------------------------- time

    fun jdUT(t: Instant): Double = t.epochSecond / 86400.0 + t.nano / 86_400_000_000_000.0 + 2440587.5

    fun instant(jdUT: Double): Instant {
        val ms = Math.round((jdUT - 2440587.5) * 86_400_000.0)
        return Instant.ofEpochMilli(ms)
    }

    /**
     * ΔT = TT − UT in seconds. For 2005-2060 a linear fit to the IERS/Swiss Ephemeris values
     * (68.9 s in 2026, 73.4 s in 2046); outside that range the Espenak-Meeus polynomials.
     * An error of even 10 s here moves a tithi end time by less than 1 second.
     */
    fun deltaT(jdUT: Double): Double {
        val y = 2000.0 + (jdUT - 2451545.0) / 365.25
        return when {
            y in 2005.0..2060.0 -> 68.9 + 0.227 * (y - 2026.0)
            y < 2005.0 && y >= 1986.0 -> {
                val t = y - 2000.0
                63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t
            }
            y > 2060.0 && y <= 2150.0 -> {
                val u = (y - 1820.0) / 100.0
                -20.0 + 32.0 * u * u - 0.5628 * (2150.0 - y)
            }
            else -> {
                val u = (y - 1820.0) / 100.0
                -20.0 + 32.0 * u * u
            }
        }
    }

    /** Julian centuries (TT) from J2000.0. */
    fun centuriesTT(jdUT: Double): Double = (jdUT + deltaT(jdUT) / 86400.0 - 2451545.0) / 36525.0

    // ---------------------------------------------------------------- angles

    fun norm360(d: Double): Double = d - 360.0 * floor(d / 360.0)

    /** Normalises to (-180, 180]. */
    fun normPm180(d: Double): Double {
        val x = norm360(d)
        return if (x > 180.0) x - 360.0 else x
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))

    // ---------------------------------------------------------------- Sun

    private fun series(s: DoubleArray, tau: Double): Double {
        var sum = 0.0
        var i = 0
        while (i < s.size) {
            sum += s[i] * cos(s[i + 1] + s[i + 2] * tau)
            i += 3
        }
        return sum
    }

    /** Apparent geocentric tropical longitude of the Sun (mean equinox of date), degrees. */
    fun sunLongitude(jdUT: Double): Double {
        val tau = centuriesTT(jdUT) / 10.0
        val l = (series(EARTH_L0, tau) +
            tau * (series(EARTH_L1, tau) +
                tau * (series(EARTH_L2, tau) +
                    tau * (series(EARTH_L3, tau) +
                        tau * (series(EARTH_L4, tau) + tau * series(EARTH_L5, tau)))))) / 1e8
        // heliocentric Earth → geocentric Sun, FK5 correction and aberration
        return norm360(Math.toDegrees(l) + 180.0 - 0.09033 / 3600.0 - 20.4898 / 3600.0)
    }

    // ---------------------------------------------------------------- Moon

    class MoonPos(val longitude: Double, val latitude: Double, val distanceKm: Double)

    fun moon(jdUT: Double): MoonPos {
        val t = centuriesTT(jdUT)
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2

        var sl = 0.0
        var sr = 0.0
        var i = 0
        while (i < MOON_LR.size) {
            val mm = MOON_LR[i + 1]
            val arg = MOON_LR[i] * d + mm * m + MOON_LR[i + 2] * mp + MOON_LR[i + 3] * f
            val ef = if (mm == 0) 1.0 else if (abs(mm) == 1) e else e * e
            sl += MOON_LR[i + 4] * ef * sinD(arg)
            sr += MOON_LR[i + 5] * ef * cosD(arg)
            i += 6
        }
        var sb = 0.0
        i = 0
        while (i < MOON_B.size) {
            val mm = MOON_B[i + 1]
            val arg = MOON_B[i] * d + mm * m + MOON_B[i + 2] * mp + MOON_B[i + 3] * f
            val ef = if (mm == 0) 1.0 else if (abs(mm) == 1) e else e * e
            sb += MOON_B[i + 4] * ef * sinD(arg)
            i += 5
        }
        sl += 3958 * sinD(a1) + 1962 * sinD(lp - f) + 318 * sinD(a2)
        sb += -2235 * sinD(lp) + 382 * sinD(a3) + 175 * sinD(a1 - f) + 175 * sinD(a1 + f) +
            127 * sinD(lp - mp) - 115 * sinD(lp + mp)
        return MoonPos(norm360(lp + sl / 1e6), sb / 1e6, 385000.56 + sr / 1000.0)
    }

    fun moonLongitude(jdUT: Double): Double = moon(jdUT).longitude

    // ---------------------------------------------------------------- sidereal

    /** Lahiri (Chitrapaksha) ayanamsa, mean, degrees. 23°51'25.5" at J2000 + general precession. */
    fun ayanamsa(jdUT: Double): Double {
        val t = centuriesTT(jdUT)
        return 23.857092 + (5028.796195 * t + 1.1054348 * t * t) / 3600.0
    }

    fun sunSidereal(jdUT: Double): Double = norm360(sunLongitude(jdUT) - ayanamsa(jdUT))
    fun moonSidereal(jdUT: Double): Double = norm360(moonLongitude(jdUT) - ayanamsa(jdUT))

    /** Moon − Sun elongation 0..360 (basis of tithi and karana). */
    fun elongation(jdUT: Double): Double = norm360(moonLongitude(jdUT) - sunLongitude(jdUT))

    /** Sidereal Sun + Moon (basis of yoga). */
    fun yogaAngle(jdUT: Double): Double = norm360(sunSidereal(jdUT) + moonSidereal(jdUT))

    // ---------------------------------------------------------------- moonrise / moonset

    /** Altitude of the Moon's centre above the horizon minus the rise/set altitude h0 (degrees). */
    fun moonAltitudeMinusH0(jdUT: Double, latDeg: Double, lonDeg: Double): Double {
        val p = moon(jdUT)
        val t = centuriesTT(jdUT)
        val eps = 23.439291 - 0.0130042 * t
        val lam = Math.toRadians(p.longitude)
        val bet = Math.toRadians(p.latitude)
        val epsR = Math.toRadians(eps)
        val ra = atan2(sin(lam) * cos(epsR) - tan(bet) * sin(epsR), cos(lam))
        val dec = asin(sin(bet) * cos(epsR) + cos(bet) * sin(epsR) * sin(lam))
        val tu = (jdUT - 2451545.0) / 36525.0
        val gmst = norm360(280.46061837 + 360.98564736629 * (jdUT - 2451545.0) + 0.000387933 * tu * tu)
        val h = Math.toRadians(gmst + lonDeg) - ra
        val lat = Math.toRadians(latDeg)
        val alt = Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h)))
        val parallax = Math.toDegrees(asin(6378.14 / p.distanceKm))
        val h0 = 0.7275 * parallax - 34.0 / 60.0 // upper limb + refraction, topocentric (Meeus ch. 15)
        return alt - h0
    }

    /** Moonrise and moonset between [startJd] and [endJd] (UT), or null when absent. */
    fun moonRiseSet(startJd: Double, endJd: Double, lat: Double, lon: Double): Pair<Double?, Double?> {
        var rise: Double? = null
        var set: Double? = null
        val step = 1.0 / 72.0 // 20 minutes
        var t0 = startJd
        var a0 = moonAltitudeMinusH0(t0, lat, lon)
        while (t0 < endJd && (rise == null || set == null)) {
            val t1 = minOf(t0 + step, endJd)
            val a1 = moonAltitudeMinusH0(t1, lat, lon)
            if (a0 < 0 && a1 >= 0 && rise == null) rise = bisect(t0, t1, lat, lon)
            if (a0 >= 0 && a1 < 0 && set == null) set = bisect(t0, t1, lat, lon)
            t0 = t1
            a0 = a1
        }
        return rise to set
    }

    private fun bisect(a: Double, b: Double, lat: Double, lon: Double): Double {
        var lo = a
        var hi = b
        val loNeg = moonAltitudeMinusH0(lo, lat, lon) < 0
        repeat(30) {
            val mid = (lo + hi) / 2
            if ((moonAltitudeMinusH0(mid, lat, lon) < 0) == loNeg) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    // ---------------------------------------------------------------- crossings

    /**
     * Solves angle(jd) == target (degrees) near [guessJd] by Newton iteration. [angle] must be
     * continuous and increasing (mod 360). Returns JD (UT).
     */
    fun solve(angle: (Double) -> Double, target: Double, guessJd: Double): Double {
        var jd = guessJd
        repeat(40) {
            val diff = normPm180(target - angle(jd))
            if (abs(diff) < 1e-7) return jd
            val h = 0.02
            val rate = normPm180(angle(jd + h) - angle(jd - h)) / (2 * h)
            jd += diff / rate
        }
        return jd
    }

    /** Next time after [fromJd] at which angle reaches the next multiple of [unit] degrees. */
    fun nextBoundary(angle: (Double) -> Double, unit: Double, meanRate: Double, fromJd: Double): Pair<Int, Double> {
        val a = angle(fromJd)
        val idx = floor(a / unit).toInt()
        val target = norm360((idx + 1) * unit)
        val guess = fromJd + ((idx + 1) * unit - a) / meanRate
        return idx to solve(angle, target, guess)
    }

    // ---------------------------------------------------------------- coefficient tables
    // Moon: D, M, M', F, Σl (1e-6 deg), Σr (1e-3 km)   |   latitude: D, M, M', F, Σb (1e-6 deg)
    // Earth VSOP87 L series: amplitude (1e-8 rad), phase (rad), frequency (rad / millennium)

    private val MOON_LR = intArrayOf(
        0, 0, 1, 0, 6288774, -20905355,
        2, 0, -1, 0, 1274027, -3699111,
        2, 0, 0, 0, 658314, -2955968,
        0, 0, 2, 0, 213618, -569925,
        0, 1, 0, 0, -185116, 48888,
        0, 0, 0, 2, -114332, -3149,
        2, 0, -2, 0, 58793, 246158,
        2, -1, -1, 0, 57066, -152138,
        2, 0, 1, 0, 53322, -170733,
        2, -1, 0, 0, 45758, -204586,
        0, 1, -1, 0, -40923, -129620,
        1, 0, 0, 0, -34720, 108743,
        0, 1, 1, 0, -30383, 104755,
        2, 0, 0, -2, 15327, 10321,
        0, 0, 1, 2, -12528, 0,
        0, 0, 1, -2, 10980, 79661,
        4, 0, -1, 0, 10675, -34782,
        0, 0, 3, 0, 10034, -23210,
        4, 0, -2, 0, 8548, -21636,
        2, 1, -1, 0, -7888, 24208,
        2, 1, 0, 0, -6766, 30824,
        1, 0, -1, 0, -5163, -8379,
        1, 1, 0, 0, 4987, -16675,
        2, -1, 1, 0, 4036, -12831,
        2, 0, 2, 0, 3994, -10445,
        4, 0, 0, 0, 3861, -11650,
        2, 0, -3, 0, 3665, 14403,
        0, 1, -2, 0, -2689, -7003,
        2, 0, -1, 2, -2602, 0,
        2, -1, -2, 0, 2390, 10056,
        1, 0, 1, 0, -2348, 6322,
        2, -2, 0, 0, 2236, -9884,
        0, 1, 2, 0, -2120, 5751,
        0, 2, 0, 0, -2069, 0,
        2, -2, -1, 0, 2048, -4950,
        2, 0, 1, -2, -1773, 4130,
        2, 0, 0, 2, -1595, 0,
        4, -1, -1, 0, 1215, -3958,
        0, 0, 2, 2, -1110, 0,
        3, 0, -1, 0, -892, 3258,
        2, 1, 1, 0, -810, 2616,
        4, -1, -2, 0, 759, -1897,
        0, 2, -1, 0, -713, -2117,
        2, 2, -1, 0, -700, 2354,
        2, 1, -2, 0, 691, 0,
        2, -1, 0, -2, 596, 0,
        4, 0, 1, 0, 549, -1423,
        0, 0, 4, 0, 537, -1117,
        4, -1, 0, 0, 520, -1571,
        1, 0, -2, 0, -487, -1739,
        2, 1, 0, -2, -399, 0,
        0, 0, 2, -2, -381, -4421,
        1, 1, 1, 0, 351, 0,
        3, 0, -2, 0, -340, 0,
        4, 0, -3, 0, 330, 0,
        2, -1, 2, 0, 327, 0,
        0, 2, 1, 0, -323, 1165,
        1, 1, -1, 0, 299, 0,
        2, 0, 3, 0, 294, 0,
        2, 0, -1, -2, 0, 8752,
    )

    private val MOON_B = intArrayOf(
        0, 0, 0, 1, 5128122,
        0, 0, 1, 1, 280602,
        0, 0, 1, -1, 277693,
        2, 0, 0, -1, 173237,
        2, 0, -1, 1, 55413,
        2, 0, -1, -1, 46271,
        2, 0, 0, 1, 32573,
        0, 0, 2, 1, 17198,
        2, 0, 1, -1, 9266,
        0, 0, 2, -1, 8822,
        2, -1, 0, -1, 8216,
        2, 0, -2, -1, 4324,
        2, 0, 1, 1, 4200,
        2, 1, 0, -1, -3359,
        2, -1, -1, 1, 2463,
        2, -1, 0, 1, 2211,
        2, -1, -1, -1, 2065,
        0, 1, -1, -1, -1870,
        4, 0, -1, -1, 1828,
        0, 1, 0, 1, -1794,
        0, 0, 0, 3, -1749,
        0, 1, -1, 1, -1565,
        1, 0, 0, 1, -1491,
        0, 1, 1, 1, -1475,
        0, 1, 1, -1, -1410,
        0, 1, 0, -1, -1344,
        1, 0, 0, -1, -1335,
        0, 0, 3, 1, 1107,
        4, 0, 0, -1, 1021,
        4, 0, -1, 1, 833,
        0, 0, 1, -3, 777,
        4, 0, -2, 1, 671,
        2, 0, 0, -3, 607,
        2, 0, 2, -1, 596,
        2, -1, 1, -1, 491,
        2, 0, -2, 1, -451,
        0, 0, 3, -1, 439,
        2, 0, 2, 1, 422,
        2, 0, -3, -1, 421,
        2, 1, -1, 1, -366,
        2, 1, 0, 1, -351,
        4, 0, 0, 1, 331,
        2, -1, 1, 1, 315,
        2, -2, 0, -1, 302,
        0, 0, 1, 3, -283,
        2, 1, 1, -1, -229,
        1, 1, 0, -1, 223,
        1, 1, 0, 1, 223,
        0, 1, -2, -1, -220,
        2, 1, -1, -1, -220,
        1, 0, 1, 1, -185,
        2, -1, -2, -1, 181,
        0, 1, 2, 1, -177,
        4, 0, -2, -1, 176,
        4, -1, -1, -1, 166,
        1, 0, 1, -1, -164,
        4, 0, 1, -1, 132,
        1, 0, -1, -1, -119,
        4, -1, 0, -1, 115,
        2, -2, 0, 1, 107,
    )

    private val EARTH_L0 = doubleArrayOf(
        175347046.0, 0.0, 0.0,
        3341656.0, 4.6692568, 6283.07585,
        34894.0, 4.6261, 12566.1517,
        3497.0, 2.7441, 5753.3849,
        3418.0, 2.8289, 3.5231,
        3136.0, 3.6277, 77713.7715,
        2676.0, 4.4181, 7860.4194,
        2343.0, 6.1352, 3930.2097,
        1324.0, 0.7425, 11506.7698,
        1273.0, 2.0371, 529.691,
        1199.0, 1.1096, 1577.3435,
        990.0, 5.233, 5884.927,
        902.0, 2.045, 26.298,
        857.0, 3.508, 398.149,
        780.0, 1.179, 5223.694,
        753.0, 2.533, 5507.553,
        505.0, 4.583, 18849.228,
        492.0, 4.205, 775.523,
        357.0, 2.92, 0.067,
        317.0, 5.849, 11790.629,
        284.0, 1.899, 796.298,
        271.0, 0.315, 10977.079,
        243.0, 0.345, 5486.778,
        206.0, 4.806, 2544.314,
        205.0, 1.869, 5573.143,
        202.0, 2.458, 6069.777,
        156.0, 0.833, 213.299,
        132.0, 3.411, 2942.463,
        126.0, 1.083, 20.775,
        115.0, 0.645, 0.98,
        103.0, 0.636, 4694.003,
        102.0, 0.976, 15720.839,
        102.0, 4.267, 7.114,
        99.0, 6.21, 2146.17,
        98.0, 0.68, 155.42,
        86.0, 5.98, 161000.69,
        85.0, 1.3, 6275.96,
        85.0, 3.67, 71430.7,
        80.0, 1.81, 17260.15,
        79.0, 3.04, 12036.46,
        75.0, 1.76, 5088.63,
        74.0, 3.5, 3154.69,
        74.0, 4.68, 801.82,
        70.0, 0.83, 9437.76,
        62.0, 3.98, 8827.39,
        61.0, 1.82, 7084.9,
        57.0, 2.78, 6286.6,
        56.0, 4.39, 14143.5,
        56.0, 3.47, 6279.55,
        52.0, 0.19, 12139.55,
        52.0, 1.33, 1748.02,
        51.0, 0.28, 5856.48,
        49.0, 0.49, 1194.45,
        41.0, 5.37, 8429.24,
        41.0, 2.4, 19651.05,
        39.0, 6.17, 10447.39,
        37.0, 6.04, 10213.29,
        37.0, 2.57, 1059.38,
        36.0, 1.71, 2352.87,
        36.0, 1.78, 6812.77,
        33.0, 0.59, 17789.85,
        30.0, 0.44, 83996.85,
        30.0, 2.74, 1349.87,
        25.0, 3.16, 4690.48,
    )

    private val EARTH_L1 = doubleArrayOf(
        628331966747.0, 0.0, 0.0,
        206059.0, 2.678235, 6283.07585,
        4303.0, 2.6351, 12566.1517,
        425.0, 1.59, 3.523,
        119.0, 5.796, 26.298,
        109.0, 2.966, 1577.344,
        93.0, 2.59, 18849.23,
        72.0, 1.14, 529.69,
        68.0, 1.87, 398.15,
        67.0, 4.41, 5507.55,
        59.0, 2.89, 5223.69,
        56.0, 2.17, 155.42,
        45.0, 0.4, 796.3,
        36.0, 0.47, 775.52,
        29.0, 2.65, 7.11,
        21.0, 5.34, 0.98,
        19.0, 1.85, 5486.78,
        19.0, 4.97, 213.3,
        17.0, 2.99, 6275.96,
        16.0, 0.03, 2544.31,
        16.0, 1.43, 2146.17,
        15.0, 1.21, 10977.08,
        12.0, 2.83, 1748.02,
        12.0, 3.26, 5088.63,
        12.0, 5.27, 1194.45,
        12.0, 2.08, 4694.0,
        11.0, 0.77, 553.57,
        10.0, 1.3, 6286.6,
        10.0, 4.24, 1349.87,
        9.0, 2.7, 242.73,
        9.0, 5.64, 951.72,
        8.0, 5.3, 2352.87,
        6.0, 2.65, 9437.76,
        6.0, 4.67, 4690.48,
    )

    private val EARTH_L2 = doubleArrayOf(
        52919.0, 0.0, 0.0,
        8720.0, 1.0721, 6283.0758,
        309.0, 0.867, 12566.152,
        27.0, 0.05, 3.52,
        16.0, 5.19, 26.3,
        16.0, 3.68, 155.42,
        10.0, 0.76, 18849.23,
        9.0, 2.06, 77713.77,
        7.0, 0.83, 775.52,
        5.0, 4.66, 1577.34,
        4.0, 1.03, 7.11,
        4.0, 3.44, 5573.14,
        3.0, 5.14, 796.3,
        3.0, 6.05, 5507.55,
        3.0, 1.19, 242.73,
        3.0, 6.12, 529.69,
        3.0, 0.31, 398.15,
        3.0, 2.28, 553.57,
        2.0, 4.38, 5223.69,
        2.0, 3.75, 0.98,
    )

    private val EARTH_L3 = doubleArrayOf(
        289.0, 5.844, 6283.076,
        35.0, 0.0, 0.0,
        17.0, 5.49, 12566.15,
        3.0, 5.2, 155.42,
        1.0, 4.72, 3.52,
        1.0, 5.3, 18849.23,
        1.0, 5.97, 242.73,
    )

    private val EARTH_L4 = doubleArrayOf(
        114.0, 3.142, 0.0,
        8.0, 4.13, 6283.08,
        1.0, 3.84, 12566.15,
    )

    private val EARTH_L5 = doubleArrayOf(
        1.0, 3.14, 0.0,
    )
}
