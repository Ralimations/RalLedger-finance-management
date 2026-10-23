package com.ralledger.domain.model

enum class ExpectedIncomeStatus(val displayName: String) {
    PLANNED("Planned"),
    CONVERTED("Converted to receivable"),
    CANCELLED("Cancelled")
}

data class ExpectedIncome(
    val id: String,
    val description: String,
    val counterparty: String,
    val amount: Money,
    val expectedDate: Long,
    val incomeCategoryId: String,
    val incomeSourceName: String,
    val status: ExpectedIncomeStatus,
    val convertedReceivableId: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long
)
