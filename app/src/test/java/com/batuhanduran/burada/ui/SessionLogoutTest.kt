package com.batuhanduran.burada.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutTest {
    @Test fun successfulCleanupSignsOutOriginalAccount() = runTest {
        val events = mutableListOf<String>()
        finishSessionLogout("alice", { "alice" }, { events += "cleanup" }, { events += "logout" })
        assertEquals(listOf("cleanup", "logout"), events)
    }

    @Test fun cleanupFailureDoesNotCrashOrPreventLogout() = runTest {
        var signedOut = false
        finishSessionLogout("alice", { "alice" }, { throw IllegalStateException("SDK unavailable") }, { signedOut = true })
        assertTrue(signedOut)
    }

    @Test fun unavailableNetworkCannotKeepUserTrappedInAccount() = runTest {
        var signedOut = false
        val startedAt = testScheduler.currentTime
        finishSessionLogout("alice", { "alice" }, { awaitCancellation() }, { signedOut = true })
        assertTrue(signedOut)
        assertEquals(5_000L, testScheduler.currentTime - startedAt)
    }

    @Test fun delayedCleanupCannotSignOutReplacementAccount() = runTest {
        var uid: String? = "alice"
        var signedOut = false
        finishSessionLogout("alice", { uid }, {
            uid = "bob"
            throw IllegalStateException("Late cleanup error")
        }, { signedOut = true })
        assertFalse(signedOut)
        assertEquals("bob", uid)
    }

    @Test fun cancellationStillSignsOutOriginalAccountAndRemainsCancelled() = runTest {
        var signedOut = false
        var cancellationPropagated = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                finishSessionLogout("alice", { "alice" }, { awaitCancellation() }, { signedOut = true })
            } catch (cancelled: CancellationException) {
                cancellationPropagated = true
                throw cancelled
            }
        }
        job.cancelAndJoin()
        assertTrue(signedOut)
        assertTrue(cancellationPropagated)
        assertTrue(job.isCancelled)
    }
}
