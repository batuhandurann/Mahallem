package com.batuhanduran.burada.data.remote

/** Independently testable account boundary for background delivery. */
object PushEnvelopePolicy {
    fun shouldDisplay(data: Map<String, String>, activeUid: String?): Boolean =
        !activeUid.isNullOrBlank() && data["recipientUid"] == activeUid &&
            data["type"] == "chat_message" && !data["messageId"].isNullOrBlank() &&
            !data["conversationId"].isNullOrBlank()
}
