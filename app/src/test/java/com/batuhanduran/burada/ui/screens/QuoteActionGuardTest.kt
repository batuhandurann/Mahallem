package com.batuhanduran.burada.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteActionGuardTest {
    @Test fun pendingOfferOnOpenRequestCanBeAccepted() {
        assertTrue(mayActOnQuote("PENDING", false))
    }
    @Test fun finalOrUnknownOffersNeverExposeAcceptPayment() {
        for (status in listOf("REJECTED", "WITHDRAWN", "ACCEPTED", "CANCELLED", "")) {
            assertFalse("Unexpected action for $status", mayActOnQuote(status, false))
        }
    }
    @Test fun acceptedRequestCannotReceiveAnotherOffer() {
        assertFalse(mayActOnQuote("PENDING", true))
    }
}
