package com.ralledger.domain.model

data class Transaction(
    val id: String,
    val journalEntryId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val transactionType: TransactionType,
    val amount: Money,
    val fee: Money = Money.ZERO,
    val accountId: String,
    val accountName: String = "",
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val counterparty: String = "",
    val description: String = "",
    val notes: String = "",
    val reversesEntryId: String? = null,
    val isReversed: Boolean = false,
    val secondaryCategoryId: String? = null,
    val secondaryCategoryName: String? = null,
    val tagIds: Set<String> = emptySet(),
    val tagNames: List<String> = emptyList(),
    val projectId: String? = null,
    val projectName: String? = null
)

data class TransactionCorrection(
    val transactionType: TransactionType,
    val timestamp: Long,
    val amount: Money,
    val fee: Money = Money.ZERO,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val counterparty: String = "",
    val description: String = "",
    val notes: String = ""
)
