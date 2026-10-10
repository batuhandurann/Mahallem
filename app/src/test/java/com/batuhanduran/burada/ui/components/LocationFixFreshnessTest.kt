package com.batuhanduran.burada.ui.components

import org.junit.Assert.*
import org.junit.Test

class LocationFixFreshnessTest {
    @Test fun currentFixIsAccepted() {
        assertTrue(isFreshLocationFix(300_000_000_000L, 300_000_000_000L))
    }

    @Test fun boundaryFixIsAccepted() {
        assertTrue(isFreshLocationFix(180_000_000_000L, 300_000_000_000L))
    }

    @Test fun staleFixIsRejected() {
        assertFalse(isFreshLocationFix(179_999_999_999L, 300_000_000_000L))
    }

    @Test fun futureFixIsRejected() {
        assertFalse(isFreshLocationFix(300_000_000_001L, 300_000_000_000L))
    }
    @Test fun negativeFixTimestampIsRejected() {
        assertFalse(isFreshLocationFix(-1L, 300_000_000_000L))
    }

    @Test fun extremeAgeDoesNotOverflowIntoFreshRange() {
        assertFalse(isFreshLocationFix(0L, Long.MAX_VALUE))
    }

}
