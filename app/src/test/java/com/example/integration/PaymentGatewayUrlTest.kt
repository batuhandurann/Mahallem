package com.example.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentGatewayUrlTest {
    @Test fun acceptsPaytrSecureCheckoutUrl() {
        assertTrue(isAllowedPaytrCheckoutUrl("https://www.paytr.com/odeme/guvenli/token123?lang=tr"))
        assertTrue(isAllowedPaytrCheckoutUrl("https://www.paytr.com:443/odeme/guvenli/token123"))
    }

    @Test fun rejectsNonHttpsAndWrongHosts() {
        assertFalse(isAllowedPaytrCheckoutUrl("http://www.paytr.com/odeme/guvenli/token123"))
        assertFalse(isAllowedPaytrCheckoutUrl("https://paytr.com/odeme/guvenli/token123"))
        assertFalse(isAllowedPaytrCheckoutUrl("https://www.paytr.com.evil.example/odeme/guvenli/token123"))
    }

    @Test fun rejectsUserInfoAndUnexpectedPorts() {
        assertFalse(isAllowedPaytrCheckoutUrl("https://www.paytr.com@evil.example/odeme/guvenli/token123"))
        assertFalse(isAllowedPaytrCheckoutUrl("https://www.paytr.com:8443/odeme/guvenli/token123"))
    }

    @Test fun rejectsMissingOrUnexpectedPath() {
        assertFalse(isAllowedPaytrCheckoutUrl("https://www.paytr.com"))
        assertFalse(isAllowedPaytrCheckoutUrl("https://www.paytr.com/odeme/guvensiz/token123"))
        assertFalse(isAllowedPaytrCheckoutUrl("not a url"))
    }
}
