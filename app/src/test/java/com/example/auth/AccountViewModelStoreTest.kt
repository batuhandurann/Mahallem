package com.example.auth

import androidx.lifecycle.ViewModel
import org.junit.Assert.*
import org.junit.Test

class AccountViewModelStoreTest {
    private class AccountData : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun sameAccountRetainsDataButSwitchAndLogoutDisposeIt() {
        val owner = AccountViewModelStore()
        owner.selectAccount("A")
        val first = AccountData()
        owner.viewModelStore.put("private", first)
        owner.selectAccount("A")
        assertSame(first, owner.viewModelStore.get("private"))
        assertFalse(first.cleared)
        owner.selectAccount("B")
        assertTrue(first.cleared)
        assertNull(owner.viewModelStore.get("private"))
        val second = AccountData()
        owner.viewModelStore.put("private", second)
        owner.selectAccount(null)
        assertTrue(second.cleared)
        assertNull(owner.viewModelStore.get("private"))
        owner.selectAccount("A")
        assertNull(owner.viewModelStore.get("private"))
    }
}
