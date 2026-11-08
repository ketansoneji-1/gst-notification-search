package com.kbsc.choghadiya.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor

/** One element (tithi / nakshatra / yoga / karana / rashi) that is running, with its end time. */
data class Period(val index: Int, val name: Name, val end: ZonedDateTime)

data class TimeRange(val start: ZonedDateTime, val end: ZonedDateTime)

/** Gujarati (Amanta, Kartikadi) lunar date. */
data class LunarDate(
    val monthIndex: Int,        // 1 = Chaitra ... 12 = Phalguna
    val isAdhik: Boolean,
    val tithi: Int,             // 0..29 at sunrise
    val vikramSamvat: Int,      // Gujarati (Kartik-based) Vikram Samvat
    val shakaSamvat: Int,
) {
    val monthName: Name get() = Names.month(monthIndex)
    val paksha: Name get() = Names.paksha(tithi)
    val tithiName: Name get() = Names.tithi(tithi)
    /** Day number in the paksha, 1..15. */
    val dayInPaksha: Int get() = tithi % 15 + 1
    val ritu: Name get() = Names.ritu[(monthIndex - 1) / 2]

    fun shortGu(): String = "${paksha.gu} ${if (tithi == 14 || tithi == 29) tithiName.gu else dayInPaksha.toString()}"
    fun shortEn(): String = "${if (tithi < 15) "S" else "K"}${dayInPaksha}"
    fun longGu(): String = "${if (isAdhik) "અધિક " else ""}${monthName.gu} ${paksha.gu} ${tithiName.gu}"
    fun longEn(): String = "${if (isAdhik) "Adhik " else ""}${monthName.en} ${paksha.en} ${tithiName.en}"
}

data class Panchang(
    val date: LocalDate,
    val location: Location,
    val weekday: Name,
    val sunrise: ZonedDateTime,
    val sunset: ZonedDateTime,
    val nextSunrise: ZonedDateTime,
    val moonrise: ZonedDateTime?,
    val moonset: ZonedDateTime?,
    val lunar: LunarDate,
    val tithis: List<Period>,
    val nakshatras: List<Period>,
    val yogas: List<Period>,
    val karanas: List<Period>,
    val moonRashis: List<Period>,
    val sunRashi: Name,
    val sankranti: Period?,     // Sun entering a new rashi during this Hindu day: index/name of the new rashi
    val ayana: Name,
    val rahuKaal: TimeRange,
    val gulikaKaal: TimeRange,
    val yamaganda: TimeRange,
    val abhijit: TimeRange?,    // traditionally not applicable on Wednesday
    val festivals: List<Festival>,
)

/**
 * Light-weight day summary for the month calendar. [kshayaTithi] is a tithi that begins and
 * ends between this sunrise and the next (it is "skipped" as an udaya tithi but observed today).
 */
data class DaySummary(val date: LocalDate, val lunar: LunarDate, val kshayaTithi: Int?, val festivals: List<Festival>) {
    val isMajor: Boolean get() = festivals.any { it.category == FestivalCategory.MAJOR }
}

object PanchangCalculator {

    private const val TITHI = 12.0
    private const val NAK = 360.0 / 27.0
    private const val KARANA = 6.0
    private const val RASHI = 30.0

    private val elong: (Double) -> Double = Astro::elongation
    private val moonSid: (Double) -> Double = Astro::moonSidereal
    private val sunSid: (Double) -> Double = Astro::sunSidereal
    private val yogaAng: (Double) -> Double = Astro::yogaAngle

