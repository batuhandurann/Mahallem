package com.batuhanduran.burada

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression gate for issue #26: the legacy QA app is a different Android
 * application and must never replace the canonical main implementation.
 */
class CanonicalAndroidIdentityTest {
    @Test fun applicationIdRemainsCanonical() {
        assertEquals("com.batuhanduran.burada", BuildConfig.APPLICATION_ID)
    }

    @Test fun launcherActivityRemainsCanonical() {
        assertEquals("com.batuhanduran.burada.MainActivity", MainActivity::class.java.name)
    }

    @Test fun applicationClassRemainsCanonical() {
        assertEquals("com.batuhanduran.burada.BuradaApplication", BuradaApplication::class.java.name)
    }
}
