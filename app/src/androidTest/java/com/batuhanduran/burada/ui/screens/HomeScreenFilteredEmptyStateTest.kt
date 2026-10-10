package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.model.FeedFlowType
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.data.model.UrgencyMode
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenFilteredEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    private val exampleRequest = JobRequestEntity(
        id = "request-1",
        title = "Temizlik için yardım",
        sector = "CLEANING",
        categoryId = "home-cleaning",
        district = "Buca",
        urgencyMode = "PLANNED",
        eventOrJobDate = "2026-10-12",
        eventTime = "12:00",
        address = "Buca",
        status = "PENDING",
        customerName = "Test",
        customerPhone = "05000000000"
    )

    private val exampleProvider = ServiceProviderEntity(
        id = "provider-1",
        name = "Örnek Temizlik",
        title = "Temizlik Hizmeti",
        sector = "CLEANING",
        categoryId = "home-cleaning",
        rating = 4.8,
        reviewCount = 5,
        experienceYears = 3,
        district = "Buca",
        city = "İzmir",
        hourlyOrBasePrice = "100 TL",
        isEmergencyAvailable = false,
        verifiedSafeBadge = false,
        mykCertified = false,
        childSafeCertified = false,
        phone = "05000000000",
        bio = "Test açıklaması"
    )

    private fun show(
        flow: FeedFlowType,
        providers: List<ServiceProviderEntity>,
        requests: List<JobRequestEntity>
    ) {
        compose.setContent {
            BuradaTheme {
                HomeScreen(
                    providers = providers,
                    jobRequests = requests,
                    feedFlowType = flow,
                    onFeedFlowTypeSelected = {},
                    selectedSector = SectorType.ALL,
                    onSectorSelected = {},
                    selectedUrgency = UrgencyMode.ALL,
                    onUrgencySelected = {},
                    selectedCategory = null,
                    onCategorySelected = {},
                    searchQuery = "",
                    onSearchQueryChanged = {},
                    searchSuggestions = emptyList(),
                    onSuggestionSelected = {},
                    selectedDistrict = "Buca",
                    onDistrictSelected = {},
                    onProviderClick = {},
                    onFavoriteToggle = {},
                    onRequestQuoteForProvider = {},
                    onChatWithProvider = {},
                    onChatForJobRequest = {},
                    onCreateRequestClick = {},
                    onPublishOfferClick = {},
                    onEmergencyTriggerClick = {},
                    onMyRequestsClick = {},
                    onMessagesClick = {},
                    activeRequestsCount = 0,
                    unreadMessagesCount = 0,
                    isProviderMode = false,
                    onToggleProviderMode = {},
                    onReportListing = { _, _, _ -> }
                )
            }
        }
    }

    private fun assertEmptyExplanationVisible() {
        compose.onNodeWithTag("home_list")
            .performScrollToNode(hasText("Kriterlere Uygun İlan Bulunamadı"))
        compose.onNodeWithText("Kriterlere Uygun İlan Bulunamadı")
            .assertIsDisplayed()
    }

    @Test fun providerTabExplainsEmptyResultsEvenWithOtherTabRequests() {
        show(FeedFlowType.PROVIDER_OFFERS, emptyList(), listOf(exampleRequest))
        assertEmptyExplanationVisible()
    }

    @Test fun requestTabExplainsEmptyResultsEvenWithOtherTabProviders() {
        show(FeedFlowType.SEEKER_REQUESTS, listOf(exampleProvider), emptyList())
        assertEmptyExplanationVisible()
    }
}
