package com.ralledger.domain.model

import java.util.Locale

data class TransactionTemplate(
    val id: String,
    val name: String,
    val transactionType: TransactionType,
    val amount: Money,
    val fee: Money = Money.ZERO,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val feeCategoryId: String? = null,
    val counterparty: String = "",
    val description: String,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizeTransactionTemplateName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
