package com.example.ui.screens

import com.example.data.model.FeedFlowType

internal fun isSelectedFeedEmpty(flow: FeedFlowType, providerCount: Int, requestCount: Int): Boolean =
    when (flow) {
        FeedFlowType.ALL -> providerCount == 0 && requestCount == 0
        FeedFlowType.PROVIDER_OFFERS -> providerCount == 0
        FeedFlowType.SEEKER_REQUESTS -> requestCount == 0
    }
