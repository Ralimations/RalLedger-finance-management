package com.ralledger.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ralledger.domain.usecase.InsightsPeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.financialDataStore by preferencesDataStore(name = "financial_preferences")

data class FinancialSettings(
    val defaultTransactionAccountId: String? = null,
    val defaultInsightsPeriod: InsightsPeriodType = InsightsPeriodType.MONTH,
    val dateDisplayFormat: DateDisplayFormat = DateDisplayFormat.MONTH_DAY_YEAR,
    val transactionListDensity: TransactionListDensity = TransactionListDensity.COMFORTABLE,
    val currencyDisplayFormat: CurrencyDisplayFormat = CurrencyDisplayFormat.SYMBOL
)

class FinancialPreferences(context: Context) {
    private val store = context.applicationContext.financialDataStore

    val settings: Flow<FinancialSettings> = store.data.map { preferences ->
        FinancialSettings(
            defaultTransactionAccountId = preferences[DEFAULT_ACCOUNT_KEY]?.takeIf(String::isNotBlank),
            defaultInsightsPeriod = decodeDefaultInsightsPeriod(preferences[DEFAULT_PERIOD_KEY]),
            dateDisplayFormat = decodeDateDisplayFormat(preferences[DATE_FORMAT_KEY]),
            transactionListDensity = decodeTransactionListDensity(preferences[TRANSACTION_DENSITY_KEY]),
            currencyDisplayFormat = decodeCurrencyDisplayFormat(preferences[CURRENCY_FORMAT_KEY])
        )
    }

    suspend fun setDefaultTransactionAccount(accountId: String?) {
        store.edit { preferences ->
            if (accountId.isNullOrBlank()) preferences.remove(DEFAULT_ACCOUNT_KEY)
            else preferences[DEFAULT_ACCOUNT_KEY] = accountId
        }
    }

    suspend fun setDefaultInsightsPeriod(period: InsightsPeriodType) {
        require(period != InsightsPeriodType.CUSTOM) { "A custom date range cannot be the default reporting period" }
        store.edit { preferences -> preferences[DEFAULT_PERIOD_KEY] = period.name }
    }

    suspend fun setDateDisplayFormat(format: DateDisplayFormat) {
        store.edit { preferences -> preferences[DATE_FORMAT_KEY] = format.name }
    }

    suspend fun setTransactionListDensity(density: TransactionListDensity) {
        store.edit { preferences -> preferences[TRANSACTION_DENSITY_KEY] = density.name }
    }

    suspend fun setCurrencyDisplayFormat(format: CurrencyDisplayFormat) {
        store.edit { preferences -> preferences[CURRENCY_FORMAT_KEY] = format.name }
    }

    private companion object {
        val DEFAULT_ACCOUNT_KEY = stringPreferencesKey("default_transaction_account")
        val DEFAULT_PERIOD_KEY = stringPreferencesKey("default_insights_period")
        val DATE_FORMAT_KEY = stringPreferencesKey("date_display_format")
        val TRANSACTION_DENSITY_KEY = stringPreferencesKey("transaction_list_density")
        val CURRENCY_FORMAT_KEY = stringPreferencesKey("currency_display_format")
    }
}

internal fun decodeDefaultInsightsPeriod(value: String?): InsightsPeriodType =
    value?.let { encoded -> InsightsPeriodType.entries.firstOrNull { it.name == encoded && it != InsightsPeriodType.CUSTOM } }
        ?: InsightsPeriodType.MONTH

internal fun decodeDateDisplayFormat(value: String?): DateDisplayFormat =
    value?.let { encoded -> DateDisplayFormat.entries.firstOrNull { it.name == encoded } }
        ?: DateDisplayFormat.MONTH_DAY_YEAR

internal fun decodeTransactionListDensity(value: String?): TransactionListDensity =
    value?.let { encoded -> TransactionListDensity.entries.firstOrNull { it.name == encoded } }
        ?: TransactionListDensity.COMFORTABLE

internal fun decodeCurrencyDisplayFormat(value: String?): CurrencyDisplayFormat =
    value?.let { encoded -> CurrencyDisplayFormat.entries.firstOrNull { it.name == encoded } }
        ?: CurrencyDisplayFormat.SYMBOL
