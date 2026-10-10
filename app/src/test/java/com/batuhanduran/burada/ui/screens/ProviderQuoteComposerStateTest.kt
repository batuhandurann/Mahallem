package com.batuhanduran.burada.ui.screens

import org.junit.Assert.*
import org.junit.Test

class ProviderQuoteComposerStateTest {
    @Test fun failureRetainsDraftAndAcknowledgedSuccessClearsIt() {
        val draft = ProviderQuoteComposerState()
        draft.open("request", "provider")
        draft.price = "1000 TL"
        draft.arrival = "Yarın"
        draft.notes = "Malzeme dahil"
        draft.isSending = true
        draft.complete(false)
        assertTrue(draft.showDialog)
        assertFalse(draft.isSending)
        assertNotNull(draft.error)
        assertEquals("1000 TL", draft.price)
        assertEquals("Yarın", draft.arrival)
        assertEquals("Malzeme dahil", draft.notes)
        draft.isSending = true
        draft.complete(true)
        assertFalse(draft.showDialog)
        assertEquals("", draft.price)
        assertEquals("", draft.arrival)
        assertEquals("", draft.notes)
    }

    @Test fun reopeningSameTargetKeepsDraftWhileDifferentTargetStartsEmpty() {
        val draft = ProviderQuoteComposerState()
        draft.open("request", "provider")
        draft.price = "1000 TL"
        draft.showDialog = false
        draft.open("request", "provider")
        assertEquals("1000 TL", draft.price)
        draft.isSending = true
        draft.open("other", "provider")
        assertEquals("request", draft.requestId)
        draft.complete(false)
        draft.open("other", "provider")
        assertEquals("other", draft.requestId)
        assertEquals("", draft.price)
        assertNull(draft.error)
    }

    @Test fun sessionCleanupRemovesPrivateDraft() {
        val draft = ProviderQuoteComposerState()
        draft.open("request", "provider")
        draft.notes = "Özel müşteri bilgisi"
        draft.clear()
        assertNull(draft.requestId)
        assertNull(draft.providerId)
        assertEquals("", draft.notes)
        assertFalse(draft.showDialog)
    }
}
