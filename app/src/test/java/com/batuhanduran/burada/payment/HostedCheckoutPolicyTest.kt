package com.batuhanduran.burada.payment

import org.junit.Assert.*
import org.junit.Test

class HostedCheckoutPolicyTest {
    @Test fun onlyExactEnvironmentHttpsProviderHostCanOpen() {
        assertTrue(HostedCheckoutPolicy.allowedUrl("https://sandbox-api.iyzipay.com/payment/checkoutform/initialize/auth/ecom?token=abc", CheckoutEnvironment.SANDBOX))
        assertTrue(HostedCheckoutPolicy.allowedUrl("https://api.iyzipay.com/checkoutform/pay?token=abc", CheckoutEnvironment.PRODUCTION))
        for (url in listOf("intent://api.iyzipay.com/", "http://api.iyzipay.com/", "https://api.iyzipay.com.evil.test/",
            "https://evil.test@api.iyzipay.com/", "https://api.iyzipay.com:8443/", "https://api.iyzipay.com/#success",
            "https://sandbox-api.iyzipay.com/", "https://api.iyzipay.com\\@evil.test/")) {
            assertFalse(url, HostedCheckoutPolicy.allowedUrl(url, CheckoutEnvironment.PRODUCTION))
        }
        assertFalse(HostedCheckoutPolicy.allowedUrl("https://api.iyzipay.com/", CheckoutEnvironment.DISABLED))
    }
    @Test fun unfamiliarStatusNeverBecomesPaymentSuccessAndUrlNeedsReady() {
        val unknown = HostedCheckoutPolicy.decode(mapOf("status" to "SUCCESS", "environment" to "sandbox", "paymentPageUrl" to "https://sandbox-api.iyzipay.com/"))
        assertEquals(CheckoutStatus.UNKNOWN, unknown.status)
        assertNull(unknown.paymentPageUrl)
        assertNull(HostedCheckoutPolicy.decode(mapOf("status" to "READY", "environment" to "sandbox", "paymentPageUrl" to "https://evil.test/")).paymentPageUrl)
        assertEquals(CheckoutEnvironment.DISABLED, HostedCheckoutPolicy.environment("SANDBOX"))
    }
}
