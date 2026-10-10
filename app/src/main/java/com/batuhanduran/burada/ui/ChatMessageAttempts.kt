package com.batuhanduran.burada.ui

import java.util.UUID

data class ChatMessageAttempt(
    val conversationId: String,
    val text: String,
    val isOffer: Boolean,
    val offerPrice: String,
    val submissionId: String
)

/** UID-scoped memory retains uncertain submissions until a server acknowledgement. */
class ChatMessageAttempts(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private data class Draft(val conversationId: String, val text: String, val isOffer: Boolean, val offerPrice: String)
    private val pending = mutableMapOf<Draft, String>()

    fun begin(conversationId: String, text: String, isOffer: Boolean, offerPrice: String): ChatMessageAttempt {
        val draft = Draft(conversationId, text.trim(), isOffer, offerPrice)
        val id = pending.getOrPut(draft, newId)
        return ChatMessageAttempt(draft.conversationId, draft.text, draft.isOffer, draft.offerPrice, id)
    }

    fun acknowledge(attempt: ChatMessageAttempt) {
        val draft = Draft(attempt.conversationId, attempt.text, attempt.isOffer, attempt.offerPrice)
        // A late acknowledgement must not remove a newer intentional identical message.
        if (pending[draft] == attempt.submissionId) pending.remove(draft)
    }

    fun clear() = pending.clear()
}