    fun compute(date: LocalDate, loc: Location, zenith: Double = SunCalculator.ZENITH_UPPER_LIMB): Panchang? {
        val day = ChoghadiyaCalculator.compute(date, loc, zenith) ?: return null
        val zone = loc.zoneId
        val riseJd = Astro.jdUT(day.sunrise.toInstant())
        val nextJd = Astro.jdUT(day.nextSunrise.toInstant())

        val tithis = periods(elong, TITHI, 12.19, riseJd, nextJd, zone) { Names.tithi(it) }
        val naks = periods(moonSid, NAK, 13.18, riseJd, nextJd, zone) { Names.nakshatra[it] }
        val yogas = periods(yogaAng, NAK, 14.17, riseJd, nextJd, zone) { Names.yoga[it] }
        val karanas = periods(elong, KARANA, 12.19, riseJd, nextJd, zone) { Names.karana(it) }
        val moonRashis = periods(moonSid, RASHI, 13.18, riseJd, nextJd, zone) { Names.rashi[it] }

        val sunAtRise = Astro.sunSidereal(riseJd)
        val sunAtNext = Astro.sunSidereal(nextJd)
        val sunRashiIdx = floor(sunAtRise / RASHI).toInt()
        val sankranti = if (floor(sunAtNext / RASHI).toInt() != sunRashiIdx) {
            val (_, jd) = Astro.nextBoundary(sunSid, RASHI, 0.9856, riseJd)
            val newIdx = (sunRashiIdx + 1) % 12
            Period(newIdx, Names.rashi[newIdx], Astro.instant(jd).atZone(zone))
        } else null

        val ayana = if (sunAtRise >= 270.0 || sunAtRise < 90.0) Name("ઉત્તરાયણ", "Uttarayana") else Name("દક્ષિણાયન", "Dakshinayana")

        // Moonrise / moonset during the civil date (local midnight to midnight).
        val dayStartJd = Astro.jdUT(date.atStartOfDay(zone).toInstant())
        val dayEndJd = Astro.jdUT(date.plusDays(1).atStartOfDay(zone).toInstant())
        val (mr, ms) = Astro.moonRiseSet(dayStartJd, dayEndJd, loc.latitude, loc.longitude)

        val w = ChoghadiyaCalculator.weekdayIndex(date)
        val lunar = lunarDate(riseJd, zone)
        return Panchang(
            date = date,
            location = loc,
            weekday = Names.weekday[w],
            sunrise = day.sunrise,
            sunset = day.sunset,
            nextSunrise = day.nextSunrise,
            moonrise = mr?.let { Astro.instant(it).atZone(zone) },
            moonset = ms?.let { Astro.instant(it).atZone(zone) },
            lunar = lunar,
            tithis = tithis,
            nakshatras = naks,
            yogas = yogas,
            karanas = karanas,
            moonRashis = moonRashis,
            sunRashi = Names.rashi[sunRashiIdx],
            sankranti = sankranti,
            ayana = ayana,
            rahuKaal = TimeRange(day.rahuKaalStart, day.rahuKaalEnd),
            gulikaKaal = dayPart(day.sunrise, day.sunset, GULIKA_PART[w]),
            yamaganda = dayPart(day.sunrise, day.sunset, YAMAGANDA_PART[w]),
            abhijit = if (w == 3) null else abhijit(day.sunrise, day.sunset),
            festivals = festivals(date, loc, zenith, lunar, kshaya(lunar.tithi, tithis)),
        )
    }

    fun summary(date: LocalDate, loc: Location, zenith: Double = SunCalculator.ZENITH_UPPER_LIMB): DaySummary? {
        val day = ChoghadiyaCalculator.compute(date, loc, zenith) ?: return null
        val riseJd = Astro.jdUT(day.sunrise.toInstant())
        val nextJd = Astro.jdUT(day.nextSunrise.toInstant())
        val lunar = lunarDate(riseJd, loc.zoneId)
        val nextTithi = floor(Astro.elongation(nextJd) / TITHI).toInt().coerceIn(0, 29)
        val ksh = if ((nextTithi - lunar.tithi + 30) % 30 == 2) (lunar.tithi + 1) % 30 else null
        return DaySummary(date, lunar, ksh, festivals(date, loc, zenith, lunar, ksh))
    }

