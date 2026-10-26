package com.ralledger.ui.accounts

import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType

internal fun accountTransactionDirection(transaction: Transaction, accountId: String): String = when {
    transaction.transactionType == TransactionType.TRANSFER && transaction.destinationAccountId == accountId -> "+"
    transaction.transactionType == TransactionType.TRANSFER && transaction.accountId == accountId -> "−"
    transaction.transactionType in setOf(
        TransactionType.INCOME,
        TransactionType.REFUND,
        TransactionType.INTEREST_CREDIT,
        TransactionType.RECEIVABLE_COLLECTION,
        TransactionType.LOAN_DISBURSEMENT
    ) -> "+"
    transaction.transactionType in setOf(
        TransactionType.EXPENSE,
        TransactionType.SUBSCRIPTION_PAYMENT,
        TransactionType.RECEIVABLE_WRITEOFF,
        TransactionType.PAYABLE_PAYMENT,
        TransactionType.LOAN_REPAYMENT
    ) -> "−"
    else -> ""
}

internal fun transactionsForAccount(accountId: String, transactions: List<Transaction>): List<Transaction> =
    transactions.asSequence()
        .filter { it.accountId == accountId || it.destinationAccountId == accountId }
        .sortedWith(compareByDescending<Transaction> { it.timestamp }.thenBy { it.id })
        .toList()
