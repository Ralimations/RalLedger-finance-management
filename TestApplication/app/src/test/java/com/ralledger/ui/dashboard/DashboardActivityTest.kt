package com.ralledger.ui.dashboard

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardActivityTest {
    @Test
    fun recentActivityUsesLatestActiveRowsAndRespectsLimit() {
        val transactions = listOf(
            transaction("older", TransactionType.EXPENSE, timestamp = 10),
            transaction("latest", TransactionType.INCOME, timestamp = 30),
            transaction("middle", TransactionType.TRANSFER, timestamp = 20)
        )

        assertEquals(listOf("latest", "middle"), recentDashboardTransactions(transactions, limit = 2).map { it.id })
    }

    @Test
    fun recentActivityOmitsReversalsAndOpeningAdjustments() {
        val transactions = listOf(
            transaction("reversed-original", TransactionType.EXPENSE, timestamp = 40, isReversed = true),
            transaction("reversal", TransactionType.REVERSAL, timestamp = 35, reversesEntryId = "original"),
            transaction("opening", TransactionType.OPENING_BALANCE, timestamp = 30),
            transaction("adjustment", TransactionType.BALANCE_ADJUSTMENT, timestamp = 25),
            transaction("purchase", TransactionType.EXPENSE, timestamp = 20)
        )

        assertEquals(listOf("purchase"), recentDashboardTransactions(transactions).map { it.id })
    }

    @Test
    fun recentActivityReturnsEmptyForNonPositiveLimit() {
        assertEquals(emptyList<Transaction>(), recentDashboardTransactions(listOf(transaction("one", TransactionType.INCOME)), 0))
    }

    private fun transaction(
        id: String,
        type: TransactionType,
        timestamp: Long = 1,
        isReversed: Boolean = false,
        reversesEntryId: String? = null
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = timestamp,
        transactionType = type,
        amount = Money(100),
        accountId = "account",
        isReversed = isReversed,
        reversesEntryId = reversesEntryId
    )
}
