package com.ralledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavigationLabelTest {
    @Test
    fun usesCompactLabelOnNarrowScreenWithLargeText() {
        assertEquals("Account", displayNavigationLabel("Accounts", "Account", 360, 1.3f))
    }

    @Test
    fun keepsFullLabelWhenWidthOrTextScaleDoesNotNeedCompaction() {
        assertEquals("Accounts", displayNavigationLabel("Accounts", "Account", 361, 1.3f))
        assertEquals("Accounts", displayNavigationLabel("Accounts", "Account", 360, 1.19f))
    }

    @Test
    fun preservesLabelsWithoutACompactAlternative() {
        assertEquals("Activity", displayNavigationLabel("Activity", "Activity", 360, 1.3f))
    }
}
