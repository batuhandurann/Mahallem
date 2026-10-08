package com.example.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOperationsTest {
    @Test fun offlinePushRemovalStillClearsAndEndsSession() = runTest {
        val events = mutableListOf<String>()
        SessionOperations.signOut(
            unregister = { throw IllegalStateException("offline") },
            clearLocalData = { events.add("clear") },
            endSession = { events.add("signOut") }
        )
        assertEquals(listOf("clear", "signOut"), events)
    }

    @Test fun pushRemovalTimeoutDoesNotHangLogout() = runTest {
        var signedOut = false
        SessionOperations.signOut(unregister = { delay(60_000) }, clearLocalData = {}, endSession = { signedOut = true })
        assertTrue(signedOut)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test fun databaseFailureIsReportedAndSessionStillEnds() = runTest {
        var signedOut = false
        val failure = IllegalStateException("disk failure")
        val actual = runCatching {
            SessionOperations.signOut(clearLocalData = { throw failure }, endSession = { signedOut = true })
        }.exceptionOrNull()
        assertSame(failure, actual)
        assertTrue(signedOut)
    }

    @Test fun canceledUiCannotSkipCleanupOrSignOut() = runTest {
        val started = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        var cleared = false
        var signedOut = false
        val job = launch {
            SessionOperations.signOut(clearLocalData = {
                started.complete(Unit)
                proceed.await()
                cleared = true
            }, endSession = { signedOut = true })
        }
        started.await()
        job.cancel()
        proceed.complete(Unit)
        job.join()
        assertTrue(cleared)
        assertTrue(signedOut)
    }

    @Test fun nextAccountWaitsForLogoutAndItsOwnCleanup() = runTest {
        val started = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val logout = launch {
            SessionOperations.signOut(clearLocalData = {
                started.complete(Unit)
                proceed.await()
                events.add("clear A")
            }, endSession = { events.add("signOut A") })
        }
        started.await()
        val login = async {
            SessionOperations.authenticate(clearLocalData = { events.add("clear before B") }) { events.add("signIn B") }
        }
        runCurrent()
        assertTrue(events.isEmpty())
        proceed.complete(Unit)
        logout.join()
        login.await()
        assertEquals(listOf("clear A", "signOut A", "clear before B", "signIn B"), events)
    }

    @Test fun failedCleanupBlocksNewAccountAuthentication() = runTest {
        var loginAttempted = false
        val result = runCatching {
            SessionOperations.authenticate(clearLocalData = { error("disk unavailable") }) { loginAttempted = true }
        }
        assertTrue(result.isFailure)
        assertFalse(loginAttempted)
    }
}
