package com.ralledger.domain.model

enum class ReceivableStatus(val displayName: String) {
    DRAFT("Draft"),
    CONFIRMED("Confirmed"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    CANCELLED("Cancelled"),
    WRITTEN_OFF("Written Off")
}

data class ReceivablePayment(
    val id: String,
    val receivableId: String,
    val journalEntryId: String,
    val amount: Money,
    val destinationAccountId: String,
    val paymentDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val reversedAt: Long? = null
)

data class Receivable(
    val id: String,
    val clientName: String,
    val originalAmount: Money,
    val outstandingAmount: Money,
    val recognitionDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val linkedCategoryId: String? = null,
    val description: String = "",
    val status: ReceivableStatus = ReceivableStatus.CONFIRMED,
    val payments: List<ReceivablePayment> = emptyList()
)
