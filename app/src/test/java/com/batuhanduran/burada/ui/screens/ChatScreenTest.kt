package com.batuhanduran.burada.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    private data class Send(val text: String, val isOffer: Boolean, val price: String, val complete: (Boolean) -> Unit)
    private val sends = mutableListOf<Send>()
    private val conversation = mutableStateOf<ConversationEntity?>(chat("first"))
    private val blocked = mutableStateOf(false)
    private val visible = mutableStateOf(true)
    private val recreation = mutableStateOf(0)

    private fun showChat(store: ChatComposerStore? = null) {
        compose.setContent {
            BuradaTheme {
                if (visible.value) key(recreation.value) {
                    ChatScreen(conversation = conversation.value, messages = emptyList(),
                        composerState = conversation.value?.id?.let { store?.forConversation(it) },
                        onBackClick = {}, onCallClick = {}, onReportClick = {},
                        isBlocked = blocked.value,
                        onSendMessage = { text, isOffer, price, complete -> sends += Send(text, isOffer, price, complete) })
                }
            }
        }
    }

    @Test fun quickReplyOnlyPreparesAnEditableDraft() {
        showChat()
        compose.onNodeWithText(QUICK_REPLY_QUESTIONS.first()).performClick()
        compose.onNodeWithTag("input_chat_message").assertTextEquals(QUICK_REPLY_QUESTIONS.first())
        compose.runOnIdle { assertTrue(sends.isEmpty()) }
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Yarın müsait misiniz?")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.runOnIdle { assertEquals("Yarın müsait misiniz?", sends.single().text) }
    }

    @Test fun failedMessageRetainsDraftAndSuccessClearsItOnlyAfterAcknowledgement() {
        showChat()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, sends.size)
            sends.single().complete(false)
        }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsEnabled().performClick()
        compose.runOnIdle { sends.last().complete(true) }
        compose.onNodeWithTag("input_chat_message")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled()
    }

    @Test fun successfulPendingMessageDoesNotEraseANewerDraft() {
        showChat()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("İlk mesaj")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Sonraki mesaj")
        compose.runOnIdle { sends.single().complete(true) }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Sonraki mesaj")
        compose.onNodeWithTag("btn_send_chat_message").assertIsEnabled()
    }

    @Test fun previousConversationAcknowledgementCannotClearTheCurrentDraft() {
        showChat()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.runOnIdle { conversation.value = chat("second") }
        // Identical text makes this sensitive to callbacks accidentally sharing state.
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Merhaba")
        compose.runOnIdle { sends.single().complete(true) }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsEnabled()
    }

    @Test fun failedOfferKeepsDialogAndAmountAndCanBeRetried() {
        showChat()
        compose.onNodeWithTag("btn_quick_offer").performClick()
        compose.onNodeWithTag("input_offer_price_dialog").performTextReplacement("3500 ₺")
        compose.onNodeWithTag("btn_confirm_send_offer").performClick()
        compose.onNodeWithTag("btn_confirm_send_offer").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, sends.size)
            assertTrue(sends.single().isOffer)
            assertEquals("3500 ₺", sends.single().price)
            sends.single().complete(false)
        }
        compose.onNodeWithTag("input_offer_price_dialog").assertTextContains("3500 ₺")
        compose.onNodeWithTag("btn_confirm_send_offer").assertIsEnabled().performClick()
        compose.runOnIdle { sends.last().complete(true) }
        compose.onNodeWithTag("input_offer_price_dialog").assertDoesNotExist()
        compose.onNodeWithTag("btn_quick_offer").performClick()
        compose.onNodeWithTag("btn_confirm_send_offer").assertIsNotEnabled()
    }

    @Test fun unavailableOrBlockedConversationCannotSend() {
        conversation.value = null
        showChat()
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled()
        compose.onNodeWithTag("btn_quick_offer").assertIsNotEnabled()
        compose.runOnIdle {
            conversation.value = chat("first")
            blocked.value = true
        }
        compose.onNodeWithTag("btn_send_chat_message").assertDoesNotExist()
        compose.onNodeWithTag("btn_quick_offer").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sends.isEmpty()) }
    }

    @Test fun navigationAwayAndBackRetainsPendingSendAndFailedDraft() {
        showChat(ChatComposerStore())
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("input_chat_message").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, sends.size)
            sends.single().complete(false)
        }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Merhaba")
        compose.onNodeWithTag("btn_send_chat_message").assertIsEnabled()
    }

    @Test fun unavailableCallAndVoiceFeaturesHaveDisabledControls() {
        showChat()
        compose.onNodeWithTag("btn_chat_call").assertIsNotEnabled()
        compose.onNodeWithTag("btn_send_voice_note").assertIsNotEnabled()
        compose.onNodeWithText("Mesaj yaz…").assertExists()
    }

    @Test fun recreatedCompositionRetainsPendingOfferUntilOriginalAcknowledgement() {
        showChat(ChatComposerStore())
        compose.onNodeWithTag("btn_quick_offer").performClick()
        compose.onNodeWithTag("input_offer_price_dialog").performTextReplacement("3500 ₺")
        compose.onNodeWithTag("btn_confirm_send_offer").performClick()
        compose.runOnIdle { recreation.value++ }
        compose.onNodeWithTag("input_offer_price_dialog").assertTextContains("3500 ₺")
        compose.onNodeWithTag("btn_confirm_send_offer").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, sends.size)
            sends.single().complete(true)
        }
        compose.onNodeWithTag("input_offer_price_dialog").assertDoesNotExist()
    }

    @Test fun returningToFirstConversationReceivesItsOriginalAcknowledgement() {
        showChat(ChatComposerStore())
        compose.onNodeWithTag("input_chat_message").performTextReplacement("İlk mesaj")
        compose.onNodeWithTag("btn_send_chat_message").performClick()
        compose.runOnIdle { conversation.value = chat("second") }
        compose.onNodeWithTag("input_chat_message").performTextReplacement("Diğer sohbet")
        compose.runOnIdle { conversation.value = chat("first") }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("İlk mesaj")
        compose.onNodeWithTag("btn_send_chat_message").assertIsNotEnabled()
        compose.runOnIdle { sends.single().complete(true) }
        compose.onNodeWithTag("input_chat_message")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle { conversation.value = chat("second") }
        compose.onNodeWithTag("input_chat_message").assertTextEquals("Diğer sohbet")
    }

    private companion object {
        fun chat(id: String) = ConversationEntity(id, "other-uid", "Ayşe", "Usta", "", 0L)
    }
}
