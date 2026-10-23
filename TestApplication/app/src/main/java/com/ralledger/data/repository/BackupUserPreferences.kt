package com.ralledger.data.repository

import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.usecase.InsightsPeriodType
import com.ralledger.ui.dashboard.DashboardPreferences
import com.ralledger.ui.dashboard.DashboardSettings
import com.ralledger.ui.dashboard.DashboardWidget
import com.ralledger.ui.settings.CurrencyDisplayFormat
import com.ralledger.ui.settings.DateDisplayFormat
import com.ralledger.ui.settings.FinancialPreferences
import com.ralledger.ui.settings.FinancialSettings
import com.ralledger.ui.settings.TransactionListDensity
import com.ralledger.ui.theme.ThemeMode
import com.ralledger.ui.theme.ThemePalette
import com.ralledger.ui.theme.ThemePreferences
import com.ralledger.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** User-controlled presentation and entry defaults. Device authentication state is intentionally excluded. */
data class BackupUserPreferences(
    val defaultTransactionAccountId: String? = null,
    val defaultInsightsPeriod: String = InsightsPeriodType.MONTH.name,
    val dateDisplayFormat: String = DateDisplayFormat.MONTH_DAY_YEAR.name,
    val transactionListDensity: String = TransactionListDensity.COMFORTABLE.name,
    val currencyDisplayFormat: String = CurrencyDisplayFormat.SYMBOL.name,
    val subscriptionRemindersEnabled: Boolean = false,
    val subscriptionReminderDaysBefore: Int = 1,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val themePalette: String = ThemePalette.OCEAN.name,
    val dashboardWidgetOrder: List<String> = DashboardWidget.entries.map { it.name },
    val visibleDashboardWidgets: List<String> = DashboardWidget.entries.map { it.name }
) {
    fun toJson() = JSONObject().apply {
        put("defaultTransactionAccountId", defaultTransactionAccountId ?: JSONObject.NULL)
        put("defaultInsightsPeriod", defaultInsightsPeriod)
        put("dateDisplayFormat", dateDisplayFormat)
        put("transactionListDensity", transactionListDensity)
        put("currencyDisplayFormat", currencyDisplayFormat)
        put("subscriptionRemindersEnabled", subscriptionRemindersEnabled)
        put("subscriptionReminderDaysBefore", subscriptionReminderDaysBefore)
        put("themeMode", themeMode)
        put("themePalette", themePalette)
        put("dashboardWidgetOrder", JSONArray(dashboardWidgetOrder))
        put("visibleDashboardWidgets", JSONArray(visibleDashboardWidgets))
    }

    fun validationErrors(accounts: List<AccountEntity>): List<String> {
        val errors = mutableListOf<String>()
        if (enumValueOrNull<InsightsPeriodType>(defaultInsightsPeriod) == null ||
            defaultInsightsPeriod == InsightsPeriodType.CUSTOM.name
        ) errors += "Default reporting period is invalid"
        if (enumValueOrNull<DateDisplayFormat>(dateDisplayFormat) == null) errors += "Date display setting is invalid"
        if (enumValueOrNull<TransactionListDensity>(transactionListDensity) == null) errors += "Transaction density setting is invalid"
        if (enumValueOrNull<CurrencyDisplayFormat>(currencyDisplayFormat) == null) errors += "Currency display setting is invalid"
        if (subscriptionReminderDaysBefore !in REMINDER_DAYS) errors += "Subscription reminder timing is invalid"
        if (enumValueOrNull<ThemeMode>(themeMode) == null || enumValueOrNull<ThemePalette>(themePalette) == null) {
            errors += "Appearance settings are invalid"
        }

        val widgetNames = DashboardWidget.entries.map { it.name }.toSet()
        if (dashboardWidgetOrder.size != widgetNames.size || dashboardWidgetOrder.toSet() != widgetNames) {
            errors += "Dashboard order is invalid"
        }
        if (visibleDashboardWidgets.size != visibleDashboardWidgets.toSet().size ||
            visibleDashboardWidgets.any { it !in widgetNames }
        ) errors += "Visible dashboard widgets are invalid"

        val defaultAccountId = defaultTransactionAccountId
        if (defaultAccountId != null && accounts.none { account ->
                account.id == defaultAccountId && !account.isArchived &&
                    runCatching { AccountType.valueOf(account.accountType).isAsset }.getOrDefault(false)
            }
        ) errors += "Default transaction account is missing, archived, or not an asset account"
        return errors
    }

    fun effectiveFor(accounts: List<AccountEntity>): BackupUserPreferences = copy(
        defaultTransactionAccountId = defaultTransactionAccountId?.takeIf { id ->
            accounts.any { account ->
                account.id == id && !account.isArchived &&
                    runCatching { AccountType.valueOf(account.accountType).isAsset }.getOrDefault(false)
            }
        }
    )

    fun toFinancialSettings() = FinancialSettings(
        defaultTransactionAccountId = defaultTransactionAccountId,
        defaultInsightsPeriod = InsightsPeriodType.valueOf(defaultInsightsPeriod),
        dateDisplayFormat = DateDisplayFormat.valueOf(dateDisplayFormat),
        transactionListDensity = TransactionListDensity.valueOf(transactionListDensity),
        currencyDisplayFormat = CurrencyDisplayFormat.valueOf(currencyDisplayFormat),
        subscriptionRemindersEnabled = subscriptionRemindersEnabled,
        subscriptionReminderDaysBefore = subscriptionReminderDaysBefore
    )

    fun toThemeSettings() = ThemeSettings(
        mode = ThemeMode.valueOf(themeMode),
        palette = ThemePalette.valueOf(themePalette)
    )

    fun toDashboardSettings() = DashboardSettings(
        order = dashboardWidgetOrder.map(DashboardWidget::valueOf),
        visible = visibleDashboardWidgets.map(DashboardWidget::valueOf).toSet()
    )

    companion object {
        private val REMINDER_DAYS = setOf(0, 1, 3)

        fun fromJson(json: JSONObject) = BackupUserPreferences(
            defaultTransactionAccountId = if (json.isNull("defaultTransactionAccountId")) null
                else json.getString("defaultTransactionAccountId"),
            defaultInsightsPeriod = json.getString("defaultInsightsPeriod"),
            dateDisplayFormat = json.getString("dateDisplayFormat"),
            transactionListDensity = json.getString("transactionListDensity"),
            currencyDisplayFormat = json.getString("currencyDisplayFormat"),
            subscriptionRemindersEnabled = json.getBoolean("subscriptionRemindersEnabled"),
            subscriptionReminderDaysBefore = json.getInt("subscriptionReminderDaysBefore"),
            themeMode = json.getString("themeMode"),
            themePalette = json.getString("themePalette"),
            dashboardWidgetOrder = json.getJSONArray("dashboardWidgetOrder").strings(),
            visibleDashboardWidgets = json.getJSONArray("visibleDashboardWidgets").strings()
        )
    }
}

