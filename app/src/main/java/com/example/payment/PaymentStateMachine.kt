package com.example.payment

enum class PaymentStatus { CREATED, PENDING, PAID, HELD, DISPUTED, RELEASED, REFUND_REQUESTED, REFUNDED, FAILED }

object PaymentStateMachine {
    fun canTransition(from: PaymentStatus, to: PaymentStatus): Boolean = when (from) {
        PaymentStatus.CREATED -> to in setOf(PaymentStatus.PENDING, PaymentStatus.FAILED)
        PaymentStatus.PENDING -> to in setOf(PaymentStatus.PAID, PaymentStatus.FAILED)
        PaymentStatus.PAID -> to in setOf(PaymentStatus.HELD, PaymentStatus.REFUND_REQUESTED)
        PaymentStatus.HELD -> to in setOf(PaymentStatus.RELEASED, PaymentStatus.DISPUTED, PaymentStatus.REFUND_REQUESTED)
        PaymentStatus.DISPUTED -> to in setOf(PaymentStatus.RELEASED, PaymentStatus.REFUND_REQUESTED)
        PaymentStatus.REFUND_REQUESTED -> to in setOf(PaymentStatus.REFUNDED, PaymentStatus.PAID)
        PaymentStatus.RELEASED, PaymentStatus.REFUNDED, PaymentStatus.FAILED -> false
    }
}