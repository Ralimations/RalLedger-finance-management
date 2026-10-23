package com.ralledger.domain.model

data class SavedTransactionFilter(
    val id: String,
    val name: String,
    val filter: TransactionFilter,
    val createdAt: Long,
    val updatedAt: Long
)
