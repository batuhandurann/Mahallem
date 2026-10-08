package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Survives rotation, but discards private screen state and collectors on logout. */
class MarketplaceSessionStore : ViewModel() {
    private var session: String? = null
    private var owner = newOwner()

    fun selectSession(key: String?): ViewModelStoreOwner {
        if (key != session) {
            owner.viewModelStore.clear()
            owner = newOwner()
            session = key
        }
        return owner
    }

    override fun onCleared() {
        owner.viewModelStore.clear()
    }

    private fun newOwner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
