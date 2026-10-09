package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises production ChatScreen's send button with controllable delayed Firebase acknowledgements. */
@RunWith(AndroidJUnit4::class)
class ChatSendDraftScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun conversation() = ConversationEntity(
        id = "conversation-a",
        participantId = "user-b",
        participantName = "Hizmet Veren",
        participantTitle = "Usta",
        lastMessage = "",
        lastTimestamp = 1L
    )

    @Test fun failedOrPendingSendKeepsDraftAndDisablesDuplicateTap() {
        val result = CompletableDeferred<Boolean>()
        var attempts = 0
        compose.setContent {
            BuradaTheme {
                ChatScreen(
                    conversation = conversation(),
                    messages = emptyList(),
                    onBackClick = {},
                    onSendMessage = { _, _, _ -> },
                    onSendTextConfirmed = { _ -> attempts++; result.await() },
                    onCallClick = {},
                    onReportClick = {}
                )
            }
        }

        compose.onNodeWithTag("input_chat_message").performTextInput("Bağlantı kesilse de kaybolmasın")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, attempts)
            result.complete(false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("input_chat_message")
            .assertTextContains("Bağlantı kesilse de kaybolmasın")
        compose.onNodeWithTag("btn_send_chat_message").assertIsEnabled()
    }

    @Test fun successfulSendClearsOnlyUneditedDraft() {
        val result = CompletableDeferred<Boolean>()
        var attempts = 0
        compose.setContent {
            BuradaTheme {
                ChatScreen(
                    conversation = conversation(),
                    messages = emptyList(),
                    onBackClick = {},
                    onSendMessage = { _, _, _ -> },
                    onSendTextConfirmed = { _ -> attempts++; result.await() },
                    onCallClick = {},
                    onReportClick = {}
                )
            }
        }
        compose.onNodeWithTag("input_chat_message").performTextInput("Teslim edildi")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.runOnIdle {
            assertEquals(1, attempts)
            result.complete(true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("input_chat_message").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
    }

    @Test fun typingNewDraftDuringPendingSendIsNotErased() {
        val result = CompletableDeferred<Boolean>()
        compose.setContent {
            BuradaTheme {
                ChatScreen(
                    conversation = conversation(),
                    messages = emptyList(),
                    onBackClick = {},
                    onSendMessage = { _, _, _ -> },
                    onSendTextConfirmed = { result.await() },
                    onCallClick = {},
                    onReportClick = {}
                )
            }
        }
        compose.onNodeWithTag("input_chat_message").performTextInput("İlk mesaj")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("İkinci taslak")
        compose.runOnIdle { result.complete(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("input_chat_message").assertTextContains("İkinci taslak")
    }
}
