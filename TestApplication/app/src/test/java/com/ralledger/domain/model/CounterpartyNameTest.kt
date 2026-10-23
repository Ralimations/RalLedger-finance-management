package com.ralledger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterpartyNameTest {
    @Test
    fun normalizationCollapsesWhitespaceAndIgnoresCase() {
        assertEquals("acme media", normalizeCounterpartyName("  Acme   Media  "))
        assertEquals("acme media", normalizeCounterpartyName("ACME MEDIA"))
        assertTrue(normalizeCounterpartyName("   ").isEmpty())
    }
}
