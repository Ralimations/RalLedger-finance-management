package com.ralledger.ui.settings

import com.ralledger.domain.usecase.InsightsPeriodType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import com.ralledger.domain.model.Money

class FinancialPreferencesTest {
    @Test
    fun defaultInsightsPeriod_fallsBackToMonthForMissingOrInvalidValues() {
        assertEquals(InsightsPeriodType.MONTH, decodeDefaultInsightsPeriod(null))
        assertEquals(InsightsPeriodType.MONTH, decodeDefaultInsightsPeriod("NOT_A_PERIOD"))
        assertEquals(InsightsPeriodType.MONTH, decodeDefaultInsightsPeriod("CUSTOM"))
    }

    @Test
    fun defaultInsightsPeriod_restoresSupportedSavedRanges() {
        InsightsPeriodType.entries
            .filterNot { it == InsightsPeriodType.CUSTOM }
            .forEach { period -> assertEquals(period, decodeDefaultInsightsPeriod(period.name)) }
    }

    @Test
    fun displayPreferences_fallBackSafelyAndRestoreSavedValues() {
        assertEquals(DateDisplayFormat.MONTH_DAY_YEAR, decodeDateDisplayFormat(null))
        assertEquals(DateDisplayFormat.MONTH_DAY_YEAR, decodeDateDisplayFormat("UNKNOWN"))
        assertEquals(TransactionListDensity.COMFORTABLE, decodeTransactionListDensity(null))
        assertEquals(TransactionListDensity.COMFORTABLE, decodeTransactionListDensity("UNKNOWN"))
        assertEquals(CurrencyDisplayFormat.SYMBOL, decodeCurrencyDisplayFormat(null))
        assertEquals(CurrencyDisplayFormat.SYMBOL, decodeCurrencyDisplayFormat("UNKNOWN"))
        DateDisplayFormat.entries.forEach { assertEquals(it, decodeDateDisplayFormat(it.name)) }
        TransactionListDensity.entries.forEach { assertEquals(it, decodeTransactionListDensity(it.name)) }
        CurrencyDisplayFormat.entries.forEach { assertEquals(it, decodeCurrencyDisplayFormat(it.name)) }
    }

    @Test
    fun displayDate_usesSelectedFormatWithoutChangingTimestamp() {
        val previousLocale = Locale.getDefault()
        val previousTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val timestamp = 1_792_713_600_000L // 2026-10-23 00:00 UTC

            assertEquals("Oct 23, 2026", formatLedgerDate(timestamp, DateDisplayFormat.MONTH_DAY_YEAR))
            assertEquals("23 Oct 2026", formatLedgerDate(timestamp, DateDisplayFormat.DAY_MONTH_YEAR))
            assertEquals("2026-10-23", formatLedgerDate(timestamp, DateDisplayFormat.YEAR_MONTH_DAY))
            assertEquals("2026-10-23 00:00", formatLedgerDate(timestamp, DateDisplayFormat.YEAR_MONTH_DAY, includeTime = true))
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun displayMoney_supportsSymbolAndCurrencyCodeStyles() {
        val amount = Money(123_456)
        assertEquals(amount.format(), formatLedgerMoney(amount, "PHP", CurrencyDisplayFormat.SYMBOL))
        assertEquals("PHP 1,234.56", formatLedgerMoney(amount, "PHP", CurrencyDisplayFormat.CODE))
        assertEquals("USD 1,234.56", formatLedgerMoney(amount, "USD", CurrencyDisplayFormat.CODE))
    }
}
