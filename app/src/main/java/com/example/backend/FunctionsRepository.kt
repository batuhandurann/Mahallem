package com.example.backend

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FunctionsRepository(private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")) {
    suspend fun createPaymentIntent(requestId: Long, quoteId: Long, amountMinor: Long, currency: String = "TRY"): Map<*, *> {
        val result = functions.getHttpsCallable("createPaymentIntent").call(mapOf("requestId" to requestId.toString(), "quoteId" to quoteId.toString(), "amountMinor" to amountMinor, "currency" to currency)).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz ödeme yanıtı.")
    }

    suspend fun requestRefund(paymentId: String): Map<*, *> {
        val result = functions.getHttpsCallable("requestRefund").call(mapOf("paymentId" to paymentId)).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz iade yanıtı.")
    }
}