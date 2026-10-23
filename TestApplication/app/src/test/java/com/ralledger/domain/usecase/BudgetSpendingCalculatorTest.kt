package com.ralledger.domain.usecase

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.util.Calendar

class BudgetSpendingCalculatorTest {
    @Test
    fun aggregateCountsExpensesAndTransferFeesButIgnoresReversedAndOtherActivity() {
        val period = YearMonth.now()
        val currentMonth = timestamp(period.year, period.monthValue, day = 12)
        val previousPeriod = period.minusMonths(1)
        val previousMonth = timestamp(previousPeriod.year, previousPeriod.monthValue, day = 12)
        val transactions = listOf(
            transaction("expense", TransactionType.EXPENSE, 6_000L, currentMonth, "food"),
            transaction("receivable-writeoff", TransactionType.RECEIVABLE_WRITEOFF, 1_200L, currentMonth, "food"),
            transaction("reversed-writeoff", TransactionType.RECEIVABLE_WRITEOFF, 2_000L, currentMonth, "food", reversed = true),
            transaction("reversed-expense", TransactionType.EXPENSE, 9_000L, currentMonth, "food", reversed = true),
            transaction("reversal", TransactionType.REVERSAL, 9_000L, currentMonth, "food"),
            transaction("transfer-fee", TransactionType.TRANSFER, 20_000L, currentMonth, "food", fee = 500L),
            transaction("no-fee", TransactionType.TRANSFER, 80_000L, currentMonth, "food"),
            transaction("other-category", TransactionType.EXPENSE, 7_000L, currentMonth, "home"),
            transaction("previous-month", TransactionType.EXPENSE, 4_000L, previousMonth, "food")
        )

        val totals = BudgetSpendingCalculator.aggregate(transactions)

        assertEquals(Money(7_700L), totals[BudgetSpendingKey("food", period.year, period.monthValue)])
        assertEquals(Money(7_000L), totals[BudgetSpendingKey("home", period.year, period.monthValue)])
        assertEquals(Money(4_000L), totals[BudgetSpendingKey("food", previousPeriod.year, previousPeriod.monthValue)])
    }

    private fun transaction(
        id: String,
        type: TransactionType,
        amount: Long,
        timestamp: Long,
        categoryId: String,
        fee: Long = 0L,
        reversed: Boolean = false
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = timestamp,
        transactionType = type,
        amount = Money(amount),
        fee = Money(fee),
        accountId = "wallet",
        categoryId = categoryId,
        isReversed = reversed
    )

    private fun timestamp(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
