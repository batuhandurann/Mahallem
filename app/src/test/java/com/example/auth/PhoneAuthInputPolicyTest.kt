package com.example.auth

import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneAuthInputPolicyTest {
    @Test fun emptySmsCodeIsRejected() {
        assertFalse(PhoneAuthInputPolicy.validSmsCode(""))
    }
}
