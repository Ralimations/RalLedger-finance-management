package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
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
        startOfMonthTimestamp: Long,
        debts: List<Debt> = emptyList()
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
        val accountLiabilities = includedAccounts
            .filter { it.accountType.isLiability }
            .fold(Money.ZERO) { total, account -> total + account.currentLedgerBalance }
        val openDebts = debts.filter { !it.isArchived }
        val totalDebtLiabilities = openDebts.fold(Money.ZERO) { total, debt -> total + debt.outstandingAmount }
        val totalPayables = openDebts.filter { it.kind == DebtKind.BILL }
            .fold(Money.ZERO) { total, debt -> total + debt.outstandingAmount }
        val totalLiabilities = accountLiabilities + totalDebtLiabilities

        val transactionsById = transactions.associateBy { it.id }
        var monthlyIncome = Money.ZERO
        var monthlyExpenses = Money.ZERO
        var monthlyLoanProceeds = Money.ZERO
        transactions.asSequence()
            .filter { it.timestamp >= startOfMonthTimestamp }
            .forEach { transaction ->
                when (transaction.transactionType) {
                    TransactionType.INCOME -> monthlyIncome += transaction.amount
                    TransactionType.EXPENSE -> monthlyExpenses += transaction.amount
                    TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT -> monthlyExpenses += transaction.amount
                    TransactionType.LOAN_DISBURSEMENT -> monthlyLoanProceeds += transaction.amount
                    TransactionType.REVERSAL -> {
                        when (transactionsById[transaction.reversesEntryId]?.transactionType) {
                            TransactionType.INCOME -> monthlyIncome -= transaction.amount
                            TransactionType.EXPENSE -> monthlyExpenses -= transaction.amount
                            TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT -> monthlyExpenses -= transaction.amount
                            TransactionType.LOAN_DISBURSEMENT -> monthlyLoanProceeds -= transaction.amount
                            else -> Unit
                        }
                    }
                    else -> Unit
                }
            }

        val monthlySubscriptionCost = subscriptions
            .filter { it.status == SubscriptionStatus.ACTIVE && !it.isArchived }
            .fold(Money.ZERO) { total, subscription -> total + subscription.normalizedMonthlyCost() }

        return DashboardSummary(
            liquidCashAndBankBalances = liquidCash,
            availableToSpendFunds = liquidCash,
            reservedSavings = reservedSavings,
            accountsReceivableTotal = totalReceivables,
            accountsPayableTotal = accountLiabilities + totalPayables,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = totalAssets - totalLiabilities,
            monthlyIncomeReceived = monthlyIncome,
            monthlyExpensesPaid = monthlyExpenses,
            netCashFlow = monthlyIncome + monthlyLoanProceeds - monthlyExpenses,
            activeSubscriptionsMonthlyTotal = monthlySubscriptionCost
        )
    }
}
