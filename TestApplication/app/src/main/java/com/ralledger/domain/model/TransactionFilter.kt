package com.ralledger.domain.model

enum class TransactionTypeFilter { ALL, INCOME, EXPENSE, TRANSFER, OTHER }

data class TransactionFilter(
    val query: String = "",
    val type: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val accountId: String? = null,
    val categoryId: String? = null,
    val startInclusive: Long? = null,
    val endExclusive: Long? = null
) {
    fun matches(transaction: Transaction): Boolean {
        val matchesSearch = query.isBlank() ||
            transaction.description.contains(query, ignoreCase = true) ||
            transaction.counterparty.contains(query, ignoreCase = true) ||
            transaction.accountName.contains(query, ignoreCase = true) ||
            transaction.destinationAccountName?.contains(query, ignoreCase = true) == true ||
            transaction.categoryName?.contains(query, ignoreCase = true) == true
        val matchesType = when (type) {
            TransactionTypeFilter.ALL -> true
            TransactionTypeFilter.INCOME -> transaction.transactionType == TransactionType.INCOME
            TransactionTypeFilter.EXPENSE -> transaction.transactionType in setOf(
                TransactionType.EXPENSE,
                TransactionType.RECEIVABLE_WRITEOFF,
                TransactionType.PAYABLE_RECOGNITION
            )
            TransactionTypeFilter.TRANSFER -> transaction.transactionType == TransactionType.TRANSFER
            TransactionTypeFilter.OTHER -> transaction.transactionType !in setOf(
                TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.RECEIVABLE_WRITEOFF, TransactionType.TRANSFER
            )
        }
        val matchesAccount = accountId == null ||
            transaction.accountId == accountId || transaction.destinationAccountId == accountId
        val matchesCategory = categoryId == null || transaction.categoryId == categoryId
        val matchesStart = startInclusive == null || transaction.timestamp >= startInclusive
        val matchesEnd = endExclusive == null || transaction.timestamp < endExclusive
        return matchesSearch && matchesType && matchesAccount && matchesCategory && matchesStart && matchesEnd
    }
}
