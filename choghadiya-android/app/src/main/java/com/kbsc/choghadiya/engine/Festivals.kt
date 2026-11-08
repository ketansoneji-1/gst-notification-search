package com.kbsc.choghadiya.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Collections
import kotlin.math.floor

enum class FestivalCategory { MAJOR, VRAT, EKADASHI, JAYANTI, SANKRANTI, NATIONAL, PERIOD, MONTHLY }

data class Festival(val date: LocalDate, val name: Name, val category: FestivalCategory)

/**
 * Time of day at which the tithi must be running for the festival to fall on that day
 * (Dharmasindhu / Nirnayasindhu practice followed by Gujarati panchangs).
 */
enum class Observance {
    UDAYA,      // at sunrise
    MADHYAHNA,  // midday: 3rd of 5 parts of daytime
    APARAHNA,   // afternoon: 4th of 5 parts of daytime
    PRADOSH,    // first 3 muhurtas (~2 h 24 m) after sunset
    NISHITA,    // midnight: 8th of 15 night muhurtas
}

private class FestivalDef(
    val month: Int,          // 1 = Chaitra ... 12 = Phalguna (Amanta, as in Gujarat)
    val tithi: Int,          // 0..29
    val rule: Observance,
    val name: Name,
    val category: FestivalCategory,
)

/**
 * Hindu festivals, vrats, jayantis, Ekadashis, Sankrantis and Indian national days for any
 * year, calculated by rule (no year tables), so it keeps working for future years.
 */
object FestivalCalculator {

    private fun n(gu: String, en: String) = Name(gu, en)
    private val M = FestivalCategory.MAJOR
    private val V = FestivalCategory.VRAT
    private val J = FestivalCategory.JAYANTI

