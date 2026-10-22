package com.ralledger.domain.model

data class DashboardSummary(
    val liquidCashAndBankBalances: Money = Money.ZERO,
    val availableToSpendFunds: Money = Money.ZERO,
    val reservedSavings: Money = Money.ZERO,
    val accountsReceivableTotal: Money = Money.ZERO,
    val accountsPayableTotal: Money = Money.ZERO,
    val totalAssets: Money = Money.ZERO,
    val totalLiabilities: Money = Money.ZERO,
    val netWorth: Money = Money.ZERO,
    val monthlyIncomeReceived: Money = Money.ZERO,
    val monthlyExpensesPaid: Money = Money.ZERO,
    val netCashFlow: Money = Money.ZERO,
    val activeSubscriptionsMonthlyTotal: Money = Money.ZERO
)
