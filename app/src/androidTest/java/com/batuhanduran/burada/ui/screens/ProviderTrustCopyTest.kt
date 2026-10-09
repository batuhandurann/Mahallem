package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderTrustCopyTest {
    @get:Rule val compose = createComposeRule()

    private fun show(sector: String) {
        compose.setContent {
            BuradaTheme {
                ProviderDetailScreen(
                    provider = ServiceProviderEntity(
                        id = "provider-test",
                        name = "Test Hizmet Veren",
                        title = "Test",
                        sector = sector,
                        categoryId = "test",
                        rating = 4.0,
                        reviewCount = 0,
                        experienceYears = 1,
                        district = "Buca",
                        city = "İzmir",
                        hourlyOrBasePrice = "100 TL",
                        isEmergencyAvailable = false,
                        verifiedSafeBadge = true,
                        mykCertified = true,
                        childSafeCertified = true,
                        phone = "05000000000",
                        bio = "Test profili"
                    ),
                    onBackClick = {},
                    onFavoriteToggle = {},
                    onRequestQuoteClick = {}
                )
            }
        }
    }

    private fun assertTrust() {
        compose.onNodeWithText("Adli Sicil Onaylı").assertDoesNotExist()
        compose.onNodeWithText("MYK Usta Belgesi").assertDoesNotExist()
        compose.onNodeWithText("Çocuk Dostu").assertDoesNotExist()
        compose.onNodeWithTag("btn_detail_chat").assertIsDisplayed()
        compose.onNodeWithTag("btn_detail_request_quote").assertIsDisplayed()
        compose.onNodeWithTag("provider_detail_list")
            .performScrollToNode(hasTestTag("provider_portfolio_empty"))
        compose.onNodeWithText(
            "Bu profilde doğrulanmış çalışma fotoğrafı veya videosu henüz gösterilmiyor."
        ).assertIsDisplayed()
    }

    @Test fun renovationHasNoFabricatedProof() {
        show("HOME_REPAIR")
        assertTrust()
    }

    @Test fun eventsHasNoFabricatedProof() {
        show("EVENTS")
        assertTrust()
    }
}
