package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FinancialProjectPortfolioCalculatorTest {
    @Test
    fun reportsAggregateProjectKindsAndKeepUnassignedActivitySeparate() {
        val date = LocalDate.of(2026, 10, 20)
        val projects = listOf(
            FinancialProject("personal", "Home", FinancialProjectKind.PERSONAL, createdAt = 1, updatedAt = 1),
            FinancialProject("business", "Studio", FinancialProjectKind.BUSINESS, createdAt = 1, updatedAt = 1)
        )
        val transactions = listOf(
            transaction("personal-purchase", date, TransactionType.EXPENSE, 12_000, "personal"),
            transaction("personal-reversal", date.plusDays(1), TransactionType.REVERSAL, 12_000, null, "personal-purchase"),
            transaction("personal-remaining", date.plusDays(2), TransactionType.EXPENSE, 2_500, "personal"),
            transaction("business-income", date, TransactionType.INCOME, 40_000, "business"),
            transaction("business-expense", date.plusDays(1), TransactionType.EXPENSE, 8_000, "business"),
            transaction("unassigned-expense", date, TransactionType.EXPENSE, 1_500, null)
        )

        val result = FinancialProjectPortfolioCalculator.calculate(
            transactions = transactions,
            categories = listOf(
                Category("expense", "Equipment", CategoryType.EXPENSE),
                Category("income", "Studio sales", CategoryType.INCOME)
            ),
            projects = projects,
            period = InsightsPeriod(InsightsPeriodType.MONTH, date),
            zone = ZoneOffset.UTC
        )

        assertEquals(Money.ZERO, result.personal.income)
        assertEquals(Money(2_500), result.personal.expenses)
        assertEquals(Money(-2_500), result.personal.net)
        assertEquals(Money(40_000), result.business.income)
        assertEquals(Money(8_000), result.business.expenses)
        assertEquals(Money(32_000), result.business.net)
        assertEquals("Studio sales", result.business.incomeSources.single().name)
        assertEquals(Money(8_000), result.business.categoryTotals.single().amount)
        assertEquals(Money(1_500), result.unassigned.expenses)
        assertEquals(Money(-1_500), result.unassigned.net)
        assertEquals(Money(1_500), result.unassigned.categoryTotals.single().amount)
    }

    private fun transaction(
        id: String,
        date: LocalDate,
        type: TransactionType,
        amount: Long,
        projectId: String?,
        reversesEntryId: String? = null
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        transactionType = type,
        amount = Money(amount),
        accountId = "cash",
        categoryId = if (type == TransactionType.INCOME) "income" else "expense",
        projectId = projectId,
        reversesEntryId = reversesEntryId
    )
}
