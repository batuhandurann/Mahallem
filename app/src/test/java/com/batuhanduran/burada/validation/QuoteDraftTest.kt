package com.batuhanduran.burada.validation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteDraftTest {
    @Test fun acceptedPricesRequireProviderSuppliedArrival() {
        listOf("1.250,50 TL", "1250", "2.800 ₺", "0,50", "999999999,99")
            .forEach { assertNull("Rejected a valid price: $it", quoteDraftError(it, "Yarın 14.00", "")) }
    }

    @Test fun missingOrInvalidPriceCannotBeSubmitted() {
        listOf("", " ", "0", "0,00 TL", "-30", "1abc2", "1,2,3", "1.2.3",
            "99,999", "1.000.000.000", "12 TL ekstra", "00,50", "99999999999999999999")
            .forEach { assertNotNull("Accepted invalid price: $it", quoteDraftError(it, "Yarın 14.00", "")) }
    }

    @Test fun doNotInventArrivalOrGuarantee() {
        assertNotNull(quoteDraftError("2.800 TL", "", ""))
        assertNotNull(quoteDraftError("2.800 TL", "  ", ""))
        assertNull(quoteDraftError("2.800 TL", "1 saat içinde", ""))
    }

    @Test fun boundArrivalAndOptionalNotes() {
        assertNotNull(quoteDraftError("150 TL", "a".repeat(121), ""))
        assertNotNull(quoteDraftError("150 TL", "Yarın", "x".repeat(1001)))
        assertNull(quoteDraftError("150 TL", "Yarın", "x".repeat(1000)))
    }
}
