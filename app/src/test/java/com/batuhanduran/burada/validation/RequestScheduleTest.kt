package com.batuhanduran.burada.validation

import org.junit.Assert.*
import org.junit.Test

class RequestScheduleTest {
    @Test fun calendarValidationDoesNotDependOnSkippedLocalDays() {
        val originalZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Apia"))
            assertTrue(RequestSchedules.isValidDate("2011-12-30"))
            assertFalse(RequestSchedules.isValidDate("2011-02-29"))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"))
            assertTrue(RequestSchedules.isValidDate("2018-11-04"))
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }
    @Test fun rejectsInvalidCalendarDatesIncludingCenturyLeapYears() {
        listOf("", "Hemen / Bugün", "2026-02-29", "2026-02-31", "2026-04-31", "2026-13-01", "2026-00-10", "2026-10-00", "2026-1-01", "1900-02-29").forEach {
            assertFalse(it, RequestSchedules.isValidDate(it))
        }
        listOf("2028-02-29", "2000-02-29", "2026-12-31").forEach {
            assertTrue(it, RequestSchedules.isValidDate(it))
        }
    }
    @Test fun rejectsInvalidTimesAndAcceptsDayBoundaries() {
        listOf("", "En geç 1 saat içinde", "24:00", "25:70", "12:60", "9:00", "12:00:00").forEach {
            assertFalse(it, RequestSchedules.isValidTime(it))
        }
        listOf("00:00", "23:59").forEach { assertTrue(RequestSchedules.isValidTime(it)) }
    }
    @Test fun titleIsRequiredAndLimited() {
        listOf("", "   ", "\t\n", "x", "x".repeat(121)).forEach {
            try {
                RequestSchedules.requireValid(it, "2026-10-08", "15:30")
                fail("Accepted invalid title")
            } catch (_: IllegalArgumentException) { }
        }
        RequestSchedules.requireValid("Acil su kaçağı", "2026-10-08", "15:30")
    }
    @Test fun emergencyUsesOneInstantForBothFields() {
        val instant = java.util.Date(1_791_460_800_000L)
        val schedule = RequestSchedules.now(instant)
        RequestSchedules.requireValid("Acil su kaçağı", schedule.date, schedule.time)
        assertEquals(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(instant), schedule.date)
        assertEquals(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(instant), schedule.time)
    }
}
