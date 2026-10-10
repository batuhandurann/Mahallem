package com.batuhanduran.burada.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationEmailCooldownTest {
    @Test fun roundsUpAndExpiresAtThirtySeconds() {
        var now = 0L
        val cooldown = VerificationEmailCooldown { now }
        assertEquals(0, cooldown.remainingSeconds("alice"))
        cooldown.recordAttempt("alice")
        assertEquals(30, cooldown.remainingSeconds("alice"))
        now = 29_001
        assertEquals(1, cooldown.remainingSeconds("alice"))
        now = 30_000
        assertEquals(0, cooldown.remainingSeconds("alice"))
    }

    @Test fun attemptsAreIsolatedByAccountAndFailedAttemptsCanResetTheWindow() {
        var now = 0L
        val cooldown = VerificationEmailCooldown { now }
        cooldown.recordAttempt("alice")
        now = 10_000
        assertEquals(0, cooldown.remainingSeconds("bob"))
        cooldown.recordAttempt("bob")
        assertEquals(20, cooldown.remainingSeconds("alice"))
        assertEquals(30, cooldown.remainingSeconds("bob"))
        now = 20_000
        cooldown.recordAttempt("alice")
        assertEquals(30, cooldown.remainingSeconds("alice"))
        assertEquals(20, cooldown.remainingSeconds("bob"))
    }
}
