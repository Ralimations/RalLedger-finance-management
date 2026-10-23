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
    val defaultInsightsPeriod: InsightsPeriodType = InsightsPeriodType.MONTH
)

class FinancialPreferences(context: Context) {
    private val store = context.applicationContext.financialDataStore

    val settings: Flow<FinancialSettings> = store.data.map { preferences ->
        FinancialSettings(
            defaultTransactionAccountId = preferences[DEFAULT_ACCOUNT_KEY]?.takeIf(String::isNotBlank),
            defaultInsightsPeriod = decodeDefaultInsightsPeriod(preferences[DEFAULT_PERIOD_KEY])
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

    private companion object {
        val DEFAULT_ACCOUNT_KEY = stringPreferencesKey("default_transaction_account")
        val DEFAULT_PERIOD_KEY = stringPreferencesKey("default_insights_period")
    }
}

internal fun decodeDefaultInsightsPeriod(value: String?): InsightsPeriodType =
    value?.let { encoded -> InsightsPeriodType.entries.firstOrNull { it.name == encoded && it != InsightsPeriodType.CUSTOM } }
        ?: InsightsPeriodType.MONTH
