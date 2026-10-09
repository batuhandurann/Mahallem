package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AvailabilityCalendarDateTest {
    private fun date(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"), Locale("tr", "TR")).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test fun generatesTenDatesStartingToday() {
        val days = upcomingCalendarDays(date(2026, 10, 8))
        assertEquals(10, days.size)
        assertEquals("2026-10-08", days.first().dateIso)
        assertEquals("Per", days.first().dayName)
        assertEquals("2026-10-17", days.last().dateIso)
    }

    @Test fun marksTurkishWeekends() {
        val days = upcomingCalendarDays(date(2026, 10, 8))
        assertTrue(days.first { it.dateIso == "2026-10-10" }.isWeekend)
        assertTrue(days.first { it.dateIso == "2026-10-11" }.isWeekend)
        assertFalse(days.first { it.dateIso == "2026-10-12" }.isWeekend)
    }

    @Test fun rollsAcrossYearBoundaryWithoutHardcodedDates() {
        val days = upcomingCalendarDays(date(2026, 12, 29))
        assertEquals("2026-12-29", days.first().dateIso)
        assertEquals("2027-01-07", days.last().dateIso)
    }
}
