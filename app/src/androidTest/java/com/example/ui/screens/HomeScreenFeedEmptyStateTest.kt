package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.InitialData
import com.example.data.local.JobRequestEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rendering regression: fails if HomeScreen stops using the production policy. */
@RunWith(AndroidJUnit4::class)
class HomeScreenFeedEmptyStateTest {
    @get:Rule val composeRule = createComposeRule()
    private val emptyTitle = "Kriterlere Uygun İlan Bulunamadı"

    @Test fun providerFeedEmptyEvenWhenRequestsExist() {
        render(FeedFlowType.PROVIDER_OFFERS, emptyList(), listOf(request()))
        assertEmptyStateDisplayed()
    }

    @Test fun requestFeedEmptyEvenWhenProvidersExist() {
        render(FeedFlowType.SEEKER_REQUESTS, InitialData.getSeedProviders().take(1), emptyList())
        assertEmptyStateDisplayed()
    }

    @Test fun allFeedEmptyWhenBothListsEmpty() {
        render(FeedFlowType.ALL, emptyList(), emptyList())
        assertEmptyStateDisplayed()
    }

    @Test fun providerFeedWithResultsDoesNotShowEmptyState() {
        render(FeedFlowType.PROVIDER_OFFERS, InitialData.getSeedProviders().take(1), emptyList())
        composeRule.onNodeWithText(emptyTitle).assertDoesNotExist()
    }

    private fun assertEmptyStateDisplayed() {
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasText(emptyTitle))
        composeRule.onNodeWithText(emptyTitle).assertIsDisplayed()
    }

    private fun request() = JobRequestEntity(
        id = 1, title = "Test talebi", sector = "HOME_REPAIR", categoryId = "boyaci",
        district = "Buca", urgencyMode = "PLANNED", eventOrJobDate = "2030-01-01",
        eventTime = "12:00", address = "Test", status = "PENDING",
        customerName = "Test", customerPhone = "05000000000"
    )

    private fun render(flow: FeedFlowType, providers: List<ServiceProviderEntity>, requests: List<JobRequestEntity>) {
        composeRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    providers = providers, jobRequests = requests, feedFlowType = flow,
                    onFeedFlowTypeSelected = {}, selectedSector = SectorType.ALL,
                    onSectorSelected = {}, selectedUrgency = UrgencyMode.ALL,
                    onUrgencySelected = {}, selectedCategory = null, onCategorySelected = {},
                    searchQuery = "", onSearchQueryChanged = {}, searchSuggestions = emptyList(),
                    onSuggestionSelected = {}, selectedDistrict = "Buca", onDistrictSelected = {},
                    onProviderClick = {}, onFavoriteToggle = {}, onRequestQuoteForProvider = {},
                    onChatWithProvider = {}, onChatForJobRequest = {}, onCreateRequestClick = {},
                    onPublishOfferClick = {}, onEmergencyTriggerClick = {}, onMyRequestsClick = {},
                    onMessagesClick = {}, activeRequestsCount = 0, unreadMessagesCount = 0,
                    isProviderMode = false, onToggleProviderMode = {}, onReportListing = { _, _, _ -> }
                )
            }
        }
    }
}
