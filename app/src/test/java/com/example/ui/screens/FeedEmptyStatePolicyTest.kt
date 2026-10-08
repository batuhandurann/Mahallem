package com.example.ui.screens

import com.example.data.model.FeedFlowType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the same production function that HomeScreen calls. */
class FeedEmptyStatePolicyTest {
    @Test fun bothEmpty() = assertTrue(isSelectedFeedEmpty(FeedFlowType.ALL, 0, 0))
    @Test fun allFeedWithProviders() = assertFalse(isSelectedFeedEmpty(FeedFlowType.ALL, 1, 0))
    @Test fun allFeedWithRequests() = assertFalse(isSelectedFeedEmpty(FeedFlowType.ALL, 0, 1))
    @Test fun providerFilterEmptyDespiteRequests() =
        assertTrue(isSelectedFeedEmpty(FeedFlowType.PROVIDER_OFFERS, 0, 2))
    @Test fun providerFilterNonempty() =
        assertFalse(isSelectedFeedEmpty(FeedFlowType.PROVIDER_OFFERS, 2, 0))
    @Test fun requestFilterEmptyDespiteProviders() =
        assertTrue(isSelectedFeedEmpty(FeedFlowType.SEEKER_REQUESTS, 2, 0))
    @Test fun requestFilterNonempty() =
        assertFalse(isSelectedFeedEmpty(FeedFlowType.SEEKER_REQUESTS, 0, 2))
}
