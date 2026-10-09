package com.batuhanduran.burada.ui.components

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingCalendarDaysTest {
    private fun instant(
        year: Int, month: Int, day: Int, hour: Int = 12, zone: String = "Europe/Istanbul"
    ): Date = GregorianCalendar(TimeZone.getTimeZone(zone)).apply {
        clear()
        set(year, month - 1, day, hour, 0, 0)
    }.time

    @Test fun todayStartsTheTenDayWindow() {
        val days = generateUpcomingCalendarDays(
            instant(2026, 10, 9), TimeZone.getTimeZone("Europe/Istanbul")
        )
        assertEquals(10, days.size)
        assertEquals("2026-10-09", days.first().dateIso)
        assertEquals("2026-10-18", days.last().dateIso)
        assertEquals("Cum", days.first().dayName)
        assertEquals("9", days.first().dayNumber)
        assertFalse(days.first().isWeekend)
        assertEquals("Cmt", days[1].dayName)
        assertTrue(days[1].isWeekend)
        assertEquals("Paz", days[2].dayName)
        assertTrue(days[2].isWeekend)
    }

    @Test fun tomorrowMovesTheEntireWindowWithoutOldDates() {
        val days = generateUpcomingCalendarDays(
            instant(2026, 10, 10), TimeZone.getTimeZone("Europe/Istanbul")
        )
        assertEquals("2026-10-10", days.first().dateIso)
        assertEquals("2026-10-19", days.last().dateIso)
        assertFalse(days.any { it.dateIso == "2026-10-09" })
    }

    @Test fun yearEndRollsOverToJanuary() {
        val days = generateUpcomingCalendarDays(
            instant(2026, 12, 29), TimeZone.getTimeZone("Europe/Istanbul")
        )
        assertEquals("2026-12-29", days.first().dateIso)
        assertEquals("2027-01-01", days[3].dateIso)
        assertEquals("2027-01-07", days.last().dateIso)
    }

    @Test fun leapYearAndMonthEndAreCalendarCorrect() {
        val days = generateUpcomingCalendarDays(
            instant(2028, 2, 27), TimeZone.getTimeZone("Europe/Istanbul")
        )
        assertEquals("2028-02-29", days[2].dateIso)
        assertEquals("2028-03-01", days[3].dateIso)
    }

    @Test fun daylightSavingTransitionStillHasTenConsecutiveDates() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val days = generateUpcomingCalendarDays(instant(2026, 3, 28, zone = "Europe/Berlin"), zone)
        assertEquals("2026-03-28", days[0].dateIso)
        assertEquals("2026-03-29", days[1].dateIso)
        assertEquals("2026-03-30", days[2].dateIso)
        assertEquals("2026-04-06", days[9].dateIso)
    }

    @Test fun dateIsLocalToUserNotUtc() {
        val utcMoment = instant(2026, 10, 9, 0, zone = "UTC")
        val laDays = generateUpcomingCalendarDays(utcMoment, TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("2026-10-08", laDays.first().dateIso)
        val istanbulDays = generateUpcomingCalendarDays(utcMoment, TimeZone.getTimeZone("Europe/Istanbul"))
        assertEquals("2026-10-09", istanbulDays.first().dateIso)
    }
}
