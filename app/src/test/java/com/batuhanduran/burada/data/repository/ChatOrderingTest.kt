package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ChatMessageEntity
import com.batuhanduran.burada.data.local.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOrderingTest {
    private fun message(index: Int) = ChatMessageEntity(
        id = "msg-$index",
        conversationId = "conversation",
        senderId = "alice",
        senderName = "Alice",
        text = "message $index",
        timestamp = index.toLong(),
        isFromMe = false
    )

    private fun conversation(id: String, lastActivity: Long) = ConversationEntity(
        id = id,
        participantId = "other",
        participantName = "Other",
        participantTitle = "",
        lastMessage = "",
        lastTimestamp = lastActivity
    )

    @Test
    fun newestFirestoreWindowIsShownOldestToNewest() {
        // Simulates the 200 newest results of a descending Firestore query over 205 messages.
        val newestFirst = (205 downTo 6).map(::message)
        val shown = ChatOrdering.oldestFirst(newestFirst)
        assertEquals(ChatOrdering.RECENT_MESSAGE_LIMIT.toInt(), shown.size)
        assertEquals("msg-6", shown.first().id)
        assertEquals("msg-205", shown.last().id)
        assertTrue(shown.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
    }

    @Test
    fun emptyMessagesDoNotCrash() {
        assertEquals(emptyList<ChatMessageEntity>(), ChatOrdering.oldestFirst(emptyList()))
    }

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
}
