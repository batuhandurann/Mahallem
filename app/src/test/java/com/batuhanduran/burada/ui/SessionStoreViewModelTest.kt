package com.batuhanduran.burada.ui

import androidx.lifecycle.ViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SessionStoreViewModelTest {
    private class AccountData : ViewModel() {
        var disposed = false
        override fun onCleared() { disposed = true }
    }
    @Test fun changingUidDisposesPreviousAccountStoreAndLogoutClearsNewStore() {
        val sessions = SessionStoreViewModel()
        val batuhan = sessions.ownerFor("batuhan")
        val data = AccountData()
        batuhan.viewModelStore.put("privateMessages", data)
        assertSame(batuhan, sessions.ownerFor("batuhan"))
        assertFalse(data.disposed)
        val ayse = sessions.ownerFor("ayse")
        assertTrue(data.disposed)
        assertNotSame(batuhan, ayse)
        assertNull(ayse.viewModelStore.get("privateMessages"))
        val ayseData = AccountData()
        ayse.viewModelStore.put("privateMessages", ayseData)
        sessions.clearSession()
        assertTrue(ayseData.disposed)
        sessions.clearSession()
        assertNotSame(ayse, sessions.ownerFor("ayse"))
    }
}
