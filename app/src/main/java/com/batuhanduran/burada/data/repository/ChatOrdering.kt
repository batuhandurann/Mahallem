package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ConversationEntity

/** Stable, newest-activity-first ordering for the signed-in user's conversation cards. */
internal object ChatOrdering {
    fun mostRecentConversationsFirst(items: List<ConversationEntity>): List<ConversationEntity> =
        items.sortedWith(compareByDescending<ConversationEntity> { it.lastTimestamp }.thenBy { it.id })
}