    // Month numbers: 1 Chaitra, 2 Vaishakha, 3 Jyeshtha, 4 Ashadha, 5 Shravana, 6 Bhadrapada,
    // 7 Ashwin (Aso), 8 Kartik, 9 Margashirsha, 10 Pausha, 11 Magha, 12 Phalguna.
    // Tithi: 0..14 Shukla (Sud) 1..15, 15..29 Krishna (Vad) 1..15 (29 = Amas).
    private val DEFS = listOf(
        // ---- Chaitra
        FestivalDef(1, 0, Observance.UDAYA, n("ચૈત્રી નવરાત્રી પ્રારંભ / ગુડી પડવો / ચેટીચંડ", "Chaitra Navratri begins / Gudi Padwa / Cheti Chand"), M),
        FestivalDef(1, 7, Observance.UDAYA, n("ચૈત્રી દુર્ગાષ્ટમી", "Chaitra Durga Ashtami"), V),
        FestivalDef(1, 8, Observance.MADHYAHNA, n("રામ નવમી", "Ram Navami"), M),
        FestivalDef(1, 12, Observance.UDAYA, n("મહાવીર જયંતિ", "Mahavir Jayanti"), J),
        FestivalDef(1, 14, Observance.UDAYA, n("હનુમાન જયંતિ", "Hanuman Jayanti"), M),
        // ---- Vaishakha
        FestivalDef(2, 2, Observance.UDAYA, n("અખાત્રીજ (અક્ષય તૃતીયા) / પરશુરામ જયંતિ", "Akshaya Tritiya / Parshuram Jayanti"), M),
        FestivalDef(2, 13, Observance.PRADOSH, n("નૃસિંહ જયંતિ", "Narasimha Jayanti"), J),
        FestivalDef(2, 14, Observance.UDAYA, n("બુદ્ધ પૂર્ણિમા", "Buddha Purnima"), J),
        // ---- Jyeshtha
        FestivalDef(3, 9, Observance.UDAYA, n("ગંગા દશેરા", "Ganga Dussehra"), V),
        FestivalDef(3, 14, Observance.UDAYA, n("વટ સાવિત્રી પૂનમ", "Vat Purnima (Vat Savitri)"), V),
        // ---- Ashadha
        FestivalDef(4, 1, Observance.UDAYA, n("અષાઢી બીજ / જગન્નાથ રથયાત્રા", "Ashadhi Bij / Jagannath Rath Yatra"), M),
        FestivalDef(4, 10, Observance.UDAYA, n("ગૌરી વ્રત (મોળાકત) પ્રારંભ", "Gauri Vrat (Molakat) begins"), V),
        FestivalDef(4, 12, Observance.UDAYA, n("જયા પાર્વતી વ્રત પ્રારંભ", "Jaya Parvati Vrat begins"), V),
        FestivalDef(4, 14, Observance.UDAYA, n("ગુરુ પૂર્ણિમા", "Guru Purnima"), M),
        FestivalDef(4, 29, Observance.UDAYA, n("દિવાસો", "Divaso"), V),
        // ---- Shravana
        FestivalDef(5, 14, Observance.UDAYA, n("રક્ષાબંધન / બળેવ", "Raksha Bandhan / Balev"), M),
        FestivalDef(5, 18, Observance.UDAYA, n("બોળ ચોથ", "Bol Choth"), V),
        FestivalDef(5, 19, Observance.UDAYA, n("નાગ પાંચમ", "Nag Pancham"), V),
        FestivalDef(5, 20, Observance.UDAYA, n("રાંધણ છઠ", "Randhan Chhath"), V),
        FestivalDef(5, 21, Observance.UDAYA, n("શીતળા સાતમ", "Shitala Satam"), V),
        FestivalDef(5, 22, Observance.UDAYA, n("જન્માષ્ટમી", "Janmashtami"), M),
        // ---- Bhadrapada
        FestivalDef(6, 3, Observance.MADHYAHNA, n("ગણેશ ચતુર્થી", "Ganesh Chaturthi"), M),
        FestivalDef(6, 4, Observance.UDAYA, n("ઋષિ પંચમી", "Rishi Panchami"), V),
        FestivalDef(6, 13, Observance.UDAYA, n("અનંત ચતુર્દશી / ગણેશ વિસર્જન", "Anant Chaturdashi / Ganesh Visarjan"), M),
        FestivalDef(6, 14, Observance.UDAYA, n("ભાદરવી પૂનમ / શ્રાદ્ધ પ્રારંભ", "Bhadarvi Purnima / Shraddha begins"), V),
        FestivalDef(6, 29, Observance.UDAYA, n("સર્વપિતૃ અમાસ", "Sarva Pitru Amavasya"), V),
        // ---- Ashwin (Aso)
        FestivalDef(7, 0, Observance.UDAYA, n("શારદીય નવરાત્રી પ્રારંભ (ઘટસ્થાપન)", "Sharad Navratri begins (Ghatasthapana)"), M),
        FestivalDef(7, 7, Observance.UDAYA, n("દુર્ગાષ્ટમી", "Durga Ashtami"), M),
        FestivalDef(7, 8, Observance.UDAYA, n("મહાનવમી", "Maha Navami"), M),
        FestivalDef(7, 9, Observance.APARAHNA, n("દશેરા (વિજયાદશમી)", "Dussehra (Vijaya Dashami)"), M),
        FestivalDef(7, 14, Observance.PRADOSH, n("શરદ પૂનમ", "Sharad Purnima"), M),
        FestivalDef(7, 18, Observance.UDAYA, n("કરવા ચોથ", "Karva Chauth"), V),
        FestivalDef(7, 26, Observance.PRADOSH, n("વાઘ બારસ", "Vagh Baras"), M),
        FestivalDef(7, 27, Observance.PRADOSH, n("ધનતેરસ", "Dhanteras"), M),
        FestivalDef(7, 28, Observance.NISHITA, n("કાળી ચૌદસ", "Kali Chaudas"), M),
        FestivalDef(7, 29, Observance.PRADOSH, n("દિવાળી (લક્ષ્મી પૂજન)", "Diwali (Lakshmi Puja)"), M),
        // ---- Kartik
        FestivalDef(8, 0, Observance.UDAYA, n("નૂતન વર્ષ (બેસતું વર્ષ) / ગોવર્ધન પૂજા", "Gujarati New Year / Govardhan Puja"), M),
        FestivalDef(8, 1, Observance.APARAHNA, n("ભાઈબીજ", "Bhai Bij"), M),
        FestivalDef(8, 4, Observance.UDAYA, n("લાભ પાંચમ", "Labh Pancham"), M),
        FestivalDef(8, 11, Observance.UDAYA, n("તુલસી વિવાહ", "Tulsi Vivah"), V),
        FestivalDef(8, 14, Observance.UDAYA, n("દેવ દિવાળી / કાર્તિકી પૂનમ / ગુરુ નાનક જયંતિ", "Dev Diwali / Kartik Purnima / Guru Nanak Jayanti"), M),
        // ---- Margashirsha
        FestivalDef(9, 10, Observance.UDAYA, n("ગીતા જયંતિ", "Gita Jayanti"), J),
        FestivalDef(9, 14, Observance.UDAYA, n("દત્ત જયંતિ", "Datta Jayanti"), J),
        // ---- Magha
        FestivalDef(11, 4, Observance.UDAYA, n("વસંત પંચમી", "Vasant Panchami"), M),
        FestivalDef(11, 28, Observance.NISHITA, n("મહા શિવરાત્રી", "Maha Shivratri"), M),
        // ---- Phalguna
        FestivalDef(12, 14, Observance.PRADOSH, n("હોલિકા દહન (હોળી)", "Holika Dahan (Holi)"), M),
    )

