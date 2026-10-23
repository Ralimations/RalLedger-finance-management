package com.ralledger.domain.model

data class TransactionTag(
    val id: String,
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val isArchived: Boolean = false
)
