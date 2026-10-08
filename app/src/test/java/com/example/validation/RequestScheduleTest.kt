package com.example.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RequestScheduleTest {
    @Test fun emergencyBecomesLocalIsoDateAndClockTime() {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse("2026-10-08 23:59")!!
        assertEquals(RequestSchedule("2026-10-08", "23:59"), RequestSchedule.resolve(
            RequestSchedule.EMERGENCY_DATE, RequestSchedule.EMERGENCY_TIME, true, now
        ))
    }

    @Test fun localMidnightRollsTheCalendarDay() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
            val utc = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            assertEquals(RequestSchedule("2027-01-01", "00:01"), RequestSchedule.resolve(
                RequestSchedule.EMERGENCY_DATE, RequestSchedule.EMERGENCY_TIME, true, utc.parse("2026-12-31 21:01")!!
            ))
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun plannedScheduleKeepsCanonicalValues() {
        assertEquals(RequestSchedule("2028-02-29", "09:05"), RequestSchedule.resolve("2028-02-29", "09:05", false))
    }

    @Test fun malformedSchedulesNeverReachTheBackend() {
        for ((date, time) in listOf("2026-02-30" to "09:00", "2026-2-03" to "09:00", "2026-10-08" to "24:00", "" to "", "2026-10-08" to "9:00")) {
            assertThrows(IllegalArgumentException::class.java) { RequestSchedule.resolve(date, time, false) }
        }
    }

    @Test fun labelsRequireEmergencyModeAndCompletePair() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestSchedule.resolve(RequestSchedule.EMERGENCY_DATE, RequestSchedule.EMERGENCY_TIME, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RequestSchedule.resolve(RequestSchedule.EMERGENCY_DATE, "09:00", true)
        }
    }
}
