package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyAndAccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacyNoticeMustActuallyBeAcknowledgedBeforeContinue() {
        composeRule.setContent {
            MyApplicationTheme {
                PrivacyConsentScreen(userId = "uid-test", onCompleted = {})
            }
        }

        composeRule.onNode(hasTestTag("privacy_open_notice")).performClick()
        composeRule.onNode(hasTestTag("privacy_notice_dialog")).assertIsDisplayed()
        composeRule.onNode(hasTestTag("privacy_notice_ack")).performClick()
        composeRule.onNode(hasTestTag("privacy_continue")).assertIsDisplayed()
    }

    @Test
    fun localAccountScreenHidesRealAccountDestructiveActions() {
        composeRule.setContent {
            MyApplicationTheme {
                AccountSettingsScreen(isLocalMode = true)
            }
        }

        composeRule.onNode(hasTestTag("account_settings_screen")).assertIsDisplayed()
    }

    @Test
    fun requestedDeletionShowsRecoveryAction() {
        composeRule.setContent {
            MyApplicationTheme {
                AccountDeletionPendingScreen(status = "REQUESTED", onCanceled = {})
            }
        }

        composeRule.onNode(hasTestTag("account_deletion_pending_screen")).assertIsDisplayed()
        composeRule.onNode(hasTestTag("account_cancel_deletion")).assertIsDisplayed()
    }
}
