package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
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

    // Ensure the target is actually brought into the viewport before asserting visibility.
    // The request row may be laid out below the fold on smaller emulators.
    private fun assertVisibleInRequests(label: String, substring: Boolean = false) {
        composeRule.onNodeWithTag("my_requests_list")
            .performScrollToNode(hasText(label, substring = substring))
        composeRule.onNodeWithText(label, substring = substring).assertIsDisplayed()
    }

    @Test fun cleaningRequestRendersCorrectSector() {
        renderRequest("CLEANING")
        assertVisibleInRequests("Temizlik & Bakım")
        composeRule.onNodeWithText("Kostüm:", substring = true).assertDoesNotExist()
    }

    @Test fun movingRequestNeverShowsCostumeDetail() {
        renderRequest("MOVING_ASSEMBLY")
        assertVisibleInRequests("Nakliye & Montaj")
        composeRule.onNodeWithText("Kostüm:", substring = true).assertDoesNotExist()
    }

    @Test fun unknownCodeShowsHonestFallback() {
        renderRequest("LEGACY_UNKNOWN")
        assertVisibleInRequests("Diğer Hizmet")
        composeRule.onNodeWithText("Eğlence & Organizasyon").assertDoesNotExist()
    }

    @Test fun eventRetainsEventDetails() {
        renderRequest("EVENT_ENTERTAINMENT")
        assertVisibleInRequests("Etkinlik & Eğlence")
        assertVisibleInRequests("Kostüm:", substring = true)
    }
}
