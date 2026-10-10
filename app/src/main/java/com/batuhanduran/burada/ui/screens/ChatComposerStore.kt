package com.batuhanduran.burada.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Session memory only: private drafts and pending acknowledgements never enter saved state. */
class ChatComposerState {
    var messageInput by mutableStateOf("")
    var offerPriceInput by mutableStateOf("")
    var showOfferDialog by mutableStateOf(false)
    var isSending by mutableStateOf(false)

    internal fun clear() {
        messageInput = ""
        offerPriceInput = ""
        showOfferDialog = false
        isSending = false
    }
}

/** Owned by one UID-scoped MarketplaceViewModel, retained through navigation and rotation. */
class ChatComposerStore {
    private val composers = mutableMapOf<String, ChatComposerState>()

    fun forConversation(conversationId: String): ChatComposerState {
        require(conversationId.isNotBlank())
        return composers.getOrPut(conversationId) { ChatComposerState() }
    }

    fun clear() {
        composers.values.forEach { it.clear() }
        composers.clear()
    }
}
