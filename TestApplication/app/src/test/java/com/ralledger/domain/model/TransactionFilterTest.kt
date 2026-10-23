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

    @Test
    fun interestCreditMatchesItsIncomeAndTaxExpenseCategories() {
        val interestCredit = Transaction(
            id = "interest",
            journalEntryId = "interest",
            transactionType = TransactionType.INTEREST_CREDIT,
            amount = Money(10_000L),
            fee = Money(2_000L),
            accountId = "savings",
            categoryId = "interest-income",
            categoryName = "Interest income",
            secondaryCategoryId = "withholding-tax",
            secondaryCategoryName = "Withholding tax"
        )

        assertTrue(TransactionFilter(type = TransactionTypeFilter.INCOME).matches(interestCredit))
        assertTrue(TransactionFilter(type = TransactionTypeFilter.EXPENSE).matches(interestCredit))
        assertTrue(TransactionFilter(categoryId = "withholding-tax").matches(interestCredit))
        assertTrue(TransactionFilter(query = "withholding tax").matches(interestCredit))
        assertFalse(TransactionFilter(categoryId = "other").matches(interestCredit))
    }

    @Test
    fun subscriptionPaymentMatchesExpenseAndSearchFilters() {
        val payment = Transaction(
            id = "subscription-payment",
            journalEntryId = "subscription-payment",
            timestamp = 200L,
            transactionType = TransactionType.SUBSCRIPTION_PAYMENT,
            amount = Money(1_299L),
            accountId = "wallet",
            accountName = "Main wallet",
            categoryId = "streaming",
            categoryName = "Subscriptions",
            description = "Subscription payment: Music service"
        )

        assertTrue(TransactionFilter(type = TransactionTypeFilter.EXPENSE).matches(payment))
        assertTrue(TransactionFilter(categoryId = "streaming").matches(payment))
        assertTrue(TransactionFilter(query = "music service").matches(payment))
        assertFalse(TransactionFilter(type = TransactionTypeFilter.OTHER).matches(payment))
    }

    @Test
    fun transactionLabelsParticipateInSearchAndLabelFilters() {
        val labeled = transfer.copy(tagIds = setOf("tag-home", "tag-work"), tagNames = listOf("Home", "Work"))

        assertTrue(TransactionFilter(tagId = "tag-home").matches(labeled))
        assertFalse(TransactionFilter(tagId = "tag-other").matches(labeled))
        assertTrue(TransactionFilter(query = "work").matches(labeled))
        assertFalse(TransactionFilter(query = "travel").matches(labeled))
    }
}
