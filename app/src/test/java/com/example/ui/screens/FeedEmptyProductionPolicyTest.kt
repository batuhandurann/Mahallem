package com.example.ui.screens

import com.example.data.model.FeedFlowType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEmptyProductionPolicyTest {
    @Test fun allFeedEmpty() {
        assertTrue(isSelectedFeedEmpty(FeedFlowType.ALL, 0, 0))
    }
    @Test fun providerFilterEmptyDespiteRequests() {
        assertTrue(isSelectedFeedEmpty(FeedFlowType.PROVIDER_OFFERS, 0, 3))
    }
    @Test fun requestFilterEmptyDespiteProviders() {
        assertTrue(isSelectedFeedEmpty(FeedFlowType.SEEKER_REQUESTS, 3, 0))
    }
    @Test fun selectedFeedNonempty() {
        assertFalse(isSelectedFeedEmpty(FeedFlowType.PROVIDER_OFFERS, 1, 0))
    }
}
