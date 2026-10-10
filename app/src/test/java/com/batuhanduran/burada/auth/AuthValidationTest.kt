package com.batuhanduran.burada.auth

import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    @Test fun emailBoundaries() {
        assertNull(AuthValidation.emailError(" user@example.com "))
        listOf("", "name", "a@b", "a b@example.com", "a".repeat(255) + "@x.com").forEach {
            assertNotNull(AuthValidation.emailError(it))
        }
    }
    @Test fun displayNameBoundaries() {
        assertNotNull(AuthValidation.nameError("a"))
        assertNotNull(AuthValidation.nameError("a".repeat(81)))
        assertNotNull(AuthValidation.nameError("Alice\nBob"))
        assertNotNull(AuthValidation.nameError("Alice\u2028Bob"))
        assertNotNull(AuthValidation.nameError("Alice\u2029Bob"))
        assertNull(AuthValidation.nameError("Batuhan Duran"))
    }
    @Test fun loginDoesNotRejectLegacyPasswords() {
        assertNotNull(AuthValidation.passwordError("", false))
        assertNull(AuthValidation.passwordError("short", false))
        assertNotNull(AuthValidation.passwordError("short", true))
        assertNotNull(AuthValidation.passwordError("sixsix", true))
        assertNull(AuthValidation.passwordError("eight888", true))
    }
}
