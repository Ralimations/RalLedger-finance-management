package com.ralledger.ui.transactions

import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.util.Calendar

internal data class TransactionDayGroup(
    val dayStart: Long,
    val transactions: List<Transaction>
)

internal fun groupTransactionsByLocalDay(transactions: List<Transaction>): List<TransactionDayGroup> =
    transactions.groupBy { transaction ->
        Calendar.getInstance().apply {
            timeInMillis = transaction.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.toSortedMap(compareByDescending { it })
        .map { (dayStart, entries) ->
            TransactionDayGroup(
                dayStart = dayStart,
                transactions = entries.sortedWith(
                    compareByDescending<Transaction> { it.timestamp }.thenBy { it.id }
                )
            )
        }

internal fun transactionAuditStatus(transaction: Transaction): String = when {
    transaction.transactionType == TransactionType.REVERSAL -> "Reversal entry"
    transaction.isReversed -> "Reversed"
    else -> "Posted"
}

/** Returns only journal entries whose stored reversal link points to this entry or its original. */
internal fun relatedJournalEntries(
    transaction: Transaction,
    journal: List<Transaction>
): List<Transaction> = journal.asSequence()
    .filter { entry ->
        entry.journalEntryId != transaction.journalEntryId &&
            (entry.reversesEntryId == transaction.journalEntryId ||
                transaction.reversesEntryId == entry.journalEntryId)
    }
    .distinctBy(Transaction::journalEntryId)
    .sortedBy(Transaction::timestamp)
    .toList()
