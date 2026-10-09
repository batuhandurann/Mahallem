package com.batuhanduran.burada.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobReviewActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun acceptedJobCannotReview() {
        compose.setContent { BuradaTheme { JobReviewActions("r", "ACCEPTED", false, false, { _, _ -> }) } }
        compose.onNodeWithTag("review_job_r").assertDoesNotExist()
    }
    @Test fun noDefaultFiveStarsAndDraftRemainsUntilServerConfirmation() {
        var submitted = 0
        compose.setContent { BuradaTheme { JobReviewActions("r", "COMPLETED", false, false, { _, _ -> submitted++ }) } }
        compose.onNodeWithTag("review_job_r").performClick()
        compose.onNodeWithTag("submit_review").assertIsNotEnabled()
        compose.onNodeWithTag("review_star_2").performClick()
        compose.onNodeWithTag("review_comment").performTextInput("İşçilik geliştirilebilir")
        compose.onNodeWithTag("submit_review").performClick()
        compose.runOnIdle { assertEquals(1, submitted) }
        compose.onNodeWithTag("review_comment").assertTextContains("İşçilik geliştirilebilir")
    }
    @Test fun reviewedJobCannotSubmitAgain() {
        compose.setContent { BuradaTheme { JobReviewActions("r", "COMPLETED", true, false, { _, _ -> }) } }
        compose.onNodeWithTag("review_job_r").assertDoesNotExist()
        compose.onNodeWithTag("review_sent_r").assertIsDisplayed()
    }
    @Test fun cancelledJobExposesNoCompletionOrReviewControls() {
        compose.setContent { BuradaTheme { JobReviewActions("r", "CANCELLED", false, false, { _, _ -> }) } }
        compose.onNodeWithTag("complete_job_r").assertDoesNotExist()
        compose.onNodeWithTag("review_job_r").assertDoesNotExist()
    }
}
