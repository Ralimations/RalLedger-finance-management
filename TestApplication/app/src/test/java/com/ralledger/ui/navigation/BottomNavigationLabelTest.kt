package com.ralledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavigationLabelTest {
    @Test
    fun usesCompactLabelOnNarrowScreenWithLargeText() {
        assertEquals("Acct", displayNavigationLabel("Accounts", "Acct", 320, 1.3f))
        assertEquals("Plan", displayNavigationLabel("Planning", "Plan", 320, 1.3f))
        assertEquals("Stats", displayNavigationLabel("Insights", "Stats", 320, 1.3f))
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

    @Test
    fun keepsStandardLabelsOnTypicalWidth() {
        assertEquals("Accounts", displayNavigationLabel("Accounts", "Acct", 411, 1.3f))
        assertEquals("Planning", displayNavigationLabel("Planning", "Plan", 411, 1.3f))
        assertEquals("Insights", displayNavigationLabel("Insights", "Stats", 411, 1.3f))
    }
}
