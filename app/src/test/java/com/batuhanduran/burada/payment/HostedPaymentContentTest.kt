package com.batuhanduran.burada.payment

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.ui.components.HostedPaymentContent
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HostedPaymentContentTest {
    @get:Rule val compose = createComposeRule()
    private val quote = QuoteEntity(id = "q", requestId = "r", providerId = "p", providerName = "Usta",
        providerTitle = "Tamir", providerRating = 4.0, price = "250 TL", durationOrArrival = "Yarın", notes = "", status = "ACCEPTED")

    private fun show(status: CheckoutStatus, available: Boolean = true, error: String? = null, onRefresh: () -> Unit = {}) {
        compose.setContent { BuradaTheme { HostedPaymentContent(quote, "Musluk",
            PaymentAvailability(available, CheckoutEnvironment.SANDBOX), HostedCheckout(status, CheckoutEnvironment.SANDBOX),
            false, error, onRefresh, {}, {}, {}) } }
    }
    @Test fun disabledCapabilityNeverStartsPayment() {
        show(CheckoutStatus.NOT_STARTED, available = false)
        compose.onNodeWithTag("payment_start").assertDoesNotExist()
        compose.onNodeWithTag("payment_open").assertDoesNotExist()
        compose.onNodeWithTag("payment_unavailable_notice").assertIsDisplayed()
    }
    @Test fun uncertainResultAllowsRefreshWithoutDuplicateCharge() {
        var refreshed = 0
        show(CheckoutStatus.UNKNOWN, onRefresh = { refreshed++ })
        compose.onNodeWithTag("payment_start").assertDoesNotExist()
        compose.onNodeWithTag("payment_refresh").performScrollTo().performClick()
        assertEquals(1, refreshed)
    }
    @Test fun sandboxSuccessIsExplicitlyTestPayment() {
        show(CheckoutStatus.PAID)
        compose.onNodeWithText("Sunucu test ödemesini doğruladı.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("payment_start").assertDoesNotExist()
    }
    @Test fun staleNotStartedWithNetworkErrorCannotStartAgain() {
        show(CheckoutStatus.NOT_STARTED, error = "Yanıt gecikti")
        compose.onNodeWithTag("payment_start").assertDoesNotExist()
    }
}
