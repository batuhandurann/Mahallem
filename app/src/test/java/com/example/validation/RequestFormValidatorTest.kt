package com.example.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Test

class RequestFormValidatorTest {
    private fun validInput() = RequestFormInput(
        title = "Ev boya hizmeti",
        district = "Karşıyaka, İzmir",
        date = "2026-10-18",
        time = "14:00",
        address = "Bostanlı Mahallesi 123. Sokak No:4",
        customerName = "Batuhan Duran",
        customerPhone = "05321234567",
        areaSquareMeters = 95,
        isPhysicalService = true
    )

    @Test fun validFormPasses() {
        val result = RequestFormValidator.validate(validInput())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun invalidPhoneFails() {
        val result = RequestFormValidator.validate(validInput().copy(customerPhone = "123"))
        assertEquals(false, result.isValid)
        assertTrue(result.errors.any { it.contains("telefon", ignoreCase = true) })
    }

    @Test fun missingAddressFails() {
        val result = RequestFormValidator.validate(validInput().copy(address = "x"))
        assertEquals(false, result.isValid)
    }

    @Test fun physicalServiceRequiresPositiveArea() {
        val result = RequestFormValidator.validate(validInput().copy(areaSquareMeters = 0))
        assertEquals(false, result.isValid)
    }

    @Test fun nonPhysicalServiceCanOmitArea() {
        val result = RequestFormValidator.validate(validInput().copy(areaSquareMeters = 0, isPhysicalService = false))
        assertTrue(result.isValid)
    }

    @Test fun titleLengthIsLimited() {
        val result = RequestFormValidator.validate(validInput().copy(title = "x".repeat(121)))
        assertEquals(false, result.isValid)
    }
    @Test fun invalidCalendarDatesAreRejected() {
        listOf("2026-02-30", "2025-02-29", "2026-13-01", "2026-00-01", "Hemen / Bugün").forEach {
            assertEquals("Expected invalid date: $it", false, RequestFormValidator.validate(validInput().copy(date = it)).isValid)
        }
    }

    @Test fun leapDayIsAccepted() {
        assertTrue(RequestFormValidator.validate(validInput().copy(date = "2028-02-29")).isValid)
    }

    @Test fun invalidTimesAreRejected() {
        listOf("24:00", "12:60", "1:05", "En geç 1 saat içinde").forEach {
            assertEquals("Expected invalid time: $it", false, RequestFormValidator.validate(validInput().copy(time = it)).isValid)
        }
    }

    @Test fun blankTitlesAreRejected() {
        assertEquals(false, RequestFormValidator.validate(validInput().copy(title = "  ")).isValid)
    }

    @Test fun validBoundaryTimesAreAccepted() {
        for (time in listOf("00:00", "23:59")) {
            assertTrue("Expected valid time: $time", RequestFormValidator.validate(validInput().copy(time = time)).isValid)
        }
    }

    @Test fun dateAndTimeDefaultsShareTheSameInstant() {
        val utc = TimeZone.getTimeZone("UTC")
        val instant = GregorianCalendar(utc).apply {
            clear()
            set(2026, 9, 8, 23, 59, 0)
        }.timeInMillis
        val (date, time) = RequestDateTimeDefaults.at(instant, utc)
        assertEquals("2026-10-08", date)
        assertEquals("23:59", time)
        assertTrue(RequestFormValidator.validate(validInput().copy(date = date, time = time)).isValid)
    }

    @Test fun malformedYearAndMonthAreRejected() {
        for (date in listOf("0099-12-31", "2026-04-31", "2026-1-01", "2026-02-29")) {
            assertEquals("Expected invalid date: $date", false, RequestFormValidator.validate(validInput().copy(date = date)).isValid)
        }
    }
}
