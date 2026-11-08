package com.kbsc.choghadiya.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PanchangTest {

    private val ist = ZoneId.of("Asia/Kolkata")
    private val ahmedabad = Location("Ahmedabad", 23.0225, 72.5714, ist)

    private fun p(y: Int, m: Int, d: Int) = PanchangCalculator.compute(LocalDate.of(y, m, d), ahmedabad)!!

    private fun t(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(ist)

    private fun assertClose(expected: ZonedDateTime, actual: ZonedDateTime, maxSec: Long, msg: String) {
        val diff = Duration.between(expected, actual).abs().seconds
        assertTrue("$msg: expected $expected, got $actual (diff ${diff}s)", diff <= maxSec)
    }

    /**
     * 208 dates from 2026 to 2046 (every 37 days), reference values from Swiss Ephemeris 2.10
     * (Moshier ephemeris, Lahiri ayanamsa) generated independently of this code.
     */
    @Test
    fun matchesSwissEphemeris2026to2046() {
        val lines = javaClass.getResourceAsStream("/swisseph_ahmedabad.csv")!!.bufferedReader().readLines()
        var worstTithi = 0L
        var worstNak = 0L
        var worstMoonrise = 0L
        var n = 0
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val c = line.split(",")
            val date = LocalDate.parse(c[0])
            val pc = PanchangCalculator.compute(date, ahmedabad)
            assertNotNull(pc)
            pc!!
            assertClose(t(c[1]), pc.sunrise, 60, "$date sunrise")
            assertClose(t(c[2]), pc.sunset, 60, "$date sunset")

            assertEquals("$date tithi", c[3].toInt(), pc.tithis.first().index)
            assertClose(t(c[4]), pc.tithis.first().end, 90, "$date tithi end")
            worstTithi = maxOf(worstTithi, Duration.between(t(c[4]), pc.tithis.first().end).abs().seconds)

            assertEquals("$date nakshatra", c[5].toInt(), pc.nakshatras.first().index)
            assertClose(t(c[6]), pc.nakshatras.first().end, 90, "$date nakshatra end")
            worstNak = maxOf(worstNak, Duration.between(t(c[6]), pc.nakshatras.first().end).abs().seconds)

            assertEquals("$date yoga", c[7].toInt(), pc.yogas.first().index)
            assertClose(t(c[8]), pc.yogas.first().end, 90, "$date yoga end")

            if (c[9] != "-" && pc.moonrise != null) {
                assertClose(t(c[9]), pc.moonrise!!, 180, "$date moonrise")
                worstMoonrise = maxOf(worstMoonrise, Duration.between(t(c[9]), pc.moonrise).abs().seconds)
            }
            if (c[10] != "-" && pc.moonset != null) assertClose(t(c[10]), pc.moonset!!, 180, "$date moonset")
            n++
        }
        println("Checked $n dates. Worst tithi ${worstTithi}s, nakshatra ${worstNak}s, moonrise ${worstMoonrise}s")
        assertEquals(208, n)
    }

    @Test
    fun diwali2026AndGujaratiNewYear() {
        // 8 Nov 2026 (Diwali): Chaturdashi at sunrise, Amavasya begins during the day.
        val diwali = p(2026, 11, 8)
        assertEquals(28, diwali.lunar.tithi)
        assertEquals(29, diwali.tithis[1].index)
        // 9 Nov 2026: Aso Vad Amas (udaya tithi), VS 2082.
        val l = p(2026, 11, 9).lunar
        assertEquals(7, l.monthIndex)   // Ashwin (Aso)
        assertEquals(29, l.tithi)       // Amavasya
        assertFalse(l.isAdhik)
        assertEquals(2082, l.vikramSamvat)
        assertEquals(1948, l.shakaSamvat)
        assertEquals("આસો વદ અમાસ", l.longGu())
        // 10 Nov 2026: Kartik Sud Ekam, Gujarati New Year, VS 2083.
        val ny = p(2026, 11, 10)
        assertEquals(8, ny.lunar.monthIndex)
        assertEquals(0, ny.lunar.tithi)
        assertEquals(2083, ny.lunar.vikramSamvat)
        assertTrue(ny.festivals.any { it.en == "Gujarati New Year" })
    }

    @Test
    fun gujaratiNewYear2025StartsVs2082() {
        // Bestu Varas: 22 Oct 2025 = Kartik Sud Ekam, VS 2082
        val l = p(2025, 10, 22).lunar
        assertEquals(8, l.monthIndex)
        assertEquals(0, l.tithi)
        assertEquals(2082, l.vikramSamvat)
        val before = p(2025, 10, 20).lunar
        assertEquals(7, before.monthIndex)
        assertEquals(2081, before.vikramSamvat)
    }

    @Test
    fun adhikJyeshtha2026() {
        // 2026 has Adhik Jyeshtha (mid May to mid June).
        val l = p(2026, 5, 30).lunar
        assertEquals(3, l.monthIndex)
        assertTrue(l.isAdhik)
        val nija = p(2026, 6, 25).lunar
        assertEquals(3, nija.monthIndex)
        assertFalse(nija.isAdhik)
    }

    @Test
    fun kshayaEkadashiIsStillMarked() {
        // Nov 2026: Kartik Shukla Ekadashi starts after sunrise of 20 Nov and ends before the next sunrise.
        val s = PanchangCalculator.summary(LocalDate.of(2026, 11, 20), ahmedabad)!!
        assertEquals(9, s.lunar.tithi)
        assertEquals(10, s.kshayaTithi)
        assertTrue(s.festivals.any { it.en == "Ekadashi" })
        assertTrue(p(2026, 11, 20).festivals.any { it.en == "Ekadashi" })
    }

    @Test
    fun everyEkadashiIn21YearsIsMarkedOnce() {
        // Each lunar fortnight has exactly one day flagged Ekadashi (two only when it is vriddhi).
        var date = LocalDate.of(2026, 1, 1)
        var flagged = 0
        while (date.isBefore(LocalDate.of(2047, 1, 1))) {
            if (PanchangCalculator.summary(date, ahmedabad)!!.festivals.any { it.en == "Ekadashi" }) flagged++
            date = date.plusDays(1)
        }
        val fortnights = 21 * 365.25 / 14.765
        assertTrue("flagged $flagged vs ${fortnights.toInt()} fortnights", flagged >= fortnights.toInt() - 1 && flagged <= fortnights * 1.06)
    }

    @Test
    fun makarSankrantiMid_January() {
        val found = (12..16).mapNotNull { d -> p(2027, 1, d).sankranti?.let { d to it } }
        assertEquals(1, found.size)
        assertEquals(9, found.first().second.index) // Makara
    }

    @Test
    fun everyDayFor21YearsIsConsistent() {
        // Each day: tithi advances by 0, 1 or 2; month and year never go backwards across new moons.
        var date = LocalDate.of(2026, 1, 1)
        var prev = PanchangCalculator.summary(date, ahmedabad)!!.lunar
        var adhikMonths = 0
        while (date.isBefore(LocalDate.of(2047, 1, 1))) {
            date = date.plusDays(1)
            val cur = PanchangCalculator.summary(date, ahmedabad)!!.lunar
            val step = (cur.tithi - prev.tithi + 30) % 30
            assertTrue("$date tithi step $step", step in 0..2)
            if (cur.tithi < prev.tithi) { // new lunar month started
                if (cur.isAdhik) adhikMonths++
                assertTrue("$date VS", cur.vikramSamvat >= prev.vikramSamvat)
            }
            prev = cur
        }
        // About 7 or 8 adhik months occur in any 21-year span (19-year Metonic cycle has 7).
        assertTrue("adhik months $adhikMonths", adhikMonths in 7..8)
    }

    @Test
    fun kaalPeriodsAreDistinctEighths() {
        for (w in 0..6) {
            val parts = setOf(ChoghadiyaCalculator.rahuKaalPart(w), PanchangCalculator.gulikaPart(w), PanchangCalculator.yamagandaPart(w))
            assertEquals("weekday $w", 3, parts.size)
        }
        val pc = p(2026, 11, 8) // Sunday: Rahu 8th, Yamaganda 5th, Gulika 7th part
        val len = Duration.between(pc.sunrise, pc.sunset).toMillis() / 8
        assertClose(pc.sunrise.plusNanos(len * 4 * 1_000_000), pc.yamaganda.start, 1, "yamaganda")
        assertClose(pc.sunrise.plusNanos(len * 6 * 1_000_000), pc.gulikaKaal.start, 1, "gulika")
        assertNotNull(pc.abhijit)
        assertEquals(null, p(2026, 11, 11).abhijit) // Wednesday
    }
}
