package com.ralledger.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPreferencesTest {
    @Test
    fun normalizeOrder_discardsUnknownAndDuplicateValuesAndKeepsEveryWidget() {
        val order = normalizeDashboardOrder("NET_WORTH,UNKNOWN,NET_WORTH,MONTHLY_FLOW")

        assertEquals(DashboardWidget.entries.size, order.size)
        assertEquals(DashboardWidget.NET_WORTH, order.first())
        assertEquals(DashboardWidget.MONTHLY_FLOW, order[1])
        assertEquals(DashboardWidget.entries.toSet(), order.toSet())
    }

    @Test
    fun visibility_defaultsToAllAndIgnoresUnknownValues() {
        assertEquals(DashboardWidget.entries.toSet(), decodeVisibleWidgets(null))
        val visible = decodeVisibleWidgets("AVAILABLE_MONEY,UNKNOWN,NET_WORTH,AVAILABLE_MONEY")
        assertEquals(setOf(DashboardWidget.AVAILABLE_MONEY, DashboardWidget.NET_WORTH), visible)
    }

    @Test
    fun moveWidget_swapsOrderAndLeavesBoundaryOrderStable() {
        val initial = DashboardWidget.entries
        val movedUp = moveDashboardWidget(initial, DashboardWidget.NET_WORTH, -1)
        val movedDown = moveDashboardWidget(initial, DashboardWidget.NET_WORTH, 1)
        val boundaryMove = moveDashboardWidget(initial, DashboardWidget.AVAILABLE_MONEY, -1)

        assertEquals(DashboardWidget.NET_WORTH, movedUp.first())
        assertEquals(DashboardWidget.AVAILABLE_MONEY, movedUp[1])
        assertEquals(DashboardWidget.NET_WORTH, movedDown[2])
        assertEquals(initial, boundaryMove)
        assertEquals(initial, moveDashboardWidget(initial, DashboardWidget.AVAILABLE_MONEY, 0))
    }

    @Test
    fun hiddenWidgetsStayInOrderAndCanBeShownAgain() {
        val settings = DashboardSettings(visible = setOf(DashboardWidget.NET_WORTH))

        assertEquals(listOf(DashboardWidget.NET_WORTH), settings.visibleInOrder)
        assertFalse(DashboardWidget.AVAILABLE_MONEY in settings.visible)
        assertTrue(DashboardWidget.NET_WORTH in settings.visible)
    }

    @Test
    fun oldDataStoreVisibilityAddsTheNewActivityWidgetByDefault() {
        val oldWidgets = DashboardWidget.entries.filterNot { it == DashboardWidget.RECENT_TRANSACTIONS }
        val oldOrder = oldWidgets.joinToString(",") { it.name }
        val oldVisibility = listOf(DashboardWidget.AVAILABLE_MONEY, DashboardWidget.MONTHLY_FLOW)
            .joinToString(",") { it.name }

        assertFalse(dashboardOrderIncludesRecentTransactions(oldOrder))
        assertTrue(
            DashboardWidget.RECENT_TRANSACTIONS in decodeVisibleWidgets(
                oldVisibility,
                defaultRecentTransactionsVisible = true
            )
        )
        assertFalse(
            DashboardWidget.RECENT_TRANSACTIONS in decodeVisibleWidgets(
                oldVisibility,
                defaultRecentTransactionsVisible = false
            )
        )
    }

    @Test
    fun legacyBackupWidgetSettingsAppendRecentWithoutReorderingExistingWidgets() {
        val oldOrder = DashboardWidget.entries.filterNot { it == DashboardWidget.RECENT_TRANSACTIONS }.map { it.name }
        val oldVisible = listOf(DashboardWidget.AVAILABLE_MONEY.name, DashboardWidget.MONTHLY_FLOW.name)

        val (order, visible) = migrateLegacyDashboardWidgetLists(oldOrder, oldVisible)

        assertEquals(oldOrder + DashboardWidget.RECENT_TRANSACTIONS.name, order)
        assertEquals(oldVisible + DashboardWidget.RECENT_TRANSACTIONS.name, visible)
    }
}
