package com.batuhanduran.burada.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushEnvelopePolicyTest {
    private val batuhanMessage = mapOf("recipientUid" to "batuhan", "type" to "chat_message",
        "messageId" to "message-1", "conversationId" to "conversation-1")

    @Test fun oldAccountPushCannotAppearAfterSwitchOrLogout() {
        assertTrue(PushEnvelopePolicy.shouldDisplay(batuhanMessage, "batuhan"))
        assertFalse(PushEnvelopePolicy.shouldDisplay(batuhanMessage, "ayse"))
        assertFalse(PushEnvelopePolicy.shouldDisplay(batuhanMessage, null))
        assertFalse(PushEnvelopePolicy.shouldDisplay(batuhanMessage, ""))
    }

    @Test fun malformedAndOtherNotificationTypesDoNotDisplay() {
        for (key in batuhanMessage.keys) {
            assertFalse(PushEnvelopePolicy.shouldDisplay(batuhanMessage - key, "batuhan"))
        }
        assertFalse(PushEnvelopePolicy.shouldDisplay(batuhanMessage + ("type" to "advertisement"), "batuhan"))
    }
}