    /** 24 Ekadashis named by Amanta month: index (month-1)*2 + 0 Shukla / 1 Krishna. */
    private val EKADASHI = listOf(
        "કામદા" to "Kamada", "વરૂથિની" to "Varuthini",
        "મોહિની" to "Mohini", "અપરા" to "Apara",
        "નિર્જળા (ભીમ)" to "Nirjala", "યોગિની" to "Yogini",
        "દેવશયની" to "Devshayani", "કામિકા" to "Kamika",
        "પવિત્રા (પુત્રદા)" to "Shravana Putrada", "અજા" to "Aja",
        "પરિવર્તિની (પાર્શ્વ)" to "Parivartini", "ઇન્દિરા" to "Indira",
        "પાપાંકુશા" to "Papankusha", "રમા" to "Rama",
        "પ્રબોધિની (દેવઊઠી)" to "Prabodhini (Dev Uthi)", "ઉત્પત્તિ" to "Utpanna",
        "મોક્ષદા" to "Mokshada", "સફલા" to "Saphala",
        "પોષ પુત્રદા" to "Pausha Putrada", "ષટ્તિલા" to "Shattila",
        "જયા" to "Jaya", "વિજયા" to "Vijaya",
        "આમલકી" to "Amalaki", "પાપમોચની" to "Papmochani",
    )

    // ------------------------------------------------------------------ per-day data

    private class Day(
        val date: LocalDate,
        val month: Int,
        val adhik: Boolean,
        val vs: Int,
        val tithiRise: Int,
        val tithiNextRise: Int,
        val windows: Array<IntArray>, // tithi sampled across each Observance window (by ordinal)
        val tithiThreePrahar: Int,    // tithi at 3/4 of daytime (used for Holika Dahan)
        val sankranti: Int?,          // rashi entered during this Hindu day
    ) {
        fun coverage(rule: Observance, tithi: Int) = windows[rule.ordinal].count { it == tithi }
    }

    private fun tithiAt(jd: Double) = floor(Astro.elongation(jd) / 12.0).toInt().coerceIn(0, 29)

    private const val SAMPLES = 9

    private fun window(from: Double, to: Double) = IntArray(SAMPLES) { tithiAt(from + (to - from) * (it + 0.5) / SAMPLES) }

    private fun day(date: LocalDate, loc: Location, zenith: Double): Day? {
        val c = ChoghadiyaCalculator.compute(date, loc, zenith) ?: return null
        val rise = Astro.jdUT(c.sunrise.toInstant())
        val set = Astro.jdUT(c.sunset.toInstant())
        val next = Astro.jdUT(c.nextSunrise.toInstant())
        val dayLen = set - rise
        val night = next - set
        val lunar = PanchangCalculator.lunarDate(rise, loc.zoneId)
        val windows = arrayOf(
            intArrayOf(lunar.tithi),                                   // UDAYA
            window(rise + dayLen * 2 / 5, rise + dayLen * 3 / 5),     // MADHYAHNA
            window(rise + dayLen * 3 / 5, rise + dayLen * 4 / 5),     // APARAHNA
            window(set, set + night / 5),                              // PRADOSH (3 of 15 night muhurtas)
            window(set + night * 7 / 15, set + night * 8 / 15),       // NISHITA
        )
        val s0 = floor(Astro.sunSidereal(rise) / 30.0).toInt()
        val s1 = floor(Astro.sunSidereal(next) / 30.0).toInt()
        return Day(date, lunar.monthIndex, lunar.isAdhik, lunar.vikramSamvat, lunar.tithi, tithiAt(next), windows,
            tithiAt(rise + dayLen * 0.75), if (s0 != s1) (s0 + 1) % 12 else null)
    }

