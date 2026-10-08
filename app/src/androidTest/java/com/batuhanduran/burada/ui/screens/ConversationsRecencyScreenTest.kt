package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual user-facing LazyColumn rather than re-implementing its sort in a test. */
@RunWith(AndroidJUnit4::class)
class ConversationsRecencyScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun conversation(id: String, time: Long) = ConversationEntity(
        id = id,
        participantId = "friend-$id",
        participantName = "Person $id",
        participantTitle = "",
        lastMessage = "Message $id",
        lastTimestamp = time
    )

    @Test
    fun newestConversationCardRendersAboveOlderConversation() {
        compose.setContent {
            BuradaTheme {
                ConversationsListScreen(
                    conversations = listOf(conversation("old", 10L), conversation("new", 50L)),
                    onBackClick = {},
                    onConversationClick = {}
                )
            }
        }
        val newTop = compose.onNodeWithTag("conversation_item_new")
            .fetchSemanticsNode().boundsInRoot.top
        val oldTop = compose.onNodeWithTag("conversation_item_old")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Newest conversation must appear above older one", newTop < oldTop)
    }
}
