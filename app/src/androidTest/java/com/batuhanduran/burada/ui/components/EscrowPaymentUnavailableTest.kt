package com.batuhanduran.burada.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EscrowPaymentUnavailableTest {
    @get:Rule val compose = createComposeRule()

    private val exampleQuote = QuoteEntity(
        id = "q1",
        requestId = "r1",
        providerId = "p1",
        providerName = "Örnek Usta",
        providerTitle = "Tamir Hizmeti",
        providerRating = 4.5,
        price = "1.250 TL",
        durationOrArrival = "Yarın",
        notes = "Test"
    )

    @Test fun paymentUnavailableNeverCollectsCardDetailsOrShowsPayButton() {
        compose.setContent {
            BuradaTheme {
                EscrowPaymentDialog(
                    quote = exampleQuote,
                    jobTitle = "Musluk Tamiri",
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Ödeme henüz kullanılamıyor").assertIsDisplayed()
        compose.onNodeWithTag("payment_unavailable_notice").assertIsDisplayed()
        compose.onNodeWithText("Kart Numarası").assertDoesNotExist()
        compose.onNodeWithText("CVV").assertDoesNotExist()
        compose.onNodeWithTag("btn_confirm_escrow_pay").assertDoesNotExist()
    }

    @Test fun unavailablePaymentHasWorkingCloseActionOnly() {
        var dismissCalls = 0
        compose.setContent {
            BuradaTheme {
                EscrowPaymentDialog(
                    quote = exampleQuote,
                    jobTitle = "Musluk Tamiri",
                    onDismiss = { dismissCalls++ }
                )
            }
        }
        compose.onNodeWithTag("btn_payment_unavailable_close").performClick()
        compose.runOnIdle { assertEquals(1, dismissCalls) }
    }
}
