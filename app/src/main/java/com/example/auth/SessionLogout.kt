package com.example.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize logout and finish local cleanup before exposing the login screen. */
internal object SessionLogout {
    private val mutex = Mutex()

    suspend fun run(
        removeDevice: suspend () -> Unit = {},
        clearLocalData: suspend () -> Unit,
        signOut: () -> Unit
    ) = mutex.withLock {
        try {
            withTimeoutOrNull(5_000) { removeDevice() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Offline token removal must not prevent local logout.
        }
        withContext(NonCancellable) {
            // Fail closed: do not allow a new account to open a dirty local cache.
            clearLocalData()
            signOut()
        }
    }
}
