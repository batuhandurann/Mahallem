package com.batuhanduran.burada.moderation

import org.junit.Assert.*
import org.junit.Test

class ModerationPolicyTest {
    @Test fun eitherDirectionBlocksNewInteraction() {
        assertTrue(ModerationPolicy.canInteract("batuhan", "ayse", emptySet(), false))
        assertFalse(ModerationPolicy.canInteract("batuhan", "ayse", setOf("ayse"), false))
        assertFalse(ModerationPolicy.canInteract("batuhan", "ayse", emptySet(), true))
        assertFalse(ModerationPolicy.canInteract("batuhan", "batuhan", emptySet(), false))
        assertFalse(ModerationPolicy.canInteract("", "ayse", emptySet(), false))
    }

    @Test fun blocksBelongToTheirOwner() {
        assertFalse(ModerationPolicy.canInteract("batuhan", "ayse", setOf("ayse"), false))
        assertTrue(ModerationPolicy.canInteract("cem", "ayse", emptySet(), false))
    }

    @Test fun reportPreservesStructuredReasonAndTrimsDescription() {
        val report = ReportDraft(ReportTargetType.MESSAGE, "message-id", "ayse", ReportReason.HARASSMENT, "  tehdit  ")
            .validated("batuhan")
        assertEquals("harassment", report.reason.code)
        assertEquals("tehdit", report.details)
    }

    @Test fun oversizedReportRejectedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportDraft(ReportTargetType.USER, "ayse", "ayse", ReportReason.SPAM, "a".repeat(2001)).validated("batuhan")
        }
    }

    @Test fun rejectsSelfBlockSelfReportAndPathInjection() {
        assertThrows(IllegalArgumentException::class.java) { ModerationPolicy.validateBlock("batuhan", "batuhan") }
        assertThrows(IllegalArgumentException::class.java) { ModerationPolicy.validateBlock("batuhan", "users/ayse") }
        assertThrows(IllegalArgumentException::class.java) {
            ReportDraft(ReportTargetType.USER, "batuhan", "batuhan", ReportReason.OTHER).validated("batuhan")
        }
    }
}
