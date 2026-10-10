package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.QuoteEntity
import org.junit.Assert.*
import org.junit.Test

class QuoteRetryPolicyTest {
    private val draft = QuoteEntity(requestId = "request", providerId = "provider", providerName = "Stale name",
        providerTitle = "Stale title", providerRating = 5.0, price = "1000 TL", durationOrArrival = "Yarın", notes = "Malzeme dahil")
    private val saved = draft.copy(providerUid = "bob", customerUid = "alice", providerName = "Current name",
        providerTitle = "Current title", providerRating = 4.0, createdAt = 1L)

    @Test fun authoritativePendingOrAcceptedQuoteAcknowledgesSamePayload() {
        assertTrue(acknowledgesQuoteRetry(saved, draft, "bob"))
        assertTrue(acknowledgesQuoteRetry(saved.copy(status = "ACCEPTED"), draft, "bob"))
    }
    @Test fun rejectedOrWithdrawnQuoteCannotPretendToBeFreshlySent() {
        listOf("REJECTED", "WITHDRAWN", "").forEach {
            assertFalse(acknowledgesQuoteRetry(saved.copy(status = it), draft, "bob"))
        }
    }
    @Test fun changedPayloadOrParticipantsCannotAcknowledgeTheDraft() {
        listOf(saved.copy(requestId = "other"), saved.copy(providerId = "other"), saved.copy(price = "1200 TL"),
            saved.copy(durationOrArrival = "Bugün"), saved.copy(notes = "Hariç"), saved.copy(providerUid = "eve"),
            saved.copy(customerUid = "bob"), saved.copy(customerUid = "")).forEach {
            assertFalse(acknowledgesQuoteRetry(it, draft, "bob"))
        }
        assertFalse(acknowledgesQuoteRetry(saved, draft, "eve"))
    }
}
