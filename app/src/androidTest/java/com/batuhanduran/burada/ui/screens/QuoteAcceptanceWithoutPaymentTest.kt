package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteAcceptanceWithoutPaymentTest {
    @get:Rule val compose = createComposeRule()

    private val request = JobRequestEntity(
        id = "request-1",
        title = "Musluk tamiri",
        sector = "HOME_REPAIR",
        categoryId = "plumbing",
        district = "Buca",
        urgencyMode = "PLANNED",
        eventOrJobDate = "2026-10-11",
        eventTime = "12:00",
        address = "Buca",
        status = "PENDING",
        customerName = "Test Müşteri",
        customerPhone = "05000000000"
    )

    private val quote = QuoteEntity(
        id = "quote-1",
        requestId = "request-1",
        providerId = "provider-1",
        providerName = "Örnek Usta",
        providerTitle = "Tamir",
        providerRating = 5.0,
        price = "1200 TL",
        durationOrArrival = "Yarın",
        notes = "Örnek teklif"
    )

    @Test fun acceptingQuoteDoesNotTriggerUnavailablePayment() {
        var accepts = 0
        var paymentInfoClicks = 0
        compose.setContent {
            BuradaTheme {
                MyRequestsScreen(
                    requests = listOf(request.copy(sector = "CLEANING")),
                    quotes = listOf(quote),
                    onBackClick = {},
                    onAcceptQuote = { requestId, quoteId, providerName ->
                        assertEquals("request-1", requestId)
                        assertEquals("quote-1", quoteId)
                        assertEquals("Örnek Usta", providerName)
                        accepts++
                    },
                    onAcceptWithEscrow = { _, _ -> paymentInfoClicks++ },
                    onRejectQuote = {},
                    onNewRequestClick = {}
                )
            }
        }
        compose.onNodeWithTag("my_requests_list").performScrollToNode(hasText("Temizlik & Bakım"))
        compose.onNodeWithText("Temizlik & Bakım").assertIsDisplayed()
        compose.onNodeWithText("Kostüm:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("btn_accept_quote_quote-1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, accepts)
            assertEquals(0, paymentInfoClicks)
        }
    }

    @Test fun paymentInfoIsDistinctFromQuoteAcceptance() {
        var accepts = 0
        var paymentInfoClicks = 0
        compose.setContent {
            BuradaTheme {
                MyRequestsScreen(
                    requests = listOf(request),
                    quotes = listOf(quote),
                    onBackClick = {},
                    onAcceptQuote = { _, _, _ -> accepts++ },
                    onAcceptWithEscrow = { _, _ -> paymentInfoClicks++ },
                    onRejectQuote = {},
                    onNewRequestClick = {}
                )
            }
        }
        compose.onNodeWithTag("btn_escrow_info_quote-1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(0, accepts)
            assertEquals(1, paymentInfoClicks)
        }
    }
}
