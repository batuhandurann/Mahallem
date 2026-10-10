package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOrderingTest {
    private fun conversation(id: String, lastActivity: Long) = ConversationEntity(
        id = id,
        participantId = "other",
        participantName = "Other",
        participantTitle = "",
        lastMessage = "",
        lastTimestamp = lastActivity
    )

    @Test
    fun conversationsAreSortedByLatestServerActivity() {
        val unsorted = listOf(
            conversation("older", 10),
            conversation("newer", 50),
            conversation("middle", 20)
        )
        assertEquals(
            listOf("newer", "middle", "older"),
            ChatOrdering.mostRecentConversationsFirst(unsorted).map { it.id }
        )
    }

    @Test
    fun identicalTimestampsAreDeterministicAndInputsAreUnchanged() {
        val original = listOf(conversation("z", 0), conversation("b", 20), conversation("a", 20))
        assertEquals(
            listOf("a", "b", "z"),
            ChatOrdering.mostRecentConversationsFirst(original).map { it.id }
        )
        assertEquals(listOf("z", "b", "a"), original.map { it.id })
    }

    @Test
    fun emptyConversationListIsSafe() {
        assertEquals(
            emptyList<ConversationEntity>(),
            ChatOrdering.mostRecentConversationsFirst(emptyList())
        )
    }
}
