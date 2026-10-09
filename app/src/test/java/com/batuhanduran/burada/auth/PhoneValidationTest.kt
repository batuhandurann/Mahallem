package com.batuhanduran.burada.auth

import org.junit.Assert.*
import org.junit.Test

class PhoneValidationTest {
    @Test fun normalizeOnlyRealMobileFormats() {
        for (input in listOf("0555 123 45 67", "5551234567", "905551234567", "+90 (555) 123-45-67"))
            assertEquals("+905551234567", PhoneValidation.normalize(input))
        for (input in listOf("", "05xx xxx xx xx", "call 05551234567", "+902121234567", "+15551234567", "055512345678"))
            assertNull(PhoneValidation.normalize(input))
    }
    @Test fun codesAreExactlySixAsciiDigits() {
        assertTrue(PhoneValidation.validCode("123456"))
        for (input in listOf("12345", "1234567", "12345a", "１２３４５６"))
            assertFalse(PhoneValidation.validCode(input))
    }
}
