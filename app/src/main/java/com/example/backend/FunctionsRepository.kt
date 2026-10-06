package com.example.backend

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FunctionsRepository(private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")) {
    suspend fun createPaymentIntent(requestId: Long, quoteId: Long, amountMinor: Long, currency: String = "TRY"): Map<*, *> {
        val result = functions.getHttpsCallable("createPaymentIntent").call(mapOf("requestId" to requestId.toString(), "quoteId" to quoteId.toString(), "amountMinor" to amountMinor,
                    "idempotencyKey" to idempotencyKey, "currency" to currency)).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz ödeme yanıtı.")
    }

    suspend fun requestRefund(paymentId: String): Map<*, *> {
        val result = functions.getHttpsCallable("requestRefund").call(mapOf("paymentId" to paymentId)).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz iade yanıtı.")
    }
    suspend fun acceptQuote(quoteId: Long): Map<*, *> = call("acceptQuote", mapOf("quoteId" to quoteId.toString()))
    suspend fun rejectQuote(quoteId: Long): Map<*, *> = call("rejectQuote", mapOf("quoteId" to quoteId.toString()))
    suspend fun releaseEscrowPayment(paymentId: String): Map<*, *> = call("releaseEscrowPayment", mapOf("paymentId" to paymentId))

    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz yanıt.")
    }
}
