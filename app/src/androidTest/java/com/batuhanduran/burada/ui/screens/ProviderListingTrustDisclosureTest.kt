package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderListingTrustDisclosureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unverifiedCredentialsCannotBeSelfGranted() {
        var submission: Triple<Boolean, Boolean, Boolean>? = null
        compose.setContent {
            BuradaTheme {
                PublishProviderOfferScreen(
                    onBackClick = {},
                    onPublish = { _, _, _, _, _, _, _, _, _, _, safe, myk, child, _, _ ->
                        submission = Triple(safe, myk, child)
                    }
                )
            }
        }

        compose.onNodeWithTag("provider_badge_disclosure").assertExists()
        compose.onNodeWithText("Adli Sicil / Sabıka Kaydı Temiz Beyanı").assertDoesNotExist()
        compose.onNodeWithText("MYK / Mesleki Ustalık Belgesi").assertDoesNotExist()
        compose.onNodeWithTag("toggle_provider_emergency").assertExists()

        compose.onNodeWithTag("btn_select_neighborhood").performScrollTo().performClick()
        compose.onNodeWithTag("neighborhood_pilot_tr_35_buca_efeler").performClick()
        compose.onNodeWithTag("btn_submit_provider_offer").performClick()
        compose.runOnIdle { assertEquals(Triple(false, false, false), submission) }
    }
}
