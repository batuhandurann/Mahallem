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
        val report = ReportDraft(ReportTargetType.MESSAGE, "message-id", "ayse", ReportReason.HARASSMENT, "  tehdit  ", "a:b:service")
            .validated("batuhan")
        assertEquals("harassment", report.reason.code)
        assertEquals("tehdit", report.details)
        assertEquals("conversations/a:b:service/messages/message-id", report.targetDocumentPath())
    }

    @Test fun targetPathsPreserveColonIdsAndRequireMatchingUserOrMessageParent() {
        assertEquals("providers/id:with:colons", ReportDraft(ReportTargetType.LISTING,
            "provider:id:with:colons", "ayse", ReportReason.SPAM).validated("batuhan").targetDocumentPath())
        assertEquals("requests/job", ReportDraft(ReportTargetType.LISTING,
            "request:job", "ayse", ReportReason.SPAM).validated("batuhan").targetDocumentPath())
        assertEquals("conversations/batuhan:ayse:title", ReportDraft(ReportTargetType.CONVERSATION,
            "batuhan:ayse:title", "ayse", ReportReason.SPAM).validated("batuhan").targetDocumentPath())
        listOf(
            ReportDraft(ReportTargetType.USER, "cem", "ayse", ReportReason.SPAM),
            ReportDraft(ReportTargetType.MESSAGE, "message", "ayse", ReportReason.SPAM),
            ReportDraft(ReportTargetType.MESSAGE, "message", "ayse", ReportReason.SPAM, conversationId="a/messages/b"),
            ReportDraft(ReportTargetType.LISTING, "provider:", "ayse", ReportReason.SPAM),
            ReportDraft(ReportTargetType.LISTING, "other:id", "ayse", ReportReason.SPAM),
            ReportDraft(ReportTargetType.CONVERSATION, "a:b", "ayse", ReportReason.SPAM, conversationId="a:b")
        ).forEach { draft -> assertThrows(IllegalArgumentException::class.java) { draft.validated("batuhan") } }
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
