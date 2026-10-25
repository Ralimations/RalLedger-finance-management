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
}
