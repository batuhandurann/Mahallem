package com.batuhanduran.burada.ui.screens

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProviderQuoteDraftTest {
    @get:Rule val compose = createComposeRule()
    private val composer = ProviderQuoteComposerState()
    private val completions = mutableListOf<(Boolean) -> Unit>()

    @Test fun failedSubmissionKeepsFormAndBusySendCannotBeDuplicated() {
        val provider = ServiceProviderEntity("provider", "Ayşe", "Boyacı", "HOME_REPAIR", "boyaci",
            0.0, 0, 2, "Buca", "İzmir", "1000 TL", false, false, false, false, phone = "", bio = "")
        val request = JobRequestEntity("request", "Boya işi", "HOME_REPAIR", "boyaci", "Buca",
            "PLANNED", "2026-10-20", "14:00", "", "PENDING", "Ali", "")
        compose.setContent {
            BuradaTheme {
                ProviderDashboardScreen(listOf(provider), listOf(request), {}, { _, _ -> }, { _, _ -> },
                    { _, _, _, _, _, complete -> completions += complete }, quoteComposer = composer)
            }
        }
        compose.onNodeWithTag("btn_give_quote_request").performScrollTo().performClick()
        compose.onNodeWithTag("input_quote_price").performTextReplacement("1000 TL")
        compose.onNodeWithTag("input_quote_arrival").performTextReplacement("Yarın 14.00")
        compose.onNodeWithTag("input_quote_notes").performTextReplacement("Malzeme dahil")
        compose.onNodeWithTag("btn_confirm_send_quote").performClick()
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, completions.size)
            completions.single()(false)
        }
        compose.onNodeWithTag("input_quote_price").assertTextContains("1000 TL")
        compose.onNodeWithTag("input_quote_arrival").assertTextContains("Yarın 14.00")
        compose.onNodeWithTag("quote_send_error").assertExists()
        compose.onNodeWithTag("btn_confirm_send_quote").assertIsEnabled().performClick()
        compose.runOnIdle { completions.last()(true) }
        compose.onNodeWithTag("input_quote_price").assertDoesNotExist()
        compose.runOnIdle { assertEquals("", composer.price) }
    }
}
