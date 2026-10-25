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
    SUBSCRIPTIONS("Recurring subscriptions", "Estimated monthly total"),
    RECENT_TRANSACTIONS("Recent transactions", "Latest posted activity")
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
        val storedOrder = preferences[ORDER_KEY]
        DashboardSettings(
            order = normalizeDashboardOrder(storedOrder),
            visible = decodeVisibleWidgets(
                preferences[VISIBLE_KEY],
                defaultRecentTransactionsVisible = !dashboardOrderIncludesRecentTransactions(storedOrder)
            )
        )
    }

    suspend fun setVisible(widget: DashboardWidget, isVisible: Boolean) {
        store.edit { preferences ->
            val storedOrder = preferences[ORDER_KEY]
            val current = decodeVisibleWidgets(
                preferences[VISIBLE_KEY],
                defaultRecentTransactionsVisible = !dashboardOrderIncludesRecentTransactions(storedOrder)
            ).toMutableSet()
            if (isVisible) current += widget else current -= widget
            preferences[ORDER_KEY] = normalizeDashboardOrder(storedOrder).joinToString(",") { it.name }
            preferences[VISIBLE_KEY] = current.sortedBy { it.ordinal }.joinToString(",") { it.name }
        }
    }

    suspend fun move(widget: DashboardWidget, offset: Int) {
        store.edit { preferences ->
            val storedOrder = preferences[ORDER_KEY]
            val order = normalizeDashboardOrder(storedOrder)
            val updated = moveDashboardWidget(order, widget, offset)
            val visible = decodeVisibleWidgets(
                preferences[VISIBLE_KEY],
                defaultRecentTransactionsVisible = !dashboardOrderIncludesRecentTransactions(storedOrder)
            )
            preferences[ORDER_KEY] = updated.joinToString(",") { it.name }
            preferences[VISIBLE_KEY] = visible.sortedBy { it.ordinal }.joinToString(",") { it.name }
        }
    }

    suspend fun restore(settings: DashboardSettings) {
        val order = normalizeDashboardOrder(settings.order.joinToString(",") { it.name })
        val visible = settings.visible.intersect(DashboardWidget.entries.toSet())
        store.edit { preferences ->
            preferences[ORDER_KEY] = order.joinToString(",") { it.name }
            preferences[VISIBLE_KEY] = visible.sortedBy { it.ordinal }.joinToString(",") { it.name }
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
    return decodeVisibleWidgets(encoded, defaultRecentTransactionsVisible = false)
}

internal fun decodeVisibleWidgets(
    encoded: String?,
    defaultRecentTransactionsVisible: Boolean
): Set<DashboardWidget> {
    if (encoded == null) return DashboardWidget.entries.toSet()
    val visible = encoded.split(',')
        .mapNotNull { name -> DashboardWidget.entries.firstOrNull { it.name == name } }
        .toSet()
        .toMutableSet()
    if (defaultRecentTransactionsVisible) visible += DashboardWidget.RECENT_TRANSACTIONS
    return visible
}

internal fun dashboardOrderIncludesRecentTransactions(encoded: String?): Boolean =
    DashboardWidget.RECENT_TRANSACTIONS.name in encoded.orEmpty().split(',')

/** Older backups contain the previous widget set; new activity starts visible for those backups. */
internal fun migrateLegacyDashboardWidgetLists(
    order: List<String>,
    visible: List<String>
): Pair<List<String>, List<String>> {
    val newWidget = DashboardWidget.RECENT_TRANSACTIONS.name
    val previousWidgets = DashboardWidget.entries.map { it.name }.filterNot { it == newWidget }.toSet()
    val isLegacyShape = order.size == previousWidgets.size && order.toSet() == previousWidgets &&
        visible.size == visible.toSet().size && visible.all { it in previousWidgets }
    return if (isLegacyShape) {
        (order + newWidget) to (visible + newWidget)
    } else {
        order to visible
    }
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
