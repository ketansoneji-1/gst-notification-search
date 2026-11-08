package com.kbsc.choghadiya.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Quality of a Choghadiya as per traditional Gujarati panchang. */
enum class Nature(val english: String, val gujarati: String) {
    BEST("Best", "ઉત્તમ"),
    GOOD("Good", "શુભ"),
    NEUTRAL("Neutral", "સામાન્ય"),
    BAD("Inauspicious", "અશુભ"),
}

/**
 * The seven Choghadiya. Declaration order is the fixed cyclic order used for DAY
 * choghadiya: Udveg → Char → Labh → Amrut → Kaal → Shubh → Rog → (Udveg ...).
 * This is the planetary order Sun → Venus → Mercury → Moon → Saturn → Jupiter → Mars
 * (the "Hora" sequence).
 */
enum class ChoghadiyaType(
    val english: String,
    val gujarati: String,
    val planetEnglish: String,
    val planetGujarati: String,
    val nature: Nature,
    val suitableFor: String,
    val suitableForGujarati: String,
) {
    UDVEG("Udveg", "ઉદ્વેગ", "Sun", "સૂર્ય", Nature.BAD,
        "Generally avoided; traditionally acceptable only for Government-related work",
        "સામાન્ય રીતે ત્યાજ્ય; ફક્ત સરકારી કામ માટે ગ્રાહ્ય"),
    CHAR("Char", "ચલ", "Venus", "શુક્ર", Nature.NEUTRAL,
        "Travel, vehicles, movable / changing activities",
        "પ્રવાસ, વાહન તથા ચલિત કાર્યો માટે"),
    LABH("Labh", "લાભ", "Mercury", "બુધ", Nature.GOOD,
        "Business, trade, starting education, gains",
        "વેપાર, ધંધો, વિદ્યારંભ, લાભદાયક કાર્યો"),
    AMRUT("Amrut", "અમૃત", "Moon", "ચંદ્ર", Nature.BEST,
        "All auspicious work",
        "સર્વ શુભ કાર્યો માટે ઉત્તમ"),
    KAAL("Kaal", "કાળ", "Saturn", "શનિ", Nature.BAD,
        "Avoid auspicious work",
        "શુભ કાર્ય માટે ત્યાજ્ય"),
    SHUBH("Shubh", "શુભ", "Jupiter", "ગુરુ", Nature.GOOD,
        "Marriage, religious ceremonies, auspicious functions",
        "લગ્ન, ધાર્મિક તથા માંગલિક કાર્યો"),
    ROG("Rog", "રોગ", "Mars", "મંગળ", Nature.BAD,
        "Avoid auspicious work",
        "શુભ કાર્ય માટે ત્યાજ્ય");

    val isAuspicious: Boolean get() = nature == Nature.BEST || nature == Nature.GOOD
}

data class Slot(
    val index: Int,             // 1..8 within day or night
    val isDay: Boolean,
    val type: ChoghadiyaType,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    fun contains(t: Instant): Boolean = !t.isBefore(start.toInstant()) && t.isBefore(end.toInstant())
}

data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
)

/** All Choghadiya for one Hindu day (sunrise of [date] to sunrise of the next date). */
data class DayChoghadiya(
    val date: LocalDate,
    val location: Location,
    val sunrise: ZonedDateTime,
    val sunset: ZonedDateTime,
    val nextSunrise: ZonedDateTime,
    val daySlots: List<Slot>,
    val nightSlots: List<Slot>,
    val rahuKaalStart: ZonedDateTime,
    val rahuKaalEnd: ZonedDateTime,
) {
    val allSlots: List<Slot> get() = daySlots + nightSlots
    fun slotAt(t: Instant): Slot? = allSlots.firstOrNull { it.contains(t) }
}

object ChoghadiyaCalculator {

    private val ORDER = ChoghadiyaType.values()

