package com.ralledger.domain.model


enum class TransactionRepeatFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    ANNUALLY
}

enum class RecurringTransactionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED,
    COMPLETED
}

data class RecurringTransaction(
    val id: String,
    val transactionType: TransactionType,
    val amount: Money,
    val accountId: String,
    val categoryId: String,
    val counterparty: String,
    val description: String,
    val notes: String,
    val tagIds: Set<String>,
    val frequency: TransactionRepeatFrequency,
    val timeZoneId: String,
    val anchorTimestamp: Long,
    val nextOccurrenceIndex: Long,
    val nextOccurrenceTimestamp: Long,
    val remindersEnabled: Boolean = false,
    val endTimestamp: Long?,
    val status: RecurringTransactionStatus,
    val createdAt: Long,
    val updatedAt: Long
)

data class RecurringTransactionOccurrence(
    val id: String,
    val recurringTransactionId: String,
    val occurrenceIndex: Long,
    val journalEntryId: String,
    val scheduledTimestamp: Long,
    val recordedTimestamp: Long,
    val createdAt: Long
)
