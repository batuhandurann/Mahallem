package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyRequestsSectorScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderRequest(sector: String) {
        val request = JobRequestEntity(
            id = "test-123",
            title = "Hizmet ihtiyacım",
            sector = sector,
            categoryId = "general",
            district = "Buca",
            urgencyMode = "PLANNED",
            eventOrJobDate = "2026-10-10",
            eventTime = "12:00",
            address = "Buca",
            status = "PENDING",
            customerName = "Test Kullanıcısı",
            customerPhone = "05000000000"
        )
        composeRule.setContent {
            BuradaTheme {
                MyRequestsScreen(
                    requests = listOf(request),
                    quotes = emptyList(),
                    onBackClick = {},
                    onAcceptQuote = { _, _, _ -> },
                    onRejectQuote = {},
                    onNewRequestClick = {}
                )
            }
        }
    }

    @Test fun cleaningRequestRendersCorrectSector() {
        renderRequest("CLEANING")
        composeRule.onNodeWithText("Temizlik & Bakım").assertIsDisplayed()
        composeRule.onNodeWithText("Kostüm:", substring = true).assertDoesNotExist()
    }

    @Test fun movingRequestNeverShowsCostumeDetail() {
        renderRequest("MOVING_ASSEMBLY")
        composeRule.onNodeWithText("Nakliye & Montaj").assertIsDisplayed()
        composeRule.onNodeWithText("Kostüm:", substring = true).assertDoesNotExist()
    }

    @Test fun unknownCodeShowsHonestFallback() {
        renderRequest("LEGACY_UNKNOWN")
        composeRule.onNodeWithText("Diğer Hizmet").assertIsDisplayed()
        composeRule.onNodeWithText("Eğlence & Organizasyon").assertDoesNotExist()
    }

    @Test fun eventRetainsEventDetails() {
        renderRequest("EVENT_ENTERTAINMENT")
        composeRule.onNodeWithText("Etkinlik & Eğlence").assertIsDisplayed()
        composeRule.onNodeWithText("Kostüm:", substring = true).assertIsDisplayed()
    }
}
