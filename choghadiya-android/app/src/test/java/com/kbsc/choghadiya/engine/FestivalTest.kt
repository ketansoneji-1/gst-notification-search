package com.kbsc.choghadiya.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FestivalTest {

    private val ahmedabad = Location("Ahmedabad", 23.0225, 72.5714, ZoneId.of("Asia/Kolkata"))

    private fun dateOf(year: Int, prefix: String): LocalDate? =
        FestivalCalculator.forYear(year, ahmedabad).firstOrNull { it.name.en.startsWith(prefix) }?.date

    /** Published festival dates (Gujarat / India) for 2025 and 2026. */
    @Test
    fun knownFestivalDates() {
        val expected = listOf(
            "2025-01-14" to "Makar Sankranti", "2025-02-02" to "Vasant Panchami", "2025-02-26" to "Maha Shivratri",
            "2025-03-13" to "Holika Dahan", "2025-03-14" to "Dhuleti", "2025-03-30" to "Chaitra Navratri begins",
            "2025-04-06" to "Ram Navami", "2025-04-12" to "Hanuman Jayanti", "2025-04-30" to "Akshaya Tritiya",
            "2025-06-27" to "Ashadhi Bij", "2025-07-10" to "Guru Purnima", "2025-08-09" to "Raksha Bandhan",
            "2025-08-16" to "Janmashtami", "2025-08-27" to "Ganesh Chaturthi", "2025-09-22" to "Sharad Navratri begins",
            "2025-10-02" to "Dussehra", "2025-10-17" to "Vagh Baras", "2025-10-18" to "Dhanteras",
            "2025-10-19" to "Kali Chaudas", "2025-10-20" to "Diwali", "2025-10-22" to "Gujarati New Year",
            "2025-10-23" to "Bhai Bij", "2025-10-26" to "Labh Pancham", "2025-11-05" to "Dev Diwali",
            "2026-01-14" to "Makar Sankranti", "2026-01-23" to "Vasant Panchami", "2026-02-15" to "Maha Shivratri",
            "2026-03-03" to "Holika Dahan", "2026-03-04" to "Dhuleti", "2026-03-19" to "Chaitra Navratri begins",
            "2026-07-16" to "Ashadhi Bij", "2026-07-29" to "Guru Purnima", "2026-08-28" to "Raksha Bandhan",
            "2026-09-04" to "Janmashtami", "2026-09-14" to "Ganesh Chaturthi", "2026-10-11" to "Sharad Navratri begins",
            "2026-10-20" to "Dussehra", "2026-11-06" to "Dhanteras", "2026-11-08" to "Diwali",
            "2026-11-10" to "Gujarati New Year", "2026-11-11" to "Bhai Bij",
        )
        val wrong = expected.mapNotNull { (d, name) ->
            val date = LocalDate.parse(d)
            val got = dateOf(date.year, name)
            if (got != date) "$name: expected $d, got $got" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun everyMajorFestivalOccursOnceEachYear2026to2046() {
        val oncePerYear = listOf(
            "Holika Dahan", "Chaitra Navratri begins", "Ram Navami", "Hanuman Jayanti", "Akshaya Tritiya",
            "Ashadhi Bij", "Guru Purnima", "Raksha Bandhan", "Janmashtami", "Ganesh Chaturthi",
            "Sharad Navratri begins", "Dussehra", "Dhanteras", "Diwali", "Gujarati New Year", "Bhai Bij",
            "Maha Shivratri", "Vasant Panchami", "Makar Sankranti", "Republic Day", "Good Friday",
        )
        for (y in 2026..2046) {
            val list = FestivalCalculator.forYear(y, ahmedabad)
            for (f in oncePerYear) {
                assertEquals("$y $f", 1, list.count { it.name.en.startsWith(f) })
            }
            // Festival order within the Diwali week
            val dh = list.first { it.name.en.startsWith("Dhanteras") }.date
            val dw = list.first { it.name.en.startsWith("Diwali") }.date
            val ny = list.first { it.name.en.startsWith("Gujarati New Year") }.date
            val bb = list.first { it.name.en.startsWith("Bhai Bij") }.date
            assertTrue("$y Diwali order $dh $dw $ny $bb", dh < dw && dw < ny && !bb.isBefore(ny) && bb <= ny.plusDays(2))
            // Navratri leads to Dussehra 9 or 10 days later (8-11 with kshaya/vriddhi tithis)
            val nv = list.first { it.name.en.startsWith("Sharad Navratri begins") }.date
            val du = list.first { it.name.en.startsWith("Dussehra") }.date
            assertTrue("$y Navratri $nv Dussehra $du", du.toEpochDay() - nv.toEpochDay() in 8..11)
            // Dhuleti is the day after Holika Dahan; Makar Sankranti on 14 or 15 January.
            val hd = list.first { it.name.en.startsWith("Holika Dahan") }.date
            assertEquals(hd.plusDays(1), list.first { it.name.en.startsWith("Dhuleti") }.date)
            val ms = list.first { it.name.en.startsWith("Makar Sankranti") }.date
            assertTrue("$y Makar Sankranti $ms", ms.monthValue == 1 && ms.dayOfMonth in 13..16)
            // 24 or 26 (with Adhik Maas) named Ekadashis, within ±1 of that count across year edges.
            val ek = list.count { it.category == FestivalCategory.EKADASHI }
            assertTrue("$y ekadashis $ek", ek in 23..27)
        }
    }

    @Test
    fun easterDates() {
        assertEquals(LocalDate.of(2025, 4, 20), FestivalCalculator.easter(2025))
        assertEquals(LocalDate.of(2026, 4, 5), FestivalCalculator.easter(2026))
        assertEquals(LocalDate.of(2030, 4, 21), FestivalCalculator.easter(2030))
    }
}
