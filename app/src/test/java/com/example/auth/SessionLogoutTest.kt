package com.example.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionLogoutTest {
    @Test fun offlineDeviceRemovalStillClearsDataBeforeLogout() = runTest {
        val events = mutableListOf<String>()
        SessionLogout.run(
            removeDevice = { throw IllegalStateException("offline") },
            clearLocalData = { events += "clear" },
            signOut = { events += "logout" }
        )
        assertEquals(listOf("clear", "logout"), events)
    }

    @Test fun slowNetworkCannotBlockLogoutIndefinitely() = runTest {
        var signedOut = false
        SessionLogout.run(
            removeDevice = { delay(60_000) },
            clearLocalData = {}, signOut = { signedOut = true }
        )
        assertTrue(signedOut)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test fun cleanupFailureBlocksAccountSwitchAndCanBeRetried() = runTest {
        var signedOut = false
        try {
            SessionLogout.run(clearLocalData = { error("disk failure") }, signOut = { signedOut = true })
            fail("Cleanup failure must be reported")
        } catch (_: IllegalStateException) { }
        assertFalse(signedOut)
        SessionLogout.run(clearLocalData = {}, signOut = { signedOut = true })
        assertTrue(signedOut)
    }

    @Test fun cancellationDuringDeviceRemovalIsNotSwallowed() = runTest {
        var signedOut = false
        try {
            SessionLogout.run(
                removeDevice = { throw CancellationException("screen closed") },
                clearLocalData = { fail("Cleanup must not begin after cancellation") },
                signOut = { signedOut = true }
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(signedOut)
    }
}
