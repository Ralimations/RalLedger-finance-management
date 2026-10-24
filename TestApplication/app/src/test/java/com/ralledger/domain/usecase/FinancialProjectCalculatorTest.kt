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

class FinancialProjectCalculatorTest {
    @Test
    fun totalsIncludeFullReversalsAndExcludeOtherProjects() {
        val date = LocalDate.of(2026, 10, 20)
        val project = FinancialProject("business", "Studio", FinancialProjectKind.BUSINESS, createdAt = 1, updatedAt = 1)
        val category = Category("expense", "Equipment", CategoryType.EXPENSE)
        val selected = transaction("selected", date, TransactionType.EXPENSE, 12_500, "business", reversesEntryId = null)
        val reversal = transaction("reversal", date.plusDays(2), TransactionType.REVERSAL, 12_500, "business", reversesEntryId = selected.journalEntryId)
        val remaining = transaction("remaining", date.plusDays(1), TransactionType.EXPENSE, 2_000, "business", reversesEntryId = null)
        val other = transaction("other", date, TransactionType.EXPENSE, 30_000, "personal", reversesEntryId = null)

        val result = FinancialProjectCalculator.calculate(
            project,
            listOf(selected, reversal, remaining, other),
            listOf(category),
            ZoneOffset.UTC
        )

        assertEquals(Money(2_000), result.expenses)
        assertEquals(Money(-2_000), result.net)
        assertEquals(listOf(Money(2_000)), result.categoryTotals.map { it.amount })
    }

    private fun transaction(
        id: String,
        date: LocalDate,
        type: TransactionType,
        amount: Long,
        projectId: String,
        reversesEntryId: String?
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        transactionType = type,
        amount = Money(amount),
        accountId = "cash",
        categoryId = "expense",
        projectId = projectId,
        reversesEntryId = reversesEntryId
    )
}
