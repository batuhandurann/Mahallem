package com.example.ui.screens

import com.example.data.model.FeedFlowType
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedEmptyStatePolicyTest {
    private fun empty(flow: FeedFlowType, providers: Int, requests: Int): Boolean =
        when (flow) {
            FeedFlowType.ALL -> providers == 0 && requests == 0
            FeedFlowType.PROVIDER_OFFERS -> providers == 0
            FeedFlowType.SEEKER_REQUESTS -> requests == 0
        }

    @Test fun bothEmpty() = assertEquals(true, empty(FeedFlowType.ALL, 0, 0))
    @Test fun bothPresent() = assertEquals(false, empty(FeedFlowType.ALL, 1, 1))
    @Test fun providerFilterEmpty() = assertEquals(true, empty(FeedFlowType.PROVIDER_OFFERS, 0, 2))
    @Test fun providerFilterNonempty() = assertEquals(false, empty(FeedFlowType.PROVIDER_OFFERS, 2, 0))
    @Test fun requestFilterEmpty() = assertEquals(true, empty(FeedFlowType.SEEKER_REQUESTS, 2, 0))
    @Test fun requestFilterNonempty() = assertEquals(false, empty(FeedFlowType.SEEKER_REQUESTS, 0, 2))
}