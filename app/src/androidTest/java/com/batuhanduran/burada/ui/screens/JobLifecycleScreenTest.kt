package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.model.JobAction
import com.batuhanduran.burada.data.model.JobLifecycle
import com.batuhanduran.burada.ui.theme.BuradaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobLifecycleScreenTest {
    @get:Rule val compose = createComposeRule()
    private val request = JobRequestEntity(id = "job-test", title = "Musluk tamiri", sector = "HOME_REPAIR",
        categoryId = "plumbing", district = "Buca", urgencyMode = "PLANNED", eventOrJobDate = "2026-10-11",
        eventTime = "12:00", address = "", status = "AWAITING_CONFIRMATION", customerName = "Müşteri",
        customerPhone = "", ownerUid = "customer")

    @Test fun customerConfirmsThroughExplicitDialogAndCannotSubmitAsProvider() {
        var confirmed = 0
        compose.setContent { BuradaTheme {
            JobDetailScreen(request, JobLifecycle(request.id, "AWAITING_CONFIRMATION", 3), emptyList(), "customer",
                false, null, {}, {}, onAction = { action, version, note, reason ->
                    assertEquals(JobAction.CONFIRM_COMPLETION, action); assertEquals(3, version)
                    assertEquals("", note); assertEquals("", reason); confirmed++
                })
        } }
        compose.onNodeWithTag("job_action_SUBMIT_COMPLETION").assertDoesNotExist()
        compose.onNodeWithTag("job_action_CONFIRM_COMPLETION").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithTag("job_action_confirm").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }
    @Test fun cancellationNeedsReasonAndMeaningfulNote() {
        var confirmed = 0
        compose.setContent { BuradaTheme { JobActionDialog(JobAction.REQUEST_CANCEL, false, {}) { note, reason ->
            assertEquals("Randevu saatimiz uyuşmuyor", note); assertEquals("SCHEDULE", reason); confirmed++
        } } }
        compose.onNodeWithTag("job_action_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("job_reason_SCHEDULE").performScrollTo().performClick()
        compose.onNodeWithTag("job_action_note").performScrollTo().performTextInput("kısa")
        compose.onNodeWithTag("job_action_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("job_action_note").performTextReplacement("Randevu saatimiz uyuşmuyor")
        compose.onNodeWithTag("job_action_confirm").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }
    @Test fun cancellationRequesterHasOnlyWithdrawalAndBusyBlocksRepeat() {
        compose.setContent { BuradaTheme {
            JobDetailScreen(request.copy(status = "CANCELLATION_REQUESTED"),
                JobLifecycle(request.id, "CANCELLATION_REQUESTED", 4, cancellationByUid = "customer"),
                emptyList(), "customer", true, null, {}, {}, { _, _, _, _ -> error("Busy action executed") })
        } }
        compose.onNodeWithTag("job_action_ACCEPT_CANCEL").assertDoesNotExist()
        compose.onNodeWithTag("job_action_DECLINE_CANCEL").assertDoesNotExist()
        compose.onNodeWithTag("job_action_WITHDRAW_CANCEL").performScrollTo().assertIsNotEnabled()
    }
}
