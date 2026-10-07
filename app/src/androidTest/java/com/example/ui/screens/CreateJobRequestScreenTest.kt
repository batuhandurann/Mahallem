package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateJobRequestScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submitWithoutRequiredContactFieldsShowsValidationError() {
        composeRule.setContent {
            MyApplicationTheme {
                CreateJobRequestScreen(
                    preselectedCategoryId = null,
                    isEmergencyPreselected = false,
                    onBackClick = {},
                    onSubmitRequest = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.onNode(hasTestTag("btn_submit_job_request")).performClick()
        composeRule.onNode(hasTestTag("form_error")).assertIsDisplayed()
    }
}
