package com.ralledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAddDefaultsTest {
    @Test
    fun contextualAccountTakesPriorityForAccountActions() {
        assertEquals(
            "account-b",
            resolveQuickAddAccountId("account-b", "account-a", "account-c", listOf("account-a", "account-b", "account-c"))
        )
    }

    @Test
    fun invalidContextFallsBackToPreferredThenLastUsed() {
        assertEquals(
            "account-a",
            resolveQuickAddAccountId("archived", "account-a", "account-b", listOf("account-a", "account-b"))
        )
        assertEquals(
            "account-b",
            resolveQuickAddAccountId("archived", "also-archived", "account-b", listOf("account-a", "account-b"))
        )
    }

    @Test
    fun missingEligibleSuggestionsUseFirstAccountOrEmpty() {
        assertEquals("account-a", resolveQuickAddAccountId(null, null, null, listOf("account-a", "account-b")))
        assertEquals("", resolveQuickAddAccountId(null, null, null, emptyList()))
    }

    @Test
    fun identifiesWhenRecentAccountWasInferred() {
        assertEquals(
            QuickAddAccountDefaultSource.RECENT,
            resolveQuickAddAccountDefault(null, null, "account-b", listOf("account-a", "account-b")).source
        )
        assertEquals(
            QuickAddAccountDefaultSource.PREFERRED,
            resolveQuickAddAccountDefault(null, "account-a", "account-b", listOf("account-a", "account-b")).source
        )
        assertEquals(
            QuickAddAccountDefaultSource.CONTEXTUAL,
            resolveQuickAddAccountDefault("account-b", "account-a", "account-a", listOf("account-a", "account-b")).source
        )
        assertEquals(
            QuickAddAccountDefaultSource.FIRST_AVAILABLE,
            resolveQuickAddAccountDefault(null, null, null, listOf("account-a")).source
        )
        assertEquals(
            QuickAddAccountDefaultSource.NONE,
            resolveQuickAddAccountDefault(null, null, null, emptyList()).source
        )
    }

    @Test
    fun identifiesWhenRecentCategoryWasInferred() {
        assertEquals(
            QuickAddCategoryDefaultSource.RECENT,
            resolveQuickAddCategoryDefault("category-b", listOf("category-a", "category-b")).source
        )
        assertEquals(
            QuickAddCategoryDefaultSource.FIRST_AVAILABLE,
            resolveQuickAddCategoryDefault("archived-category", listOf("category-a")).source
        )
        assertEquals(
            QuickAddCategoryDefaultSource.NONE,
            resolveQuickAddCategoryDefault(null, emptyList()).source
        )
    }
}
