package com.example.payment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentStateMachineTest {
    @Test fun paidCanMoveToHeld() = assertTrue(PaymentStateMachine.canTransition(PaymentStatus.PAID, PaymentStatus.HELD))
    @Test fun heldCanMoveToReleased() = assertTrue(PaymentStateMachine.canTransition(PaymentStatus.HELD, PaymentStatus.RELEASED))
    @Test fun releasedCannotMoveBackToPaid() = assertFalse(PaymentStateMachine.canTransition(PaymentStatus.RELEASED, PaymentStatus.PAID))
    @Test fun failedCannotBecomeRefunded() = assertFalse(PaymentStateMachine.canTransition(PaymentStatus.FAILED, PaymentStatus.REFUNDED))
    @Test fun disputeCanBecomeRefundRequested() = assertTrue(PaymentStateMachine.canTransition(PaymentStatus.DISPUTED, PaymentStatus.REFUND_REQUESTED))
}
    @Test fun heldCanRequestRelease() = assertTrue(
        PaymentStateMachine.canTransition(PaymentStatus.HELD, PaymentStatus.RELEASE_REQUESTED)
    )

    @Test fun releaseRequestedCanBecomeReleased() = assertTrue(
        PaymentStateMachine.canTransition(PaymentStatus.RELEASE_REQUESTED, PaymentStatus.RELEASED)
    )

    @Test fun releaseRequestedCanBecomeRefundRequested() = assertTrue(
        PaymentStateMachine.canTransition(PaymentStatus.RELEASE_REQUESTED, PaymentStatus.REFUND_REQUESTED)
    )

    @Test fun releaseRequestedCannotBecomePaid() = assertFalse(
        PaymentStateMachine.canTransition(PaymentStatus.RELEASE_REQUESTED, PaymentStatus.PAID)
    )
