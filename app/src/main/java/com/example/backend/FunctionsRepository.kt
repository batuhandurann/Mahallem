package com.example.backend

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FunctionsRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")
) {
    suspend fun createPaymentIntent(
        requestId: Long,
        quoteId: Long,
        amountMinor: Long,
        currency: String = "TRY",
        idempotencyKey: String
    ): Map<*, *> {
        require(idempotencyKey.matches(Regex("^[A-Za-z0-9._:-]{16,128}$"))) {
            "Geçersiz ödeme idempotency anahtarı."
        }

        val result = functions.getHttpsCallable("createPaymentIntent")
            .call(
                mapOf(
                    "requestId" to requestId.toString(),
                    "quoteId" to quoteId.toString(),
                    "amountMinor" to amountMinor,
                    "idempotencyKey" to idempotencyKey,
                    "currency" to currency
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz ödeme yanıtı.")
    }

    suspend fun createQuote(
        quoteId: Long,
        requestId: Long,
        providerId: String,
        price: String,
        amountMinor: Long,
        durationOrArrival: String,
        notes: String
    ): Map<*, *> {
        val result = functions.getHttpsCallable("createQuote")
            .call(
                mapOf(
                    "quoteId" to quoteId.toString(),
                    "requestId" to requestId.toString(),
                    "providerId" to providerId,
                    "price" to price,
                    "amountMinor" to amountMinor,
                    "durationOrArrival" to durationOrArrival,
                    "notes" to notes
                )
            )
            .await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz teklif yanıtı.")
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        messageType: String = "TEXT",
        attachmentUrl: String? = null
    ): String {
        val result = functions.getHttpsCallable("sendMessage")
            .call(
                mapOf(
                    "conversationId" to conversationId,
                    "text" to text,
                    "messageType" to messageType,
                    "attachmentUrl" to attachmentUrl
                )
            )
            .await()
        val data = result.data as? Map<*, *> ?: error("Sunucudan geçersiz mesaj yanıtı.")
        return data["messageId"] as? String ?: error("Sunucudan mesaj kimliği alınamadı.")
    }

    suspend fun startConversation(
        targetId: String,
        relatedItemId: String = "",
        relatedItemTitle: String = ""
    ): String {
        val result = functions.getHttpsCallable("startConversation")
            .call(
                mapOf(
                    "targetId" to targetId,
                    "relatedItemId" to relatedItemId,
                    "relatedItemTitle" to relatedItemTitle
                )
            )
            .await()
        val data = result.data as? Map<*, *> ?: error("Sunucudan geçersiz sohbet yanıtı.")
        return data["conversationId"] as? String ?: error("Sunucudan sohbet kimliği alınamadı.")
    }

    suspend fun registerDeviceToken(token: String, platform: String = "android"): Map<*, *> =
        call("registerDeviceToken", mapOf("token" to token, "platform" to platform))

    suspend fun unregisterDeviceToken(token: String): Map<*, *> =
        call("unregisterDeviceToken", mapOf("token" to token))

    suspend fun requestRefund(paymentId: String): Map<*, *> {
        val result = functions.getHttpsCallable("requestRefund")
            .call(mapOf("paymentId" to paymentId))
            .await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz iade yanıtı.")
    }

    suspend fun acceptQuote(quoteId: Long): Map<*, *> =
        call("acceptQuote", mapOf("quoteId" to quoteId.toString()))

    suspend fun rejectQuote(quoteId: Long): Map<*, *> =
        call("rejectQuote", mapOf("quoteId" to quoteId.toString()))

    suspend fun releaseEscrowPayment(paymentId: String): Map<*, *> =
        call("releaseEscrowPayment", mapOf("paymentId" to paymentId))

    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<*, *>) ?: error("Sunucudan geçersiz yanıt.")
    }
}
