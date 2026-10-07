package com.example.ui

import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Keeps account-scoped ViewModels through Activity recreation and clears them at sign-out. */
class SessionStoreViewModel : ViewModel() {
    private var activeUid: String? = null
    private var currentOwner: ViewModelStoreOwner? = null
    private var cleared = false

    @MainThread
    fun ownerFor(uid: String): ViewModelStoreOwner {
        requireMainThread()
        check(!cleared) { "SessionStoreViewModel has already been cleared." }
        require(uid.isNotBlank()) { "A signed-in user UID is required." }

        val owner = currentOwner
        if (activeUid == uid && owner != null) return owner

        clearSession()
        return SessionOwner().also {
            activeUid = uid
            currentOwner = it
        }
    }

    @MainThread
    fun clearSession() {
        requireMainThread()
        val owner = currentOwner
        // Detach first so repeated clearing cannot dispose the same owner again.
        currentOwner = null
        activeUid = null
        owner?.viewModelStore?.clear()
    }

    override fun onCleared() {
        cleared = true
        clearSession()
        super.onCleared()
    }

    private class SessionOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Session stores must be accessed on the main thread."
        }
    }
}
