package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.QuoteEntity

/** Only an authoritative immutable quote can acknowledge an uncertain earlier send. */
internal fun acknowledgesQuoteRetry(saved: QuoteEntity, draft: QuoteEntity, uid: String): Boolean =
    saved.providerUid == uid && saved.customerUid.isNotBlank() && saved.customerUid != uid &&
        saved.status in setOf("PENDING", "ACCEPTED") &&
        saved.requestId == draft.requestId && saved.providerId == draft.providerId &&
        saved.price == draft.price && saved.durationOrArrival == draft.durationOrArrival &&
        saved.notes == draft.notes