    /** Lunar month and year for the Hindu day whose sunrise is [riseJd]. */
    fun lunarDate(riseJd: Double, zone: ZoneId): LunarDate {
        val e = Astro.elongation(riseJd)
        val tithi = floor(e / TITHI).toInt().coerceIn(0, 29)
        val prevNewMoon = Astro.solve(elong, 0.0, riseJd - e / 12.19)
        val nextNewMoon = Astro.solve(elong, 0.0, riseJd + (360.0 - e) / 12.19)
        val r1 = floor(Astro.sunSidereal(prevNewMoon) / RASHI).toInt()
        val r2 = floor(Astro.sunSidereal(nextNewMoon) / RASHI).toInt()
        // Amanta month is named after the Sun's rashi at the new moon that begins it:
        // Sun in Meena → Chaitra, Mesha → Vaishakha, ... A month with no sankranti (same rashi
        // at both new moons) is Adhik and carries the name of the following month.
        val month = (r1 + 1) % 12 + 1
        val adhik = r1 == r2

        val nm = Astro.instant(prevNewMoon).atZone(zone).toLocalDate()
        // Shaka year (Chaitradi) of the month; Pausha/Magha/Phalguna that begin in Jan-Jun belong to the previous year.
        val shaka = nm.year - 78 - if (month >= 10 && nm.monthValue <= 6) 1 else 0
        val chaitradiVs = shaka + 135
        // Gujarati Vikram Samvat starts on Kartik Shukla Pratipada (month 8).
        val vs = if (month <= 7) chaitradiVs - 1 else chaitradiVs
        return LunarDate(month, adhik, tithi, vs, shaka)
    }

    /** Elements running at sunrise and those that start before the next sunrise. */
    private fun periods(
        angle: (Double) -> Double,
        unit: Double,
        meanRate: Double,
        startJd: Double,
        endJd: Double,
        zone: ZoneId,
        name: (Int) -> Name,
    ): List<Period> {
        val count = Math.round(360.0 / unit).toInt()
        val out = mutableListOf<Period>()
        var from = startJd
        var guard = 0
        while (guard++ < 8) {
            val (idx, endJdOf) = Astro.nextBoundary(angle, unit, meanRate, from)
            val i = ((idx % count) + count) % count
            out += Period(i, name(i), Astro.instant(endJdOf).atZone(zone))
            if (endJdOf >= endJd) break
            from = endJdOf + 1e-6
        }
        return out
    }

    private fun dayPart(rise: ZonedDateTime, set: ZonedDateTime, part1: Int): TimeRange {
        val len = Duration.between(rise, set).toMillis() / 8.0
        return TimeRange(
            rise.plusNanos((len * (part1 - 1) * 1e6).toLong()),
            rise.plusNanos((len * part1 * 1e6).toLong()),
        )
    }

    /** 8th of the 15 day-muhurtas (the one around local noon). */
    private fun abhijit(rise: ZonedDateTime, set: ZonedDateTime): TimeRange {
        val m = Duration.between(rise, set).toMillis() / 15.0
        return TimeRange(rise.plusNanos((m * 7 * 1e6).toLong()), rise.plusNanos((m * 8 * 1e6).toLong()))
    }

    // 1-based eighth of daytime, Sunday first.
    private val GULIKA_PART = intArrayOf(7, 6, 5, 4, 3, 2, 1)
    private val YAMAGANDA_PART = intArrayOf(5, 4, 3, 2, 1, 7, 6)

    fun gulikaPart(w: Int) = GULIKA_PART[w]
    fun yamagandaPart(w: Int) = YAMAGANDA_PART[w]

    /** A tithi that starts after sunrise and ends before the next sunrise. */
    private fun kshaya(udaya: Int, tithis: List<Period>): Int? =
        if (tithis.size >= 3) tithis[1].index.takeIf { it != udaya } else null

    /**
     * Festivals of the day from [FestivalCalculator] plus the monthly observances
     * (Purnima, Amas, Chaturthi, Pradosh) of the udaya tithi and of a kshaya tithi.
     */
    private fun festivals(date: LocalDate, loc: Location, zenith: Double, l: LunarDate, kshaya: Int?): List<Festival> {
        val names = mutableListOf<Name>()
        if (kshaya != null) tithiFestival(kshaya, names)
        tithiFestival(l.tithi, names)
        val year = FestivalCalculator.on(date, loc, zenith)
        val monthly = names.distinct().map { Festival(date, it, FestivalCategory.MONTHLY) }
        return year + monthly
    }

    private fun tithiFestival(tithi: Int, out: MutableList<Name>) {
        when (tithi) {
            14 -> out += Name("પૂનમ", "Purnima")
            29 -> out += Name("અમાસ", "Amavasya")
            3 -> out += Name("વિનાયક ચોથ", "Vinayaka Chaturthi")
            18 -> out += Name("સંકષ્ટ ચોથ", "Sankashti Chaturthi")
            12, 27 -> out += Name("પ્રદોષ (તેરસ)", "Pradosh (Trayodashi)")
        }
    }
}