interface BackupPreferencesStore {
    suspend fun read(): BackupUserPreferences
    suspend fun write(preferences: BackupUserPreferences)
}

class DataStoreBackupPreferencesStore(
    private val financial: FinancialPreferences,
    private val theme: ThemePreferences,
    private val dashboard: DashboardPreferences
) : BackupPreferencesStore {
    override suspend fun read(): BackupUserPreferences = combine(
        financial.settings,
        theme.settings,
        dashboard.settings
    ) { financialSettings, themeSettings, dashboardSettings ->
        BackupUserPreferences(
            defaultTransactionAccountId = financialSettings.defaultTransactionAccountId,
            defaultInsightsPeriod = financialSettings.defaultInsightsPeriod.name,
            dateDisplayFormat = financialSettings.dateDisplayFormat.name,
            transactionListDensity = financialSettings.transactionListDensity.name,
            currencyDisplayFormat = financialSettings.currencyDisplayFormat.name,
            subscriptionRemindersEnabled = financialSettings.subscriptionRemindersEnabled,
            subscriptionReminderDaysBefore = financialSettings.subscriptionReminderDaysBefore,
            themeMode = themeSettings.mode.name,
            themePalette = themeSettings.palette.name,
            dashboardWidgetOrder = dashboardSettings.order.map { it.name },
            visibleDashboardWidgets = dashboardSettings.visible.sortedBy { it.ordinal }.map { it.name }
        )
    }.first()

    override suspend fun write(preferences: BackupUserPreferences) {
        theme.restore(preferences.toThemeSettings())
        dashboard.restore(preferences.toDashboardSettings())
        financial.restore(preferences.toFinancialSettings())
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
