package com.example.integration

data class PaymentIntent(
    val id: String,
    val checkoutUrl: String
)

interface PaymentGateway {
    suspend fun createPayment(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String
    ): PaymentIntent

    suspend fun refund(paymentId: String, amountMinor: Long? = null)
}

/**
 * Safe local adapter. It never contacts a bank or moves real money.
 * Replace only through a server-side sandbox implementation.
 */
class TestPaymentGateway : PaymentGateway {
    override suspend fun createPayment(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String
    ): PaymentIntent = PaymentIntent(
        id = "TEST-PAY-$requestId-$quoteId",
        checkoutUrl = "https://example.invalid/test-payment"
    )

    override suspend fun refund(paymentId: String, amountMinor: Long?) = Unit
}

/**
 * Production payments must be initiated and verified by the backend.
 * Do not put merchant secrets in the Android app.
 */
class ProductionPaymentGateway : PaymentGateway {
    override suspend fun createPayment(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String
    ): PaymentIntent = error("Production payment provider is not configured")

    override suspend fun refund(paymentId: String, amountMinor: Long?) =
        error("Production payment provider is not configured")
}