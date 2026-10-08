package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ChatMessageEntity
import com.batuhanduran.burada.data.local.ConversationEntity

/** A bounded Firestore descending query selects the newest messages; the chat UI shows them oldest-first. */
internal object ChatOrdering {
    const val RECENT_MESSAGE_LIMIT = 200L

    fun oldestFirst(newestFirst: List<ChatMessageEntity>): List<ChatMessageEntity> =
        newestFirst.reversed()

    /** Stable ordering avoids card flicker when server timestamps are equal or initially missing. */
    fun mostRecentConversationsFirst(items: List<ConversationEntity>): List<ConversationEntity> =
        items.sortedWith(compareByDescending<ConversationEntity> { it.lastTimestamp }.thenBy { it.id })
}
