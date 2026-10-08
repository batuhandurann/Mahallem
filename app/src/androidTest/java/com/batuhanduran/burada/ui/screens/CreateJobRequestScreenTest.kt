package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsDisplayed
import com.batuhanduran.burada.validation.RequestSchedules
import org.junit.Assert.*
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

    private fun chooseNeighborhood() {
        compose.onNodeWithTag("btn_select_neighborhood").performScrollTo().performClick()
        compose.onNodeWithTag("neighborhood_pilot_tr_35_buca_efeler").performClick()
    }

    private fun fill(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    @Test fun emergencyFormSubmitsCanonicalScheduleWithRequiredTitle() {
        var submitted: Triple<String, String, String>? = null
        compose.setContent {
            BuradaTheme {
                CreateJobRequestScreen(preselectedCategoryId = null, isEmergencyPreselected = true,
                    onBackClick = {},
                    onSubmitRequest = { title, _, _, _, _, date, time, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        submitted = Triple(title, date, time)
                    })
            }
        }
        chooseNeighborhood()
        compose.onNodeWithTag("btn_submit_job_request").performClick()
        compose.onNodeWithTag("form_error").assertIsDisplayed()
        compose.runOnIdle { assertNull(submitted) }
        fill("input_job_title", "Acil su kaçağı")
        compose.onNodeWithTag("btn_submit_job_request").performClick()
        compose.runOnIdle {
            val payload = requireNotNull(submitted)
            assertEquals("Acil su kaçağı", payload.first)
            RequestSchedules.requireValid(payload.first, payload.second, payload.third)
        }
    }

    @Test fun plannedFormRejectsImpossibleDateAndTimeBeforeSubmit() {
        var calls = 0
        compose.setContent {
            BuradaTheme {
                CreateJobRequestScreen(preselectedCategoryId = null, isEmergencyPreselected = false,
                    onBackClick = {},
                    onSubmitRequest = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> calls++ })
            }
        }
        chooseNeighborhood()
        fill("input_job_title", "Ev boya hizmeti")
        fill("input_date", "2026-02-31")
        fill("input_time", "25:70")
        compose.onNodeWithTag("btn_submit_job_request").performClick()
        compose.onNodeWithTag("form_error").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
        fill("input_date", "2028-02-29")
        // Valid date alone cannot mask an invalid time.
        compose.onNodeWithTag("btn_submit_job_request").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        fill("input_time", "23:59")
        compose.onNodeWithTag("btn_submit_job_request").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
