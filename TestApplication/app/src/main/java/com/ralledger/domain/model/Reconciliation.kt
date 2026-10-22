package com.ralledger.domain.model

data class Reconciliation(
    val id: String,
    val accountId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val recordedBalance: Money,
    val actualBalance: Money,
    val difference: Money,
    val adjustmentReason: String = "",
    val journalEntryId: String? = null
)
