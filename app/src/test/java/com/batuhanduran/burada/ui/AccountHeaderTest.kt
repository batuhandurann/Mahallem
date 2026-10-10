package com.batuhanduran.burada.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.batuhanduran.burada.auth.AuthUiState
import com.batuhanduran.burada.auth.AuthUser
import com.batuhanduran.burada.data.remote.ProfileSyncState
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val user = AuthUser("alice", "Ayşe Yılmaz", "alice@example.com")
    private val state = mutableStateOf(AuthUiState(initialized = true, user = user))
    private var resends = 0
    private var refreshes = 0
    private var signouts = 0

    private fun show() {
        compose.setContent {
            BuradaTheme {
                AccountHeader(state.value.user!!, ProfileSyncState(synced = true), state.value,
                    null, {}, {}, { signouts++ }, {}, { resends++ }, { refreshes++ }, {})
            }
        }
    }

    @Test fun compactHeaderOpensAccountControlsAndCooldownDisablesResend() {
        show()
        compose.onNodeWithTag("btn_resend_verification").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hesap ayarları").performClick()
        compose.onNodeWithTag("btn_resend_verification").performClick()
        compose.runOnIdle {
            assertEquals(1, resends)
            state.value = state.value.copy(verificationResendSeconds = 30)
        }
        compose.onNodeWithTag("btn_resend_verification").assertIsNotEnabled()
        compose.onNodeWithTag("btn_refresh_verification").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun pendingAccountActionKeepsDialogAndDisablesLogoutAndVerification() {
        show()
        compose.onNodeWithTag("btn_account_settings").performClick()
        compose.runOnIdle { state.value = state.value.copy(busy = true, accountActionBusy = true) }
        compose.onNodeWithText("İşlem sürüyor…").assertIsDisplayed()
        compose.onNodeWithTag("btn_resend_verification").assertIsNotEnabled()
        compose.onNodeWithTag("btn_refresh_verification").assertIsNotEnabled()
        compose.onNodeWithTag("btn_sign_out").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, signouts) }
    }

    @Test fun verifiedAccountHidesResendAndRefreshControls() {
        state.value = state.value.copy(user = user.copy(emailVerified = true))
        show()
        compose.onNodeWithTag("btn_account_settings").performClick()
        compose.onNodeWithText("E-posta doğrulandı").assertIsDisplayed()
        compose.onNodeWithTag("btn_resend_verification").assertDoesNotExist()
        compose.onNodeWithTag("btn_refresh_verification").assertDoesNotExist()
    }
}
