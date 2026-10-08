package com.example.auth

import com.example.data.local.AppDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// All sign-in paths wait for disk cleanup before allowing a new account session.
internal object SessionOperations {
    private val mutex = Mutex()

    suspend fun <T> authenticate(
        clearLocalData: suspend () -> Unit = { AppDatabase.clearLocalData() },
        action: suspend () -> T
    ): T = mutex.withLock {
        clearLocalData()
        action()
    }

    suspend fun signOut(
        unregister: suspend () -> Unit = {},
        clearLocalData: suspend () -> Unit = { AppDatabase.clearLocalData() },
        endSession: () -> Unit
    ) = withContext(NonCancellable) {
        mutex.withLock {
            try {
                try {
                    withTimeout(5_000) { unregister() }
                } catch (_: TimeoutCancellationException) {
                    // A failed/offline push-token removal must not prevent local sign-out.
                } catch (_: Exception) {
                    // The caller is still authenticated while unregister is attempted.
                }
                clearLocalData()
            } finally {
                // Even a database failure must never leave the private account UI signed in.
                endSession()
            }
        }
    }
}
