package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType

/** Single source of truth for dashboard balances and current-month cash-flow totals. */
object DashboardSummaryCalculator {
    fun calculate(
        accounts: List<Account>,
        receivables: List<Receivable>,
        subscriptions: List<Subscription>,
        transactions: List<Transaction>,
        startOfMonthTimestamp: Long
    ): DashboardSummary {
        val includedAccounts = accounts.filter { it.includeInDashboardTotals && !it.isArchived }
        val liquidCash = includedAccounts
            .filter { it.accountType.isLiquid }
            .fold(Money.ZERO) { total, account -> total + account.currentLedgerBalance }
        val reservedSavings = includedAccounts
            .filter { it.accountType == AccountType.PHYSICAL_SAVINGS }
            .fold(Money.ZERO) { total, account -> total + account.currentLedgerBalance }
        val totalReceivables = receivables
            .filter {
                it.status == ReceivableStatus.CONFIRMED ||
                    it.status == ReceivableStatus.PARTIALLY_PAID ||
                    it.status == ReceivableStatus.OVERDUE
            }
            .fold(Money.ZERO) { total, receivable -> total + receivable.outstandingAmount }
        val totalAssets = includedAccounts
            .filter { it.accountType.isAsset && !it.accountType.isReceivable }
            .fold(Money.ZERO) { total, account -> total + account.currentLedgerBalance } + totalReceivables
        val totalLiabilities = includedAccounts
            .filter { it.accountType.isLiability }
            .fold(Money.ZERO) { total, account -> total + account.currentLedgerBalance }

        val transactionsById = transactions.associateBy { it.id }
        var monthlyIncome = Money.ZERO
        var monthlyExpenses = Money.ZERO
        transactions.asSequence()
            .filter { it.timestamp >= startOfMonthTimestamp }
            .forEach { transaction ->
                when (transaction.transactionType) {
                    TransactionType.INCOME -> monthlyIncome += transaction.amount
                    TransactionType.EXPENSE -> monthlyExpenses += transaction.amount
                    TransactionType.REVERSAL -> {
                        when (transactionsById[transaction.reversesEntryId]?.transactionType) {
                            TransactionType.INCOME -> monthlyIncome -= transaction.amount
                            TransactionType.EXPENSE -> monthlyExpenses -= transaction.amount
                            else -> Unit
                        }
                    }
                    else -> Unit
                }
            }

        val monthlySubscriptionCost = subscriptions
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .fold(Money.ZERO) { total, subscription -> total + subscription.normalizedMonthlyCost() }

        return DashboardSummary(
            liquidCashAndBankBalances = liquidCash,
            availableToSpendFunds = liquidCash,
            reservedSavings = reservedSavings,
            accountsReceivableTotal = totalReceivables,
            accountsPayableTotal = totalLiabilities,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = totalAssets - totalLiabilities,
            monthlyIncomeReceived = monthlyIncome,
            monthlyExpensesPaid = monthlyExpenses,
            netCashFlow = monthlyIncome - monthlyExpenses,
            activeSubscriptionsMonthlyTotal = monthlySubscriptionCost
        )
    }
}
