package com.batuhanduran.burada.ui.screens

import org.junit.Assert.*
import org.junit.Test

class ChatComposerStoreTest {
    @Test fun returningToConversationRetainsDraftAndPendingSend() {
        val store = ChatComposerStore()
        val first = store.forConversation("first")
        first.messageInput = "Merhaba"
        first.offerPriceInput = "3500 ₺"
        first.showOfferDialog = true
        first.isSending = true

        assertNotSame(first, store.forConversation("second"))
        val returning = store.forConversation("first")
        assertSame(first, returning)
        assertEquals("Merhaba", returning.messageInput)
        assertEquals("3500 ₺", returning.offerPriceInput)
        assertTrue(returning.showOfferDialog)
        assertTrue(returning.isSending)
    }

    @Test fun accountStoresAreIsolatedAndSessionClearErasesPrivateDrafts() {
        val previousAccount = ChatComposerStore()
        val draft = previousAccount.forConversation("shared-conversation")
        draft.messageInput = "Özel mesaj"
        draft.offerPriceInput = "1200"
        draft.isSending = true

        val nextAccount = ChatComposerStore().forConversation("shared-conversation")
        assertNotSame(draft, nextAccount)
        assertEquals("", nextAccount.messageInput)
        assertFalse(nextAccount.isSending)

        previousAccount.clear()
        assertEquals("", draft.messageInput)
        assertEquals("", draft.offerPriceInput)
        assertFalse(draft.isSending)
        assertNotSame(draft, previousAccount.forConversation("shared-conversation"))
    }
}
