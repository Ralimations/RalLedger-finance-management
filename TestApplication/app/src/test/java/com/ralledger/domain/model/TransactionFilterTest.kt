package com.ralledger.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFilterTest {
    private val transfer = Transaction(
        id = "entry-transfer",
        journalEntryId = "entry-transfer",
        timestamp = 100L,
        transactionType = TransactionType.TRANSFER,
        amount = Money(5_000L),
        accountId = "wallet-a",
        accountName = "Main wallet",
        destinationAccountId = "bank-b",
        destinationAccountName = "Savings bank",
        categoryId = "expense-fee",
        categoryName = "Bank fees",
        description = "Monthly transfer",
        counterparty = "My savings"
    )

    @Test
    fun searchMatchesDescriptionCounterpartyAndDestinationAccount() {
        assertTrue(TransactionFilter(query = "MONTHLY").matches(transfer))
        assertTrue(TransactionFilter(query = "my savings").matches(transfer))
        assertTrue(TransactionFilter(query = "savings bank").matches(transfer))
        assertFalse(TransactionFilter(query = "unrelated").matches(transfer))
    }

    @Test
    fun filtersMatchEitherTransferAccountAndCategory() {
        assertTrue(TransactionFilter(accountId = "wallet-a").matches(transfer))
        assertTrue(TransactionFilter(accountId = "bank-b").matches(transfer))
        assertFalse(TransactionFilter(accountId = "other-account").matches(transfer))
        assertTrue(TransactionFilter(categoryId = "expense-fee").matches(transfer))
        assertFalse(TransactionFilter(categoryId = "other-category").matches(transfer))
    }

    @Test
    fun dateRangeIncludesStartAndExcludesNextDayBoundary() {
        assertTrue(TransactionFilter(startInclusive = 100L, endExclusive = 101L).matches(transfer))
        assertFalse(TransactionFilter(startInclusive = 101L).matches(transfer))
        assertFalse(TransactionFilter(endExclusive = 100L).matches(transfer))
    }

    @Test
    fun transactionKindFiltersIncludeRelatedTransactionKindsInOther() {
        assertTrue(TransactionFilter(type = TransactionTypeFilter.TRANSFER).matches(transfer))
        assertFalse(TransactionFilter(type = TransactionTypeFilter.EXPENSE).matches(transfer))
        val reversal = transfer.copy(transactionType = TransactionType.REVERSAL)
        assertTrue(TransactionFilter(type = TransactionTypeFilter.OTHER).matches(reversal))
    }

    @Test
    fun expenseFilterIncludesReceivableWriteOffs() {
        val writeOff = Transaction(
            id = "writeoff",
            journalEntryId = "writeoff",
            transactionType = TransactionType.RECEIVABLE_WRITEOFF,
            amount = Money(500L),
            accountId = "receivable",
            categoryId = "bad-debt"
        )

        assertTrue(TransactionFilter(type = TransactionTypeFilter.EXPENSE).matches(writeOff))
        assertFalse(TransactionFilter(type = TransactionTypeFilter.OTHER).matches(writeOff))
    }
}
