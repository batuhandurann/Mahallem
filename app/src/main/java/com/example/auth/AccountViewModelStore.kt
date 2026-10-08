package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AccountViewModelStore : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var uid: String? = null

    fun selectAccount(nextUid: String?) {
        if (uid != nextUid) {
            viewModelStore.clear()
            uid = nextUid
        }
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
