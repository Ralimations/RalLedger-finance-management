package com.ralledger.ui.dashboard

import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType

/** Recent activity excludes reversals and technical balance setup entries. */
internal fun recentDashboardTransactions(transactions: List<Transaction>, limit: Int = 5): List<Transaction> {
    if (limit <= 0) return emptyList()
    return transactions.asSequence()
        .filter { transaction ->
            !transaction.isReversed &&
                transaction.reversesEntryId == null &&
                transaction.transactionType !in setOf(
                    TransactionType.OPENING_BALANCE,
                    TransactionType.BALANCE_ADJUSTMENT,
                    TransactionType.REVERSAL
                )
        }
        .sortedWith(compareByDescending<Transaction> { it.timestamp }.thenBy { it.id })
        .take(limit)
        .toList()
}
