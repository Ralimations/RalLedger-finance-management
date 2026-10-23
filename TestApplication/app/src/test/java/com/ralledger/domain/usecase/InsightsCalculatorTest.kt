package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class InsightsCalculatorTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun periodsCoverCalendarDayWeekMonthAndLeapYear() {
        val date = LocalDate.of(2024, 2, 29)
        assertEquals(InsightsDateRange(date, date), period(InsightsPeriodType.DAY, date).resolveRange(emptyList(), zone))
        assertEquals(
            InsightsDateRange(LocalDate.of(2024, 2, 26), LocalDate.of(2024, 3, 3)),
            period(InsightsPeriodType.WEEK, date).resolveRange(emptyList(), zone)
        )
        assertEquals(
            InsightsDateRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            period(InsightsPeriodType.MONTH, date).resolveRange(emptyList(), zone)
        )
        assertEquals(
            InsightsDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
            period(InsightsPeriodType.YEAR, date).resolveRange(emptyList(), zone)
        )
    }

    @Test
    fun originalEntryAndItsReversalAreAttributedToTheirOwnDates() {
        val original = transaction("income", LocalDate.of(2026, 4, 30), TransactionType.INCOME, 10_000)
        val reversal = transaction(
            id = "reversal", date = LocalDate.of(2026, 5, 2), type = TransactionType.REVERSAL,
            amount = 10_000, reverses = original.journalEntryId
        )

        val april = InsightsCalculator.calculate(listOf(original, reversal), emptyList(), period(InsightsPeriodType.MONTH, LocalDate.of(2026, 4, 1)), zone)
        val may = InsightsCalculator.calculate(listOf(original, reversal), emptyList(), period(InsightsPeriodType.MONTH, LocalDate.of(2026, 5, 1)), zone)

        assertEquals(Money(10_000), april.income)
        assertEquals(Money(-10_000), may.income)
        assertEquals(Money(-10_000), may.net)
    }

    @Test
    fun expenseReversalReducesExpensesAndCategoryOnReversalDate() {
        val category = Category("food", "Food", CategoryType.EXPENSE)
        val original = transaction("expense", LocalDate.of(2026, 4, 30), TransactionType.EXPENSE, 2_500, categoryId = "food")
        val reversal = transaction(
            id = "reversal", date = LocalDate.of(2026, 5, 2), type = TransactionType.REVERSAL,
            amount = 2_500, categoryId = "food", reverses = original.journalEntryId
        )

        val result = InsightsCalculator.calculate(
            listOf(original, reversal), listOf(category), period(InsightsPeriodType.MONTH, LocalDate.of(2026, 5, 1)), zone
        )

        assertEquals(Money(-2_500), result.expenses)
        assertEquals(Money(-2_500), result.categoryTotals.single().amount)
        assertEquals("Food", result.categoryTotals.single().name)
    }

    @Test
    fun persistedIncomeSourcesAndTransferFeesAreIncludedInTheirBuckets() {
        val source = Category("freelance", "Freelance", CategoryType.INCOME)
        val feeCategory = Category("fees", "Transfer fees", CategoryType.EXPENSE)
        val income = transaction("income", LocalDate.of(2026, 3, 4), TransactionType.INCOME, 50_000, categoryId = source.id)
        val transfer = transaction(
            id = "transfer", date = LocalDate.of(2026, 3, 5), type = TransactionType.TRANSFER,
            amount = 20_000, fee = 350, categoryId = feeCategory.id
        )

        val result = InsightsCalculator.calculate(
            listOf(income, transfer), listOf(source, feeCategory), period(InsightsPeriodType.MONTH, LocalDate.of(2026, 3, 1)), zone
        )

        assertEquals(Money(50_000), result.income)
        assertEquals(Money(350), result.expenses)
        assertEquals("Freelance", result.incomeSources.single().name)
        assertEquals(Money(50_000), result.incomeSources.single().amount)
        assertEquals(Money(50_000), result.buckets.first { it.start == LocalDate.of(2026, 3, 4) }.income)
        assertEquals(Money(350), result.buckets.first { it.start == LocalDate.of(2026, 3, 5) }.expenses)
    }

    @Test
    fun allTimeRangeUsesLocalDatesAndEmptyAllTimeHasNoRange() {
        val date = LocalDate.of(2026, 6, 7)
        val entry = transaction("entry", date, TransactionType.INCOME, 100)

        val result = InsightsCalculator.calculate(listOf(entry), emptyList(), period(InsightsPeriodType.ALL_TIME, date), zone)
        val empty = InsightsCalculator.calculate(emptyList(), emptyList(), period(InsightsPeriodType.ALL_TIME, date), zone)

        assertEquals(InsightsDateRange(date, date), result.range)
        assertEquals(1, result.buckets.size)
        assertNull(empty.range)
        assertTrue(empty.buckets.isEmpty())
    }

    @Test
    fun customRangeIncludesBothSelectedDatesAndMovesAsOneWindow() {
        val firstDay = LocalDate.of(2026, 7, 10)
        val lastDay = LocalDate.of(2026, 7, 12)
        val insideStart = transaction("start", firstDay, TransactionType.INCOME, 100)
        val insideEnd = transaction("end", lastDay, TransactionType.INCOME, 200)
        val outside = transaction("outside", lastDay.plusDays(1), TransactionType.INCOME, 900)
        val custom = InsightsPeriod(InsightsPeriodType.CUSTOM, firstDay, InsightsDateRange(firstDay, lastDay))

        val result = InsightsCalculator.calculate(listOf(insideStart, insideEnd, outside), emptyList(), custom, zone)
        val moved = custom.move(1)

        assertEquals(Money(300), result.income)
        assertEquals(3, result.buckets.size)
        assertEquals(InsightsDateRange(firstDay.plusDays(3), lastDay.plusDays(3)), moved.customRange)
    }

    @Test
    fun subscriptionPaymentsAndReceivableCollectionsAreReportedWithoutDoubleCountingIncome() {
        val date = LocalDate.of(2026, 8, 14)
        val subscriptionPayment = transaction("subscription", date, TransactionType.SUBSCRIPTION_PAYMENT, 1_299)
        val collection = transaction("collection", date, TransactionType.RECEIVABLE_COLLECTION, 5_000)
        val result = InsightsCalculator.calculate(
            listOf(subscriptionPayment, collection),
            emptyList(),
            period(InsightsPeriodType.DAY, date),
            zone
        )

        assertEquals(Money.ZERO, result.income)
        assertEquals(Money(1_299), result.expenses)
        assertEquals(Money(1_299), result.subscriptionPayments)
        assertEquals(Money(5_000), result.receivablesCollected)
    }

    private fun period(type: InsightsPeriodType, date: LocalDate) = InsightsPeriod(type, date)

    private fun transaction(
        id: String,
        date: LocalDate,
        type: TransactionType,
        amount: Long,
        fee: Long = 0,
        categoryId: String? = null,
        reverses: String? = null
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        transactionType = type,
        amount = Money(amount),
        fee = Money(fee),
        accountId = "cash",
        categoryId = categoryId,
        reversesEntryId = reverses
    )
}
