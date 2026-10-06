package com.example.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
