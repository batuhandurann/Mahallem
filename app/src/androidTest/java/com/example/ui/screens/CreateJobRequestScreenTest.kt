package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import com.example.validation.RequestSchedules
import com.example.data.model.UrgencyMode
import org.junit.Assert.*
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

    private fun fill(tag: String, value: String) {
        composeRule.onNode(hasTestTag(tag)).performScrollTo().performTextReplacement(value)
    }

    @Test fun emergencySubmissionUsesCanonicalScheduleAndExplicitTitle() {
        var submitted: Triple<String, String, String>? = null
        composeRule.setContent {
            MyApplicationTheme {
                CreateJobRequestScreen(null, true, {},
                    { title, _, _, _, urgency, date, time, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        assertEquals(UrgencyMode.EMERGENCY, urgency)
                        submitted = Triple(title, date, time)
                    }
                )
            }
        }
        fill("input_job_title", "Acil su kaçağı")
        fill("input_address", "Bostanlı Mahallesi 123 Sokak")
        fill("input_name", "Batuhan Duran")
        fill("input_phone", "05321234567")
        composeRule.onNode(hasTestTag("btn_submit_job_request")).performClick()
        composeRule.runOnIdle {
            val payload = requireNotNull(submitted)
            assertEquals("Acil su kaçağı", payload.first)
            assertTrue(RequestSchedules.isValidDate(payload.second))
            assertTrue(RequestSchedules.isValidTime(payload.third))
        }
    }

    @Test fun plannedInvalidDateAndBlankTitleNeverSubmit() {
        var calls = 0
        composeRule.setContent {
            MyApplicationTheme {
                CreateJobRequestScreen(null, false, {},
                    { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> calls++ }
                )
            }
        }
        fill("input_address", "Bostanlı Mahallesi 123 Sokak")
        fill("input_name", "Batuhan Duran")
        fill("input_phone", "05321234567")
        composeRule.onNode(hasTestTag("btn_submit_job_request")).performClick()
        composeRule.onNode(hasTestTag("form_error")).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, calls) }
        fill("input_job_title", "Ev boya hizmeti")
        fill("input_date", "2026-02-31")
        fill("input_time", "25:70")
        composeRule.onNode(hasTestTag("btn_submit_job_request")).performClick()
        composeRule.onNode(hasTestTag("form_error")).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, calls) }
    }
}
