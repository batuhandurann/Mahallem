package com.example.auth

import org.junit.Assert.*
import org.junit.Test

class PhoneAuthInputPolicyTest {
    private val mobile = "555" + "000" + "0000"

    @Test fun acceptsMobileFormats() {
        val expected = "+90" + mobile
        assertEquals(expected, PhoneAuthInputPolicy.normalizedPhoneOrNull(mobile))
        assertEquals(expected, PhoneAuthInputPolicy.normalizedPhoneOrNull("0" + mobile))
        assertEquals(expected, PhoneAuthInputPolicy.normalizedPhoneOrNull("0090" + mobile))
    }

    @Test fun rejectsInvalidNumbers() {
        assertNull(PhoneAuthInputPolicy.normalizedPhoneOrNull(""))
        assertNull(PhoneAuthInputPolicy.normalizedPhoneOrNull(mobile.dropLast(1)))
        assertNull(PhoneAuthInputPolicy.normalizedPhoneOrNull("+90" + mobile + "1"))
    }

    @Test fun validatesSmsCodes() {
        assertTrue(PhoneAuthInputPolicy.validSmsCode("123" + "456"))
        assertFalse(PhoneAuthInputPolicy.validSmsCode(""))
        assertFalse(PhoneAuthInputPolicy.validSmsCode("12345"))
        assertFalse(PhoneAuthInputPolicy.validSmsCode("1234567"))
        assertFalse(PhoneAuthInputPolicy.validSmsCode("123 456"))
    }
}
