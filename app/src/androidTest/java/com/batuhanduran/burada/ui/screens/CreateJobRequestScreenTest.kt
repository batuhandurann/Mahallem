package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression: rendering the request form must not resolve a missing Compose FlowRow method. */
@RunWith(AndroidJUnit4::class)
class CreateJobRequestScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun requestFormRendersWithoutCrashing() {
        compose.setContent {
            BuradaTheme {
                CreateJobRequestScreen(
                    preselectedCategoryId = null,
                    isEmergencyPreselected = false,
                    onBackClick = {},
                    onSubmitRequest = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        compose.onNodeWithText("1. Hizmet Grubu Seçin").assertExists()
        compose.onNodeWithTag("btn_submit_job_request").assertExists()
    }
}
