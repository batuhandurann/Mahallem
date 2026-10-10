package com.batuhanduran.burada.ui

import org.junit.Assert.*
import org.junit.Test

class ChatMessageAttemptsTest {
    private var generated = 0
    private fun tracker() = ChatMessageAttempts { "submission-${++generated}" }

    @Test fun failedOrUncertainRetryRetainsIdentityAndTrimsEquivalentText() {
        val attempts = tracker()
        val first = attempts.begin("conversation", " Merhaba ", false, "")
        assertEquals(first, attempts.begin("conversation", "Merhaba", false, ""))
        assertEquals(1, generated)
    }

    @Test fun acknowledgedMessageAllowsIntentionalIdenticalMessageWithFreshIdentity() {
        val attempts = tracker()
        val first = attempts.begin("conversation", "Merhaba", false, "")
        attempts.acknowledge(first)
        val next = attempts.begin("conversation", "Merhaba", false, "")
        assertNotEquals(first.submissionId, next.submissionId)
        attempts.acknowledge(first)
        assertEquals(next, attempts.begin("conversation", "Merhaba", false, ""))
    }

    @Test fun changedConversationTextOfferKindOrPriceHasIndependentIdentity() {
        val attempts = tracker()
        val submissions = listOf(
            attempts.begin("first", "Merhaba", false, ""),
            attempts.begin("second", "Merhaba", false, ""),
            attempts.begin("first", "Yeni mesaj", false, ""),
            attempts.begin("first", "Merhaba", true, ""),
            attempts.begin("first", "Merhaba", true, "1000")
        )
        assertEquals(submissions.size, submissions.map { it.submissionId }.toSet().size)
        assertEquals(submissions.first(), attempts.begin("first", "Merhaba", false, ""))
    }

    @Test fun sessionClearAndReplacementAccountCannotReuseOldSubmission() {
        val attempts = tracker()
        val first = attempts.begin("conversation", "Özel mesaj", false, "")
        val replacement = tracker().begin("conversation", "Özel mesaj", false, "")
        assertNotEquals(first.submissionId, replacement.submissionId)
        attempts.clear()
        assertNotEquals(first.submissionId,
            attempts.begin("conversation", "Özel mesaj", false, "").submissionId)
    }
}
