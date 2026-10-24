package com.ralledger.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetQuickAddIntentTest {
    @Test
    fun acceptsOnlySupportedTransactionTypesCaseInsensitively() {
        assertEquals("INCOME", normalizeWidgetTransactionType("income"))
        assertEquals("EXPENSE", normalizeWidgetTransactionType("Expense"))
        assertEquals("TRANSFER", normalizeWidgetTransactionType("TRANSFER"))
        assertNull(normalizeWidgetTransactionType(null))
        assertNull(normalizeWidgetTransactionType("DELETE"))
        assertNull(normalizeWidgetTransactionType(" EXPENSE "))
    }
}
