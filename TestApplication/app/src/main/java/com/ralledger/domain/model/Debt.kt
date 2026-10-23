package com.ralledger.domain.model

enum class DebtKind(val displayName: String) {
    BILL("Bill"),
    LOAN("Loan")
}

enum class DebtStatus(val displayName: String) {
    OPEN("Open"),
    PARTIALLY_PAID("Partially paid"),
    PAID("Paid"),
    CANCELLED("Cancelled")
}

data class Debt(
    val id: String,
    val title: String,
    val counterparty: String,
    val kind: DebtKind,
    val originalAmount: Money,
    val outstandingAmount: Money,
    val dueDate: Long,
    val categoryId: String?,
    val categoryName: String?,
    val fundingAccountId: String?,
    val recognitionJournalEntryId: String,
    val status: DebtStatus,
    val isArchived: Boolean,
    val createdAt: Long,
    val payments: List<DebtPayment> = emptyList(),
    val counterpartyId: String? = null
)

data class DebtPayment(
    val id: String,
    val debtId: String,
    val journalEntryId: String,
    val paymentAccountId: String,
    val principal: Money,
    val interest: Money,
    val interestCategoryId: String?,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long? = null
)
