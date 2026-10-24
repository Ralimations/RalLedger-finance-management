package com.ralledger.ui.widgets

import com.ralledger.domain.model.DashboardSummary
import com.ralledger.ui.settings.FinancialSettings
import com.ralledger.ui.settings.formatLedgerMoney

internal fun availableMoneyWidgetText(
    summary: DashboardSummary?,
    settings: FinancialSettings,
    appLockEnabled: Boolean,
    recoveryFailed: Boolean = false
): String? {
    if (appLockEnabled || recoveryFailed || summary == null) return null
    return formatLedgerMoney(summary.availableToSpendFunds, "PHP", settings.currencyDisplayFormat)
}
