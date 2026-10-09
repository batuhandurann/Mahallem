package com.batuhanduran.burada.ui.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EstimatorCopyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun samplePricesAreLabeledClearly() {
        compose.setContent {
            BuradaTheme {
                CostEstimatorSheet(onDismiss = {}, onCreateRequestWithBudget = { _, _, _ -> })
            }
        }
        compose.onNodeWithText("Örnek Maliyet Hesaplayıcı").assertExists()
        compose.onNodeWithTag("estimator_illustrative_notice").assertExists()
        compose.onNodeWithText("Canlı Piyasa").assertDoesNotExist()
        compose.onNodeWithText("Premium / Garantili").assertDoesNotExist()
        compose.onNodeWithTag("btn_create_job_from_calculator").assertExists()
    }
}
