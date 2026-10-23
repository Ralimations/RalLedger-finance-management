package com.ralledger.ui.dashboard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dashboardDataStore by preferencesDataStore(name = "dashboard_preferences")

enum class DashboardWidget(val title: String, val detail: String) {
    AVAILABLE_MONEY("Available Money", "Cash and bank balances after reserved savings"),
    NET_WORTH("Net Worth", "Assets and liabilities"),
    RESERVED_SAVINGS("Reserved savings", "Money set aside for goals"),
    MONEY_OWED("Money owed", "Receivables and payables"),
    MONTHLY_FLOW("This month", "Income, expenses and net flow"),
    SUBSCRIPTIONS("Recurring subscriptions", "Estimated monthly total")
}

data class DashboardSettings(
    val order: List<DashboardWidget> = DashboardWidget.entries,
    val visible: Set<DashboardWidget> = DashboardWidget.entries.toSet()
) {
    val visibleInOrder: List<DashboardWidget> get() = order.filter { it in visible }
}

class DashboardPreferences(context: Context) {
    private val store = context.applicationContext.dashboardDataStore

    val settings: Flow<DashboardSettings> = store.data.map { preferences ->
        DashboardSettings(
            order = normalizeDashboardOrder(preferences[ORDER_KEY]),
            visible = decodeVisibleWidgets(preferences[VISIBLE_KEY])
        )
    }

    suspend fun setVisible(widget: DashboardWidget, isVisible: Boolean) {
        store.edit { preferences ->
            val current = decodeVisibleWidgets(preferences[VISIBLE_KEY]).toMutableSet()
            if (isVisible) current += widget else current -= widget
            preferences[VISIBLE_KEY] = current.sortedBy { it.ordinal }.joinToString(",") { it.name }
        }
    }

    suspend fun move(widget: DashboardWidget, offset: Int) {
        store.edit { preferences ->
            val order = normalizeDashboardOrder(preferences[ORDER_KEY])
            val updated = moveDashboardWidget(order, widget, offset)
            if (updated != order) preferences[ORDER_KEY] = updated.joinToString(",") { it.name }
        }
    }

    private companion object {
        val ORDER_KEY = stringPreferencesKey("widget_order")
        val VISIBLE_KEY = stringPreferencesKey("visible_widgets")
    }
}

internal fun normalizeDashboardOrder(encoded: String?): List<DashboardWidget> {
    val parsed = encoded.orEmpty().split(',')
        .mapNotNull { name -> DashboardWidget.entries.firstOrNull { it.name == name } }
    return (parsed + DashboardWidget.entries).distinct()
}

internal fun decodeVisibleWidgets(encoded: String?): Set<DashboardWidget> {
    if (encoded == null) return DashboardWidget.entries.toSet()
    return encoded.split(',')
        .mapNotNull { name -> DashboardWidget.entries.firstOrNull { it.name == name } }
        .toSet()
}

internal fun moveDashboardWidget(order: List<DashboardWidget>, widget: DashboardWidget, offset: Int): List<DashboardWidget> {
    if (offset != -1 && offset != 1) return order
    val updated = normalizeDashboardOrder(order.joinToString(",") { it.name }).toMutableList()
    val index = updated.indexOf(widget)
    val target = index + offset
    if (index < 0 || target !in updated.indices) return updated
    val moving = updated[index]
    updated[index] = updated[target]
    updated[target] = moving
    return updated
}
