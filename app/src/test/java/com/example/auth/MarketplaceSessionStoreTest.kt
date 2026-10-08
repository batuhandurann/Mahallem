package com.example.auth

import androidx.lifecycle.ViewModel
import org.junit.Assert.*
import org.junit.Test

class MarketplaceSessionStoreTest {
    private class PrivateState : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun rotationRetainsStateButAccountSwitchAndReloginDiscardIt() {
        val sessions = MarketplaceSessionStore()
        val accountA = sessions.selectSession("account-a")
        val stateA = PrivateState()
        accountA.viewModelStore.put("private", stateA)
        assertSame(accountA, sessions.selectSession("account-a"))
        assertFalse(stateA.cleared)
        val accountB = sessions.selectSession("account-b")
        assertTrue(stateA.cleared)
        assertNull(accountB.viewModelStore.get("private"))
        val stateB = PrivateState()
        accountB.viewModelStore.put("private", stateB)
        sessions.selectSession(null)
        assertTrue(stateB.cleared)
        assertNull(sessions.selectSession("account-a").viewModelStore.get("private"))
    }
}
