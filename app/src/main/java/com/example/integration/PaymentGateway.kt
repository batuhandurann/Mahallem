package com.example.integration

import java.net.URI

data class PaymentIntent(
    val id: String,
    val checkoutUrl: String
)

interface PaymentGateway {
    suspend fun createPayment(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String,
        customerEmail: String? = null,
        idempotencyKey: String? = null
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
        currency: String,
        customerEmail: String?,
        idempotencyKey: String?
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
internal fun isAllowedPaytrCheckoutUrl(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val scheme = uri.scheme ?: return false
    val host = uri.host ?: return false
    val path = uri.rawPath ?: return false
    return scheme.equals("https", ignoreCase = true)
        && host.equals("www.paytr.com", ignoreCase = true)
        && uri.rawUserInfo == null
        && (uri.port == -1 || uri.port == 443)
        && path.startsWith("/odeme/guvenli/")
}

class ProductionPaymentGateway(
    private val functions: com.example.backend.FunctionsRepository = com.example.backend.FunctionsRepository()
) : PaymentGateway {
    override suspend fun createPayment(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String,
        customerEmail: String?,
        idempotencyKey: String?
    ): PaymentIntent {
        require(amountMinor > 0) { "Ödeme tutarı geçersiz." }
        val key = idempotencyKey ?: java.util.UUID.randomUUID().toString()
        val result = functions.createPaymentIntent(
            requestId = requestId,
            quoteId = quoteId,
            amountMinor = amountMinor,
            currency = currency,
            idempotencyKey = key,
            customerEmail = customerEmail
        )
        val id = result["id"]?.toString() ?: error("Ödeme kimliği alınamadı.")
        val checkoutUrl = result["checkoutUrl"]?.toString()
            ?: error("Ödeme sağlayıcısı checkout adresi döndürmedi.")
        return PaymentIntent(id, checkoutUrl)
    }

    override suspend fun refund(paymentId: String, amountMinor: Long?) {
        functions.requestRefund(paymentId)
    }
}