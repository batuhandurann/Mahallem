package com.batuhanduran.burada.ui.screens

import com.batuhanduran.burada.data.model.FeedFlowType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEmptyStatePolicyTest {
    @Test fun allIsEmptyOnlyWhenBothListsAreEmpty() {
        assertTrue(shouldShowFeedEmptyState(FeedFlowType.ALL, 0, 0))
        assertFalse(shouldShowFeedEmptyState(FeedFlowType.ALL, 0, 1))
        assertFalse(shouldShowFeedEmptyState(FeedFlowType.ALL, 1, 0))
        assertFalse(shouldShowFeedEmptyState(FeedFlowType.ALL, 1, 1))
    }

    @Test fun providerTabCanBeEmptyWhenRequestsExist() {
        assertTrue(shouldShowFeedEmptyState(FeedFlowType.PROVIDER_OFFERS, 0, 5))
    }

    @Test fun providerTabIsNotEmptyWhenAProviderExists() {
        assertFalse(shouldShowFeedEmptyState(FeedFlowType.PROVIDER_OFFERS, 1, 0))
    }

    @Test fun requestTabCanBeEmptyWhenProvidersExist() {
        assertTrue(shouldShowFeedEmptyState(FeedFlowType.SEEKER_REQUESTS, 8, 0))
    }

    @Test fun requestTabIsNotEmptyWhenARequestExists() {
        assertFalse(shouldShowFeedEmptyState(FeedFlowType.SEEKER_REQUESTS, 0, 1))
    }

    @Test fun emptySelectionsWithoutAnyListingsAreExplained() {
        assertTrue(shouldShowFeedEmptyState(FeedFlowType.PROVIDER_OFFERS, 0, 0))
        assertTrue(shouldShowFeedEmptyState(FeedFlowType.SEEKER_REQUESTS, 0, 0))
    }
}
