package com.ralledger.ui.widgets

import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Money
import com.ralledger.ui.settings.CurrencyDisplayFormat
import com.ralledger.ui.settings.FinancialSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDisplayTest {
    private val summary = DashboardSummary(availableToSpendFunds = Money(123_456))

    @Test
    fun balanceUsesSelectedCurrencyDisplay() {
        assertEquals(
            Money(123_456).format("PHP"),
            availableMoneyWidgetText(summary, FinancialSettings(), appLockEnabled = false)
        )
        assertEquals(
            "PHP 1,234.56",
            availableMoneyWidgetText(
                summary,
                FinancialSettings(currencyDisplayFormat = CurrencyDisplayFormat.CODE),
                appLockEnabled = false
            )
        )
    }

    @Test
    fun balanceIsNeverFormattedWhileLockedOrRecoveryFailed() {
        assertNull(availableMoneyWidgetText(summary, FinancialSettings(), appLockEnabled = true))
        assertNull(
            availableMoneyWidgetText(summary, FinancialSettings(), appLockEnabled = false, recoveryFailed = true)
        )
        assertNull(availableMoneyWidgetText(null, FinancialSettings(), appLockEnabled = false))
    }
}
