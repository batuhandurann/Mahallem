package com.batuhanduran.burada.ui.screens

import com.batuhanduran.burada.data.model.FeedFlowType

/**
 * Determine emptiness for the *selected* marketplace tab.
 * A request in another tab must not hide an empty provider-tab message, or vice versa.
 */
internal fun shouldShowFeedEmptyState(
    flow: FeedFlowType,
    providerCount: Int,
    requestCount: Int
): Boolean = when (flow) {
    FeedFlowType.ALL -> providerCount == 0 && requestCount == 0
    FeedFlowType.PROVIDER_OFFERS -> providerCount == 0
    FeedFlowType.SEEKER_REQUESTS -> requestCount == 0
}
