package com.kbsc.choghadiya.engine

import com.kbsc.choghadiya.engine.ChoghadiyaType.AMRUT
import com.kbsc.choghadiya.engine.ChoghadiyaType.CHAR
import com.kbsc.choghadiya.engine.ChoghadiyaType.KAAL
import com.kbsc.choghadiya.engine.ChoghadiyaType.LABH
import com.kbsc.choghadiya.engine.ChoghadiyaType.ROG
import com.kbsc.choghadiya.engine.ChoghadiyaType.SHUBH
import com.kbsc.choghadiya.engine.ChoghadiyaType.UDVEG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ChoghadiyaTest {

    // Standard printed Gujarati panchang tables (Sunday = index 0).
    private val expectedDay = listOf(
        listOf(UDVEG, CHAR, LABH, AMRUT, KAAL, SHUBH, ROG, UDVEG), // Sunday
        listOf(AMRUT, KAAL, SHUBH, ROG, UDVEG, CHAR, LABH, AMRUT), // Monday
        listOf(ROG, UDVEG, CHAR, LABH, AMRUT, KAAL, SHUBH, ROG),   // Tuesday
        listOf(LABH, AMRUT, KAAL, SHUBH, ROG, UDVEG, CHAR, LABH),  // Wednesday
        listOf(SHUBH, ROG, UDVEG, CHAR, LABH, AMRUT, KAAL, SHUBH), // Thursday
        listOf(CHAR, LABH, AMRUT, KAAL, SHUBH, ROG, UDVEG, CHAR),  // Friday
        listOf(KAAL, SHUBH, ROG, UDVEG, CHAR, LABH, AMRUT, KAAL),  // Saturday
    )
    private val expectedNight = listOf(
        listOf(SHUBH, AMRUT, CHAR, ROG, KAAL, LABH, UDVEG, SHUBH), // Sunday
        listOf(CHAR, ROG, KAAL, LABH, UDVEG, SHUBH, AMRUT, CHAR),  // Monday
        listOf(KAAL, LABH, UDVEG, SHUBH, AMRUT, CHAR, ROG, KAAL),  // Tuesday
        listOf(UDVEG, SHUBH, AMRUT, CHAR, ROG, KAAL, LABH, UDVEG), // Wednesday
        listOf(AMRUT, CHAR, ROG, KAAL, LABH, UDVEG, SHUBH, AMRUT), // Thursday
        listOf(ROG, KAAL, LABH, UDVEG, SHUBH, AMRUT, CHAR, ROG),   // Friday
        listOf(LABH, UDVEG, SHUBH, AMRUT, CHAR, ROG, KAAL, LABH),  // Saturday
    )

    private val ahmedabad = Location("Ahmedabad", 23.0225, 72.5714, ZoneId.of("Asia/Kolkata"))

    @Test
    fun daySequencesMatchPanchangTable() {
        for (w in 0..6) assertEquals("weekday $w", expectedDay[w], ChoghadiyaCalculator.daySequence(w))
    }

    @Test
    fun nightSequencesMatchPanchangTable() {
        for (w in 0..6) assertEquals("weekday $w", expectedNight[w], ChoghadiyaCalculator.nightSequence(w))
    }

    @Test
    fun weekdayIndexSundayIsZero() {
        assertEquals(0, ChoghadiyaCalculator.weekdayIndex(LocalDate.of(2026, 11, 8))) // Sunday
        assertEquals(1, ChoghadiyaCalculator.weekdayIndex(LocalDate.of(2026, 11, 9))) // Monday
        assertEquals(6, ChoghadiyaCalculator.weekdayIndex(LocalDate.of(2026, 11, 14))) // Saturday
    }

    @Test
    fun rahuKaalParts() {
        // Sun 8th, Mon 2nd, Tue 7th, Wed 5th, Thu 6th, Fri 4th, Sat 3rd part of daytime.
        assertEquals(listOf(8, 2, 7, 5, 6, 4, 3), (0..6).map { ChoghadiyaCalculator.rahuKaalPart(it) })
    }

    /** Reference values generated with the independent Python "astral" 3.2 library (upper limb + refraction). */
    @Test
    fun sunriseSunsetMatchesIndependentReference() {
        data class Ref(val lat: Double, val lon: Double, val zone: String, val date: LocalDate, val rise: String, val set: String)
        val refs = listOf(
            Ref(23.0225, 72.5714, "Asia/Kolkata", LocalDate.of(2026, 11, 8), "06:48:44", "17:57:47"),
            Ref(23.0225, 72.5714, "Asia/Kolkata", LocalDate.of(2026, 1, 14), "07:23:04", "18:14:27"),
            Ref(23.0225, 72.5714, "Asia/Kolkata", LocalDate.of(2026, 6, 21), "05:55:09", "19:27:49"),
            Ref(19.0760, 72.8777, "Asia/Kolkata", LocalDate.of(2026, 11, 8), "06:42:12", "18:01:55"),
            Ref(19.0760, 72.8777, "Asia/Kolkata", LocalDate.of(2026, 1, 14), "07:14:48", "18:20:15"),
            Ref(51.5074, -0.1278, "Europe/London", LocalDate.of(2026, 6, 21), "04:43:27", "21:21:12"),
            Ref(51.5074, -0.1278, "Europe/London", LocalDate.of(2026, 1, 14), "08:00:36", "16:18:55"),
            Ref(-33.8688, 151.2093, "Australia/Sydney", LocalDate.of(2026, 11, 8), "05:49:03", "19:29:06"),
            Ref(-33.8688, 151.2093, "Australia/Sydney", LocalDate.of(2026, 6, 21), "07:00:11", "16:53:35"),
            Ref(40.7128, -74.0060, "America/New_York", LocalDate.of(2026, 11, 8), "06:34:52", "16:44:07"),
            Ref(40.7128, -74.0060, "America/New_York", LocalDate.of(2026, 6, 21), "05:25:17", "20:30:29"),
        )
        for (r in refs) {
            val loc = Location("t", r.lat, r.lon, ZoneId.of(r.zone))
            val d = ChoghadiyaCalculator.compute(r.date, loc)
            assertNotNull(d)
            assertWithin(r.rise, d!!.sunrise.toLocalTime(), "${r.zone} ${r.date} sunrise")
            assertWithin(r.set, d.sunset.toLocalTime(), "${r.zone} ${r.date} sunset")
        }
    }

    private fun assertWithin(expected: String, actual: LocalTime, msg: String) {
        val diff = Duration.between(LocalTime.parse(expected), actual).abs().seconds
        assertTrue("$msg: expected $expected got $actual (diff ${diff}s)", diff <= 60)
    }

    @Test
    fun slotsAreContiguousAndEqual() {
        val d = ChoghadiyaCalculator.compute(LocalDate.of(2026, 11, 8), ahmedabad)!!
        assertEquals(8, d.daySlots.size)
        assertEquals(8, d.nightSlots.size)
        assertEquals(d.sunrise, d.daySlots.first().start)
        assertEquals(d.sunset, d.daySlots.last().end)
        assertEquals(d.sunset, d.nightSlots.first().start)
        assertEquals(d.nextSunrise, d.nightSlots.last().end)
        d.allSlots.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        val dayLen = Duration.between(d.sunrise, d.sunset).toMillis() / 8
        d.daySlots.forEach { assertTrue(Math.abs(Duration.between(it.start, it.end).toMillis() - dayLen) <= 1) }
        // Sunday 8 Nov 2026 starts with Udveg and night starts with Shubh
        assertEquals(UDVEG, d.daySlots.first().type)
        assertEquals(SHUBH, d.nightSlots.first().type)
        // Rahu Kaal on Sunday = last (8th) eighth of the day
        assertEquals(d.daySlots[7].start.toEpochSecond(), d.rahuKaalStart.toEpochSecond())
        assertEquals(d.sunset.toEpochSecond(), d.rahuKaalEnd.toEpochSecond())
    }

    @Test
    fun beforeSunriseBelongsToPreviousNight() {
        val zone = ahmedabad.zoneId
        // 03:00 IST on Monday 9 Nov 2026 is still Sunday's night.
        val t = LocalDate.of(2026, 11, 9).atTime(3, 0).atZone(zone).toInstant()
        val d = ChoghadiyaCalculator.hinduDayFor(t, ahmedabad)!!
        assertEquals(LocalDate.of(2026, 11, 8), d.date)
        val slot = d.slotAt(t)!!
        assertTrue(!slot.isDay)
        // 10:00 IST on Monday belongs to Monday's day choghadiya.
        val t2 = LocalDate.of(2026, 11, 9).atTime(10, 0).atZone(zone).toInstant()
        val d2 = ChoghadiyaCalculator.hinduDayFor(t2, ahmedabad)!!
        assertEquals(LocalDate.of(2026, 11, 9), d2.date)
        assertTrue(d2.slotAt(t2)!!.isDay)
    }
}
