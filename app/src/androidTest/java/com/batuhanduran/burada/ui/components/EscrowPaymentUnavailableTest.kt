package com.batuhanduran.burada.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun quote() = QuoteEntity(
        id = "quote-one",
        requestId = "request-one",
        providerId = "provider-one",
        providerName = "Test Hizmet Veren",
        providerTitle = "Tamir",
        providerRating = 0.0,
        price = "500 TL",
        durationOrArrival = "Yarın",
        notes = ""
    )

    @Test
    fun unsupportedPaymentsNeverCollectCardDetailsOrInvokeCharge() {
        var paymentCalls = 0
        var dismissed = 0
        compose.setContent {
            BuradaTheme {
                EscrowPaymentDialog(
                    quote = quote(),
                    jobTitle = "Kapı Tamiri",
                    onDismiss = { dismissed++ },
                    onConfirmPayment = { paymentCalls++ }
                )
            }
        }

        compose.onNodeWithTag("escrow_unavailable_notice").assertExists()
        compose.onNodeWithText("Ödeme özelliği henüz kullanılamıyor").assertExists()
        compose.onAllNodesWithText("Kart Numarası").assertCountEquals(0)
        compose.onAllNodesWithText("CVV").assertCountEquals(0)
        compose.onAllNodesWithTag("btn_confirm_escrow_pay").assertCountEquals(0)
        compose.onAllNodesWithText("BDDK", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("btn_payment_unavailable_close").performClick()
        compose.runOnIdle {
            assertEquals(0, paymentCalls)
            assertEquals(1, dismissed)
        }
    }
}
