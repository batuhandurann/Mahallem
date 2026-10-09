package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderDashboardEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingProviderShowsExplanationInsteadOfEndlessSpinner() {
        var backClicks = 0
        compose.setContent {
            BuradaTheme {
                ProviderDashboardScreen(
                    providers = emptyList(),
                    requests = emptyList(),
                    onBackClick = { backClicks++ },
                    onToggleOffers = { _, _ -> },
                    onToggleCalendarDate = { _, _ -> },
                    onSubmitQuote = { _, _, _, _, _ -> }
                )
            }
        }
        compose.onNodeWithTag("provider_dashboard_empty").assertIsDisplayed()
        compose.onNodeWithText("Henüz hizmet veren ilanınız yok").assertIsDisplayed()
        compose.onNodeWithTag("btn_provider_dashboard_return_home").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }
    }
}
