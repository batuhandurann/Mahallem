package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderQuoteFormTest {
    @get:Rule val compose = createComposeRule()

    private val provider = ServiceProviderEntity(
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
        isEmergencyAvailable = true,
        verifiedSafeBadge = false,
        mykCertified = false,
        childSafeCertified = false,
        phone = "",
        bio = "Örnek",
        ownerUid = "account-1"
    )
    private val request = JobRequestEntity(
        id = "request-1",
        title = "Temizlik",
        sector = "CLEANING",
        categoryId = "home-cleaning",
        district = "Buca",
        urgencyMode = "EMERGENCY",
        eventOrJobDate = "2026-10-10",
        eventTime = "16:00",
        address = "",
        status = "PENDING",
        customerName = "",
        customerPhone = ""
    )

    @Test fun quoteCannotBeSentUntilProviderEntersTheirOwnPriceAndArrival() {
        var sent = 0
        var sentPrice = ""
        var sentArrival = ""
        compose.setContent {
            BuradaTheme {
                ProviderDashboardScreen(
                    providers = listOf(provider),
                    requests = listOf(request),
                    onBackClick = {},
                    onToggleOffers = { _, _ -> },
                    onToggleCalendarDate = { _, _ -> },
                    onSubmitQuote = { _, _, price, arrival, _ ->
                        sent++
                        sentPrice = price
                        sentArrival = arrival
                    }
                )
            }
        }
        compose.onNodeWithTag("provider_dashboard_list")
            .performScrollToNode(hasTestTag("btn_give_quote_request-1"))
        compose.onNodeWithTag("btn_give_quote_request-1").performClick()

        // No fabricated 2.800 TL amount, automatic 45-minute promise or fake warranty.
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsNotEnabled()
        compose.onNodeWithText("45 dakikada kapınızdayım").assertDoesNotExist()

        compose.onNodeWithTag("input_quote_price").performTextInput("1abc2")
        compose.onNodeWithTag("input_quote_arrival").performTextInput("Yarın 14.00")
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsNotEnabled()

        // Replacing the bad price with a real price is the only way to enable sending.
        compose.runOnIdle { assertEquals(0, sent) }
        compose.onNodeWithTag("input_quote_price").performTextClearance()
        compose.onNodeWithTag("input_quote_price").performTextInput("1250 TL")
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsEnabled()
    }

    @Test fun validProviderEnteredQuoteIsPassedUnchanged() {
        var sent = 0
        var price = ""
        var arrival = ""
        compose.setContent {
            BuradaTheme {
                ProviderDashboardScreen(
                    providers = listOf(provider),
                    requests = listOf(request),
                    onBackClick = {},
                    onToggleOffers = { _, _ -> },
                    onToggleCalendarDate = { _, _ -> },
                    onSubmitQuote = { _, _, p, a, _ ->
                        sent++
                        price = p
                        arrival = a
                    }
                )
            }
        }
        compose.onNodeWithTag("provider_dashboard_list")
            .performScrollToNode(hasTestTag("btn_give_quote_request-1"))
        compose.onNodeWithTag("btn_give_quote_request-1").performClick()
        compose.onNodeWithTag("input_quote_price").performTextInput("1.250,50 TL")
        compose.onNodeWithTag("input_quote_arrival").performTextInput("Yarın 14.00")
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, sent)
            assertEquals("1.250,50 TL", price)
            assertEquals("Yarın 14.00", arrival)
        }
    }
}