    // ------------------------------------------------------------------ public API

    private val cache: MutableMap<String, List<Festival>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Festival>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Festival>>?) = size > 6
        },
    )

    /** All festivals and important days of a Gregorian year, sorted by date. */
    fun forYear(year: Int, loc: Location, zenith: Double = SunCalculator.ZENITH_UPPER_LIMB): List<Festival> {
        val key = "$year|${loc.latitude}|${loc.longitude}|${loc.zoneId.id}|$zenith"
        cache[key]?.let { return it }
        val result = compute(year, loc, zenith)
        cache[key] = result
        return result
    }

    fun on(date: LocalDate, loc: Location, zenith: Double = SunCalculator.ZENITH_UPPER_LIMB): List<Festival> =
        forYear(date.year, loc, zenith).filter { it.date == date }

    private fun compute(year: Int, loc: Location, zenith: Double): List<Festival> {
        // Pad by ~45 days on both sides so festivals near year boundaries resolve correctly.
        val start = LocalDate.of(year, 1, 1).minusDays(45)
        val end = LocalDate.of(year, 12, 31).plusDays(45)
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
            .mapNotNull { day(it, loc, zenith) }.toList()
        val out = mutableListOf<Festival>()

        // Group days into lunar months (consecutive days with the same month / adhik / samvat).
        val groups = mutableListOf<MutableList<Int>>()
        days.indices.forEach { i ->
            val d = days[i]
            val last = groups.lastOrNull()?.let { days[it.last()] }
            if (last != null && last.month == d.month && last.adhik == d.adhik && last.vs == d.vs) groups.last() += i
            else groups += mutableListOf(i)
        }

        for (g in groups) {
            val first = days[g.first()]
            for (def in DEFS) {
                if (def.month != first.month || first.adhik) continue
                var idx = find(days, g, def.tithi, def.rule) ?: continue
                // Holika Dahan: if Purnima on the next day lasts beyond 3 prahars (3/4 of daytime),
                // Holika Dahan moves to that day's evening (Dharmasindhu).
                if (def.month == 12 && def.tithi == 14 && idx + 1 < days.size && days[idx + 1].tithiThreePrahar == 14) idx++
                out += Festival(days[idx].date, def.name, def.category)
            }
            // Named Ekadashis (Shukla 11 = 10, Krishna 11 = 25)
            for ((k, t) in listOf(0 to 10, 1 to 25)) {
                val idx = find(days, g, t, Observance.UDAYA) ?: continue
                val (gu, en) = if (first.adhik) (if (k == 0) "પદ્મિની" to "Padmini" else "પરમા" to "Parama")
                else EKADASHI[(first.month - 1) * 2 + k]
                out += Festival(days[idx].date, n("$gu એકાદશી", "$en Ekadashi"), FestivalCategory.EKADASHI)
            }
            if (first.adhik) out += Festival(first.date, n("અધિક (પુરુષોત્તમ) માસ પ્રારંભ", "Adhik (Purushottam) Maas begins"), FestivalCategory.PERIOD)
            if (first.month == 5 && !first.adhik) out += Festival(first.date, n("શ્રાવણ માસ પ્રારંભ", "Shravan month begins"), FestivalCategory.PERIOD)
        }

        // Holi (Dhuleti) is the day after Holika Dahan.
        out.filter { it.name.en.startsWith("Holika Dahan") }.forEach {
            out += Festival(it.date.plusDays(1), n("ધુળેટી (રંગોની હોળી)", "Dhuleti (Holi, festival of colours)"), M)
        }
        // Navratri days between the start and Navami.
        navratri(out, "Sharad Navratri begins", "Maha Navami", "નવરાત્રી", "Navratri")
        navratri(out, "Chaitra Navratri begins", "Ram Navami", "ચૈત્રી નવરાત્રી", "Chaitra Navratri")

        // Sankrantis
        days.forEach { d ->
            val r = d.sankranti ?: return@forEach
            if (r == 9) {
                out += Festival(d.date, n("મકર સંક્રાંતિ / ઉત્તરાયણ", "Makar Sankranti / Uttarayan"), M)
                out += Festival(d.date.plusDays(1), n("વાસી ઉત્તરાયણ", "Vasi Uttarayan"), FestivalCategory.SANKRANTI)
            } else {
                val nm = Names.rashi[r]
                out += Festival(d.date, n("${nm.gu} સંક્રાંતિ", "${nm.en.substringBefore(" (")} Sankranti"), FestivalCategory.SANKRANTI)
            }
        }

        // Indian national days and other fixed-date observances
        fun fixed(m: Int, d: Int, gu: String, en: String, c: FestivalCategory = FestivalCategory.NATIONAL) {
            out += Festival(LocalDate.of(year, m, d), n(gu, en), c)
        }
        fixed(1, 1, "અંગ્રેજી નવું વર્ષ", "New Year's Day", FestivalCategory.PERIOD)
        fixed(1, 26, "પ્રજાસત્તાક દિન", "Republic Day")
        fixed(4, 14, "આંબેડકર જયંતિ", "Ambedkar Jayanti")
        fixed(5, 1, "ગુજરાત સ્થાપના દિન", "Gujarat Day")
        fixed(8, 15, "સ્વાતંત્ર્ય દિન", "Independence Day")
        fixed(10, 2, "ગાંધી જયંતિ", "Gandhi Jayanti")
        fixed(10, 31, "સરદાર પટેલ જયંતિ (રાષ્ટ્રીય એકતા દિવસ)", "Sardar Patel Jayanti (National Unity Day)")
        fixed(12, 25, "નાતાલ (ક્રિસમસ)", "Christmas")
        out += Festival(easter(year).minusDays(2), n("ગુડ ફ્રાઇડે", "Good Friday"), FestivalCategory.NATIONAL)

        return out.filter { it.date.year == year }
            .distinctBy { it.date to it.name }
            .sortedWith(compareBy({ it.date }, { it.category.ordinal }))
    }

    /**
     * Index of the day in lunar-month group [g] on which [tithi] is observed under [rule]:
     * - UDAYA: the first day with the tithi at sunrise;
     * - other rules: the day on which the tithi covers the larger part of the rule's time window
     *   (vyapti), the earlier day on a tie;
     * - if the tithi touches no sunrise / window (kshaya), the day on which it occurs, which for
     *   Pratipada can be the last day of the previous month.
     */
    private fun find(days: List<Day>, g: List<Int>, tithi: Int, rule: Observance): Int? {
        if (rule == Observance.UDAYA) {
            g.firstOrNull { days[it].tithiRise == tithi }?.let { return it }
        } else {
            var best: Int? = null
            var bestCov = 0
            for (i in g) {
                val c = days[i].coverage(rule, tithi)
                if (c > bestCov) { best = i; bestCov = c }
            }
            if (best != null) return best
        }
        val candidates = listOfNotNull((g.first() - 1).takeIf { it >= 0 }) + g
        return candidates.firstOrNull { i ->
            val d = days[i]
            val span = (d.tithiNextRise - d.tithiRise + 30) % 30
            val prevMonthDay = i < g.first()
            (0..span).any { k ->
                val raw = d.tithiRise + k
                if (prevMonthDay) raw >= 30 && raw - 30 == tithi else raw < 30 && raw == tithi
            }
        }
    }

    private fun navratri(out: MutableList<Festival>, startEn: String, endEn: String, gu: String, en: String) {
        val starts = out.filter { it.name.en.startsWith(startEn) }
        for (s in starts) {
            val end = out.firstOrNull { it.name.en.startsWith(endEn) && !it.date.isBefore(s.date) && it.date.isBefore(s.date.plusDays(12)) }
                ?: continue
            var d = s.date
            var k = 1
            while (!d.isAfter(end.date)) {
                out += Festival(d, n("$gu – દિવસ $k", "$en – Day $k"), FestivalCategory.PERIOD)
                d = d.plusDays(1); k++
            }
        }
    }

    /** Gregorian Easter Sunday (anonymous Gregorian algorithm, Meeus). */
    fun easter(y: Int): LocalDate {
        val a = y % 19
        val b = y / 100
        val c = y % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(y, month, day).also { check(it.dayOfWeek == DayOfWeek.SUNDAY) }
    }
}
