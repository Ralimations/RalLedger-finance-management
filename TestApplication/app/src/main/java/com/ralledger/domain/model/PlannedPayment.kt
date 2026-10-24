package com.ralledger.domain.model

enum class PlannedPaymentStatus(val displayName: String) {
    PLANNED("Planned"),
    CANCELLED("Cancelled")
}

data class PlannedPayment(
    val id: String,
    val description: String,
    val counterparty: String,
    val amount: Money,
    val dueDate: Long,
    val expenseCategoryId: String,
    val categoryName: String,
    val status: PlannedPaymentStatus,
    val isArchived: Boolean,
    val createdAt: Long,
    val notes: String = "",
    val counterpartyId: String? = null,
    val payments: List<PlannedPaymentRecord> = emptyList()
) {
    val activePayment: PlannedPaymentRecord?
        get() = payments.firstOrNull { it.reversedAt == null }

    val isPaid: Boolean
        get() = activePayment != null
}

data class PlannedPaymentRecord(
    val id: String,
    val plannedPaymentId: String,
    val journalEntryId: String,
    val amount: Money,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long? = null
)
