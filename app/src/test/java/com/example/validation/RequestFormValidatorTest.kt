package com.example.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.GregorianCalendar
import java.util.TimeZone

class RequestFormValidatorTest {
    private fun today() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse("2026-10-08 10:00")!!

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
        val result = RequestFormValidator.validate(validInput(), today())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun invalidPhoneFails() {
        val result = RequestFormValidator.validate(validInput().copy(customerPhone = "123"), today())
        assertEquals(false, result.isValid)
        assertTrue(result.errors.any { it.contains("telefon", ignoreCase = true) })
    }

    @Test fun missingAddressFails() {
        val result = RequestFormValidator.validate(validInput().copy(address = "x"), today())
        assertEquals(false, result.isValid)
    }

    @Test fun physicalServiceRequiresPositiveArea() {
        val result = RequestFormValidator.validate(validInput().copy(areaSquareMeters = 0), today())
        assertEquals(false, result.isValid)
    }

    @Test fun nonPhysicalServiceCanOmitArea() {
        val result = RequestFormValidator.validate(validInput().copy(areaSquareMeters = 0, isPhysicalService = false), today())
        assertTrue(result.isValid)
    }

    @Test fun titleLengthIsLimited() {
        val result = RequestFormValidator.validate(validInput().copy(title = "x".repeat(121)), today())
        assertEquals(false, result.isValid)
    }

    @Test fun rejectsBlankTitle() {
        for (title in listOf("", "   ", "\t\n")) {
            assertTrue(RequestFormValidator.validate(validInput().copy(title = title), today()).errors.any { it.contains("Başlık") })
        }
    }

    @Test fun plannedFormCannotUseEmergencyLabels() {
        assertEquals(false, RequestFormValidator.validate(
            validInput().copy(date = "Hemen / Bugün", time = "En geç 1 saat içinde"), today()
        ).isValid)
    }

    @Test fun rejectsPastDate() {
        val result = RequestFormValidator.validate(validInput().copy(date = "2026-10-07"), today())
        assertTrue(result.errors.any { it.contains("Geçmiş bir tarih") })
    }

    @Test fun rejectsInvalidCalendarDay() {
        val result = RequestFormValidator.validate(validInput().copy(date = "2026-02-30"), today())
        assertTrue(result.errors.any { it.contains("geçerli bir gün") })
    }

    @Test fun rejectsInvalidTime() {
        val result = RequestFormValidator.validate(validInput().copy(time = "25:80"), today())
        assertTrue(result.errors.any { it.contains("Saat") })
    }

    @Test fun rejectsPastTimeToday() {
        val result = RequestFormValidator.validate(
            validInput().copy(date = "2026-10-08", time = "09:30"), today()
        )
        assertTrue(result.errors.any { it.contains("Geçmiş bir saat") })
    }

    @Test fun acceptsEmergencyShortcut() {
        val result = RequestFormValidator.validate(
            validInput().copy(date = "Hemen / Bugün", time = "En geç 1 saat içinde", isEmergency = true), today()
        )
        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test fun rejectsHalfEmergencyShortcut() {
        val result = RequestFormValidator.validate(
            validInput().copy(date = "Hemen / Bugün", time = "14:00"), today()
        )
        assertTrue(result.errors.any { it.contains("birlikte") })
    }

    @Test fun validBoundaryTimesFromQaAreAccepted() {
        for (time in listOf("00:00", "23:59")) {
            assertTrue("Expected valid time: $time", RequestFormValidator.validate(validInput().copy(time = time), today()).isValid)
        }
    }

    @Test fun atomicDateTimeDefaultsArePreserved() {
        val utc = TimeZone.getTimeZone("UTC")
        val instant = GregorianCalendar(utc).apply {
            clear()
            set(2026, 9, 8, 23, 59, 0)
        }.timeInMillis
        val (date, time) = RequestDateTimeDefaults.at(instant, utc)
        assertEquals("2026-10-08", date)
        assertEquals("23:59", time)
        assertTrue(RequestFormValidator.validate(validInput().copy(date = date, time = time), today()).isValid)
    }

    @Test fun invalidYearAndMonthFromQaRemainRejected() {
        for (date in listOf("0099-12-31", "2026-04-31", "2026-1-01", "2026-02-29")) {
            assertEquals("Expected invalid date: $date", false, RequestFormValidator.validate(validInput().copy(date = date), today()).isValid)
        }
    }

}