    /** Weekday index with Sunday = 0 ... Saturday = 6. */
    fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /**
     * First DAY choghadiya index in [ORDER] for weekday w (Sun=0).
     * Sun: Udveg, Mon: Amrut, Tue: Rog, Wed: Labh, Thu: Shubh, Fri: Char, Sat: Kaal
     * — i.e. the choghadiya ruled by the lord of the weekday. Equals (3·w) mod 7.
     */
    fun dayStartIndex(weekday: Int): Int = (3 * weekday) % 7

    /**
     * First NIGHT choghadiya for weekday w. It is the lord of the 5th weekday counted
     * from the current one (Sun → Thu = Shubh, Mon → Fri = Char, ...), i.e. (dayStart + 5) mod 7.
     */
    fun nightStartIndex(weekday: Int): Int = (dayStartIndex(weekday) + 5) % 7

    /** Day sequence advances +1 in ORDER; night sequence advances −2 (equivalently +5). */
    fun daySequence(weekday: Int): List<ChoghadiyaType> =
        (0 until 8).map { ORDER[(dayStartIndex(weekday) + it) % 7] }

    fun nightSequence(weekday: Int): List<ChoghadiyaType> =
        (0 until 8).map { ORDER[(nightStartIndex(weekday) + 5 * it) % 7] }

    /** Rahu Kaal = this 1-based eighth of daytime. Sun 8, Mon 2, Tue 7, Wed 5, Thu 6, Fri 4, Sat 3. */
    private val RAHU_KAAL_PART = intArrayOf(8, 2, 7, 5, 6, 4, 3)

    fun rahuKaalPart(weekday: Int): Int = RAHU_KAAL_PART[weekday]

    fun compute(
        date: LocalDate,
        location: Location,
        zenith: Double = SunCalculator.ZENITH_UPPER_LIMB,
    ): DayChoghadiya? {
        val today = sun(date, location, zenith) ?: return null
        val tomorrow = sun(date.plusDays(1), location, zenith) ?: return null
        val zone = location.zoneId
        val sunrise = today.sunrise.atZone(zone)
        val sunset = today.sunset.atZone(zone)
        val nextSunrise = tomorrow.sunrise.atZone(zone)
        val w = weekdayIndex(date)

        val daySlots = split(sunrise, sunset, daySequence(w), isDay = true)
        val nightSlots = split(sunset, nextSunrise, nightSequence(w), isDay = false)

        val part = Duration.between(sunrise, sunset).toMillis() / 8.0
        val rk = rahuKaalPart(w) - 1
        val rkStart = sunrise.plusNanos((part * rk * 1_000_000).toLong())
        val rkEnd = sunrise.plusNanos((part * (rk + 1) * 1_000_000).toLong())

        return DayChoghadiya(date, location, sunrise, sunset, nextSunrise, daySlots, nightSlots, rkStart, rkEnd)
    }

    /**
     * Returns the Hindu day (sunrise-to-sunrise) that contains instant [now]. Between
     * midnight and sunrise the running choghadiya belongs to the previous date's night.
     */
    fun hinduDayFor(
        now: Instant,
        location: Location,
        zenith: Double = SunCalculator.ZENITH_UPPER_LIMB,
    ): DayChoghadiya? {
        val civil = now.atZone(location.zoneId).toLocalDate()
        val d = compute(civil, location, zenith) ?: return null
        return if (now.isBefore(d.sunrise.toInstant())) compute(civil.minusDays(1), location, zenith) else d
    }

    private fun sun(date: LocalDate, loc: Location, zenith: Double): SunCalculator.SunTimes? {
        val offsetHours = loc.zoneId.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        return SunCalculator.sunTimes(date, loc.latitude, loc.longitude, offsetHours, zenith)
    }

    private fun split(
        from: ZonedDateTime,
        to: ZonedDateTime,
        sequence: List<ChoghadiyaType>,
        isDay: Boolean,
    ): List<Slot> {
        val totalMs = Duration.between(from, to).toMillis()
        return (0 until 8).map { i ->
            val s = from.plusNanos(totalMs * i / 8 * 1_000_000)
            val e = if (i == 7) to else from.plusNanos(totalMs * (i + 1) / 8 * 1_000_000)
            Slot(i + 1, isDay, sequence[i], s, e)
        }
    }
}
