package com.ralledger.ui.settings

import com.ralledger.domain.usecase.InsightsPeriodType
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
