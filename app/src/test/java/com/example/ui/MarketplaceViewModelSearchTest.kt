package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceViewModelSearchTest {
    @Test
    fun normalizeSearchQuery_trimsWhitespace() {
        assertEquals("boyacı usta", normalizeSearchQuery("  boyacı usta  "))
    }

    @Test
    fun normalizeSearchQuery_capsLength() {
        val normalized = normalizeSearchQuery("a".repeat(MAX_SEARCH_QUERY_LENGTH + 20))
        assertEquals(MAX_SEARCH_QUERY_LENGTH, normalized.length)
        assertTrue(normalized.all { it == 'a' })
    }
}
