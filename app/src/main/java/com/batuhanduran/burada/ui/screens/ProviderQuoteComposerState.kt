package com.batuhanduran.burada.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Private in-memory draft, owned by the UID-scoped marketplace ViewModel. */
class ProviderQuoteComposerState {
    var requestId by mutableStateOf<String?>(null)
        private set
    var providerId by mutableStateOf<String?>(null)
        private set
    var showDialog by mutableStateOf(false)
    var price by mutableStateOf("")
    var arrival by mutableStateOf("")
    var notes by mutableStateOf("")
    var isSending by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun open(requestId: String, providerId: String) {
        if (isSending) return
        if (this.requestId != requestId || this.providerId != providerId) {
            price = ""
            arrival = ""
            notes = ""
            error = null
        }
        this.requestId = requestId
        this.providerId = providerId
        showDialog = true
    }

    fun complete(success: Boolean) {
        isSending = false
        if (success) {
            showDialog = false
            price = ""
            arrival = ""
            notes = ""
            error = null
        } else {
            error = "Teklif gönderilemedi. Bilgileriniz korunuyor; bağlantınızı kontrol edip yeniden deneyin."
        }
    }

    fun clear() {
        requestId = null
        providerId = null
        showDialog = false
        price = ""
        arrival = ""
        notes = ""
        isSending = false
        error = null
    }
}
