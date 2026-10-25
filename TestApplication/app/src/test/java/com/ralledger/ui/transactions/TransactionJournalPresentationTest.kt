package com.ralledger.ui.transactions

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TransactionJournalPresentationTest {
    @Test
    fun journalGroupsByLocalDayAndKeepsNewestFirst() {
        val todayLate = transaction("today-late", "today-late", TransactionType.EXPENSE, localTime(25, 18))
        val yesterday = transaction("yesterday", "yesterday", TransactionType.INCOME, localTime(24, 12))
        val todayEarly = transaction("today-early", "today-early", TransactionType.TRANSFER, localTime(25, 8))

        val groups = groupTransactionsByLocalDay(listOf(yesterday, todayEarly, todayLate))

        assertEquals(2, groups.size)
        assertEquals(listOf("today-late", "today-early"), groups[0].transactions.map { it.id })
        assertEquals(listOf("yesterday"), groups[1].transactions.map { it.id })
    }

    @Test
    fun journalHistoryUsesOnlyStoredReversalLinks() {
        val original = transaction("original", "entry-1", TransactionType.EXPENSE, 100)
        val reversal = transaction("reversal", "entry-2", TransactionType.REVERSAL, 200, reversesEntryId = "entry-1")
        val unrelated = transaction("unrelated", "entry-3", TransactionType.INCOME, 300)

        assertEquals(listOf("entry-2"), relatedJournalEntries(original, listOf(original, reversal, unrelated)).map { it.journalEntryId })
        assertEquals(listOf("entry-1"), relatedJournalEntries(reversal, listOf(original, reversal, unrelated)).map { it.journalEntryId })
    }

    @Test
    fun auditStatusReflectsPostedReversedAndReversalRows() {
        assertEquals("Posted", transaction("posted", "posted", TransactionType.EXPENSE, 1).let(::transactionAuditStatus))
        assertEquals("Reversed", transaction("old", "old", TransactionType.EXPENSE, 1, isReversed = true).let(::transactionAuditStatus))
        assertEquals("Reversal entry", transaction("rev", "rev", TransactionType.REVERSAL, 1).let(::transactionAuditStatus))
    }

    private fun localTime(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, 0, 0)
    }.timeInMillis

    private fun transaction(
        id: String,
        journalEntryId: String,
        type: TransactionType,
        timestamp: Long,
        reversesEntryId: String? = null,
        isReversed: Boolean = false
    ) = Transaction(
        id = id,
        journalEntryId = journalEntryId,
        timestamp = timestamp,
        transactionType = type,
        amount = Money(100),
        accountId = "account",
        reversesEntryId = reversesEntryId,
        isReversed = isReversed
    )
}
