package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSummaryCalculatorTest {
    @Test
    fun archivedSubscriptionsAreExcludedFromMonthlyCommitments() {
        val active = Subscription(
            id = "active-sub",
            name = "Active",
            billingAmount = Money(1_000L),
            assignedAccountId = "wallet",
            nextBillingDate = 1L,
            billingFrequency = BillingFrequency.MONTHLY
        )
        val archived = active.copy(id = "archived-sub", isArchived = true)

        val summary = DashboardSummaryCalculator.calculate(
            accounts = emptyList(),
            receivables = emptyList(),
            subscriptions = listOf(active, archived),
            transactions = emptyList(),
            startOfMonthTimestamp = 0L
        )

        assertEquals(Money(1_000L), summary.activeSubscriptionsMonthlyTotal)
    }

    @Test
    fun totals_includeWalletBalancesAndReceivablesOnce_andExcludeHiddenAccounts() {
        val accounts = listOf(
            account("gcash", AccountType.E_WALLET, Money(100_000L)),
            account("test_wallet", AccountType.E_WALLET, Money(111_100L)),
            account("hidden", AccountType.TRADITIONAL_BANK, Money(900_000L), include = false),
            account("archived", AccountType.DIGITAL_BANK, Money(700_000L), archived = true)
        )
        val receivables = listOf(
            Receivable(
                id = "invoice_1",
                clientName = "Synthetic Client",
                originalAmount = Money(1_500_000L),
                outstandingAmount = Money(1_500_000L),
                dueDate = 10_000L,
                status = ReceivableStatus.CONFIRMED
            )
        )

        val summary = DashboardSummaryCalculator.calculate(
            accounts = accounts,
            receivables = receivables,
            subscriptions = emptyList(),
            transactions = emptyList(),
            startOfMonthTimestamp = 0L
        )

        assertEquals(Money(211_100L), summary.liquidCashAndBankBalances)
        assertEquals(Money(1_500_000L), summary.accountsReceivableTotal)
        assertEquals(Money(1_711_100L), summary.totalAssets)
        assertEquals(Money(1_711_100L), summary.netWorth)
    }

    @Test
    fun currentMonthIncomeAndExpenseTotals_applyReversalsOnTheirPostingDate() {
        val transactions = listOf(
            transaction("old_income", TransactionType.INCOME, 500L, timestamp = 500L),
            transaction("income", TransactionType.INCOME, 50_000L),
            transaction("income_reversal", TransactionType.REVERSAL, 30_000L, reverses = "income"),
            transaction("expense", TransactionType.EXPENSE, 15_000L),
            transaction("expense_reversal", TransactionType.REVERSAL, 5_000L, reverses = "expense"),
            transaction("receivable_writeoff", TransactionType.RECEIVABLE_WRITEOFF, 4_000L),
            transaction("writeoff_reversal", TransactionType.REVERSAL, 1_000L, reverses = "receivable_writeoff")
        )

        val summary = DashboardSummaryCalculator.calculate(
            accounts = emptyList(),
            receivables = emptyList(),
            subscriptions = emptyList(),
            transactions = transactions,
            startOfMonthTimestamp = 1_000L
        )

        assertEquals(Money(20_000L), summary.monthlyIncomeReceived)
        assertEquals(Money(10_000L), summary.monthlyExpensesPaid)
        assertEquals(Money(10_000L), summary.netCashFlow)
    }

    private fun account(
        id: String,
        type: AccountType,
        balance: Money,
        include: Boolean = true,
        archived: Boolean = false
    ) = Account(
        id = id,
        name = id,
        accountType = type,
        currentLedgerBalance = balance,
        includeInDashboardTotals = include,
        isArchived = archived
    )

    private fun transaction(
        id: String,
        type: TransactionType,
        amount: Long,
        timestamp: Long = 2_000L,
        reverses: String? = null
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = timestamp,
        transactionType = type,
        amount = Money(amount),
        accountId = "wallet",
        reversesEntryId = reverses
    )
}
