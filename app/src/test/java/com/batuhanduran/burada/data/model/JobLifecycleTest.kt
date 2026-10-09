package com.batuhanduran.burada.data.model

import org.junit.Assert.*
import org.junit.Test

class JobLifecycleTest {
    @Test fun completionControlsRespectRoles() {
        assertFalse(JobAction.SUBMIT_COMPLETION in availableJobActions("ACCEPTED", true, false))
        assertTrue(JobAction.SUBMIT_COMPLETION in availableJobActions("IN_PROGRESS", false, false))
        assertTrue(JobAction.CONFIRM_COMPLETION in availableJobActions("AWAITING_CONFIRMATION", true, false))
        assertFalse(JobAction.CONFIRM_COMPLETION in availableJobActions("AWAITING_CONFIRMATION", false, false))
        assertTrue(JobAction.REQUEST_REVISION in availableJobActions("AWAITING_CONFIRMATION", true, false))
    }
    @Test fun requesterCannotAcceptTheirOwnCancellation() {
        assertEquals(listOf(JobAction.WITHDRAW_CANCEL), availableJobActions("CANCELLATION_REQUESTED", true, true))
        assertEquals(listOf(JobAction.ACCEPT_CANCEL, JobAction.DECLINE_CANCEL), availableJobActions("CANCELLATION_REQUESTED", false, false))
    }
    @Test fun terminalAndUnknownStatesNeverOfferMutation() {
        for (status in listOf("COMPLETED", "CANCELLED", "unknown", ""))
            for (customer in listOf(true, false)) assertTrue(availableJobActions(status, customer, false).isEmpty())
    }
    @Test fun openCancellationIsCustomerOnlyAndReasonsRequireExplanation() {
        assertEquals(listOf(JobAction.CANCEL_OPEN), availableJobActions("PENDING", true, false))
        assertTrue(availableJobActions("PENDING", false, false).isEmpty())
        for (action in listOf(JobAction.CANCEL_OPEN, JobAction.REQUEST_CANCEL)) {
            assertTrue(action.needsNote); assertTrue(action.needsReason)
        }
    }
}
