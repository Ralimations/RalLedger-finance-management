package com.ralledger.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.usecase.InsightsPeriodType
import com.ralledger.ui.dashboard.DashboardPreferences
import com.ralledger.ui.dashboard.DashboardSettings
import com.ralledger.ui.dashboard.DashboardWidget
import com.ralledger.ui.dashboard.migrateLegacyDashboardWidgetLists
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
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.backupRestoreJournalDataStore by preferencesDataStore(name = "backup_restore_journal")

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

        fun fromJson(json: JSONObject): BackupUserPreferences {
            val (dashboardWidgetOrder, visibleDashboardWidgets) = migrateLegacyDashboardWidgetLists(
                order = json.getJSONArray("dashboardWidgetOrder").strings(),
                visible = json.getJSONArray("visibleDashboardWidgets").strings()
            )
            return BackupUserPreferences(
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
                dashboardWidgetOrder = dashboardWidgetOrder,
                visibleDashboardWidgets = visibleDashboardWidgets
            )
        }
    }
}

interface BackupPreferencesStore {
    suspend fun read(): BackupUserPreferences
    suspend fun write(preferences: BackupUserPreferences)
}

/** A durable cross-store intent used to reconcile settings after a process interruption. */
data class PendingBackupRestore(
    val id: String,
    val previousPreferences: BackupUserPreferences,
    val targetPreferences: BackupUserPreferences
)

interface BackupRestoreJournalStore {
    suspend fun read(): PendingBackupRestore?
    suspend fun write(journal: PendingBackupRestore)
    suspend fun clear()
}

class DataStoreBackupRestoreJournalStore(context: Context) : BackupRestoreJournalStore {
    private val store = context.applicationContext.backupRestoreJournalDataStore

    override suspend fun read(): PendingBackupRestore? = store.data.map { it[JOURNAL_KEY] }.first()?.let { value ->
        try {
            val json = JSONObject(value)
            PendingBackupRestore(
                id = json.getString("id").also { require(it.isNotBlank()) },
                previousPreferences = BackupUserPreferences.fromJson(json.getJSONObject("previousPreferences")),
                targetPreferences = BackupUserPreferences.fromJson(json.getJSONObject("targetPreferences"))
            )
        } catch (error: Exception) {
            throw IllegalStateException("Saved backup recovery data is invalid; restore was stopped to protect your data", error)
        }
    }

    override suspend fun write(journal: PendingBackupRestore) {
        require(journal.id.isNotBlank())
        val json = JSONObject().apply {
            put("id", journal.id)
            put("previousPreferences", journal.previousPreferences.toJson())
            put("targetPreferences", journal.targetPreferences.toJson())
        }
        store.edit { it[JOURNAL_KEY] = json.toString() }
    }

    override suspend fun clear() {
        store.edit { it.remove(JOURNAL_KEY) }
    }

    private companion object {
        val JOURNAL_KEY = stringPreferencesKey("pending_restore")
    }
}

/** Test helper for restore flows where process-death persistence is supplied separately. */
class InMemoryBackupRestoreJournalStore : BackupRestoreJournalStore {
    @Volatile
    private var journal: PendingBackupRestore? = null

    override suspend fun read(): PendingBackupRestore? = journal
    override suspend fun write(journal: PendingBackupRestore) { this.journal = journal }
    override suspend fun clear() { journal = null }
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
