package com.batuhanduran.burada.data.remote

import org.junit.Assert.*
import org.junit.Test

class AtomicWriteBudgetTest {
    @Test fun hourlyCapsAndBoundariesApplyToEveryOperation() {
        WriteOperation.entries.forEach { operation ->
            assertEquals(BudgetIncrement(1, true), nextBudgetIncrement(operation, null, null, 5_000))
            assertEquals(BudgetIncrement(2, false), nextBudgetIncrement(operation, 1, 5_000, 5_001))
            try {
                nextBudgetIncrement(operation, operation.hourlyLimit, 5_000, 3_604_999)
                fail("${operation.id} exceeded its hourly allowance")
            } catch (exception: WriteQuotaExceededException) {
                assertTrue(exception.message!!.contains("saatlik sınır"))
            }
            assertEquals(BudgetIncrement(1, true), nextBudgetIncrement(operation, operation.hourlyLimit, 5_000, 3_605_000))
        }
        assertEquals(listOf(10L, 60L, 30L, 240L, 10L), WriteOperation.entries.map { it.hourlyLimit })
    }

    @Test fun backwardClockCannotResetAnExhaustedWindow() {
        try {
            nextBudgetIncrement(WriteOperation.REPORT, 10, 20_000, 1_000)
            fail("Clock skew granted another allowance")
        } catch (_: WriteQuotaExceededException) { }
        assertEquals(BudgetIncrement(2, false), nextBudgetIncrement(WriteOperation.REPORT, 1, 20_000, 1_000))
    }

    @Test fun CorruptOrPartialStoredBudgetFailsClosed() {
        listOf(0L to 0L, 11L to 0L, null to 0L, 1L to null).forEach { (count, window) ->
            try {
                nextBudgetIncrement(WriteOperation.REPORT, count, window, 5_000_000)
                fail("Invalid stored budget was accepted")
            } catch (exception: IllegalStateException) {
                assertFalse(exception is WriteQuotaExceededException)
            }
        }
    }
}
