package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.CustomReport
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CustomReportCalculatorTest {
    @Test
    fun filteredReportIncludesMatchingSourceReversalInSelectedDateRange() {
        val zone = ZoneOffset.UTC
        val category = Category("food", "Food", CategoryType.EXPENSE)
        val original = transaction("original", LocalDate.of(2026, 10, 1), TransactionType.EXPENSE, 2_500, categoryId = "food", tagIds = setOf("personal"))
        val reversal = transaction("reversal", LocalDate.of(2026, 10, 5), TransactionType.REVERSAL, 2_500, categoryId = "food", reversesEntryId = original.journalEntryId)
        val otherAccount = transaction("other", LocalDate.of(2026, 10, 5), TransactionType.EXPENSE, 8_000, categoryId = "food", accountId = "other", tagIds = setOf("personal"))
        val report = CustomReport(
            id = "report",
            name = "Personal food",
            startDate = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(),
            accountId = "cash",
            categoryId = "food",
            tagId = "personal",
            createdAt = 1,
            updatedAt = 1
        )

        val result = CustomReportCalculator.calculate(report, listOf(original, reversal, otherAccount), listOf(category), zone)

        assertEquals(Money(-2_500), result.expenses)
        assertEquals(Money(2_500), result.net)
        assertEquals(Money(-2_500), result.categoryTotals.single().amount)
        assertEquals(LocalDate.of(2026, 10, 5), result.range?.start)
    }

    private fun transaction(
        id: String,
        date: LocalDate,
        type: TransactionType,
        amount: Long,
        categoryId: String?,
        accountId: String = "cash",
        tagIds: Set<String> = emptySet(),
        reversesEntryId: String? = null
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        transactionType = type,
        amount = Money(amount),
        accountId = accountId,
        categoryId = categoryId,
        tagIds = tagIds,
        reversesEntryId = reversesEntryId
    )
}
