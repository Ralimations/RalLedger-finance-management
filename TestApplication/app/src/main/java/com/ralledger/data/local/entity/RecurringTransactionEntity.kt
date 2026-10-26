package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.RecurringTransaction
import com.ralledger.domain.model.RecurringTransactionStatus
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.model.TransactionRepeatFrequency

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["status", "nextOccurrenceTimestamp"])
    ]
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    val transactionType: String,
    val amountCentavos: Long,
    val accountId: String,
    val categoryId: String,
    val counterparty: String,
    val description: String,
    val notes: String,
    val frequency: String,
    val timeZoneId: String,
    val anchorTimestamp: Long,
    val nextOccurrenceIndex: Long,
    val nextOccurrenceTimestamp: Long,
    val endTimestamp: Long?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(tagIds: Set<String>) = RecurringTransaction(
        id = id,
        transactionType = TransactionType.valueOf(transactionType),
        amount = Money(amountCentavos),
        accountId = accountId,
        categoryId = categoryId,
        counterparty = counterparty,
        description = description,
        notes = notes,
        tagIds = tagIds,
        frequency = TransactionRepeatFrequency.valueOf(frequency),
        timeZoneId = timeZoneId,
        anchorTimestamp = anchorTimestamp,
        nextOccurrenceIndex = nextOccurrenceIndex,
        nextOccurrenceTimestamp = nextOccurrenceTimestamp,
        endTimestamp = endTimestamp,
        status = RecurringTransactionStatus.valueOf(status),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomainModel(item: RecurringTransaction) = RecurringTransactionEntity(
            id = item.id,
            transactionType = item.transactionType.name,
            amountCentavos = item.amount.centavos,
            accountId = item.accountId,
            categoryId = item.categoryId,
            counterparty = item.counterparty.trim(),
            description = item.description.trim(),
            notes = item.notes.trim(),
            frequency = item.frequency.name,
            timeZoneId = item.timeZoneId,
            anchorTimestamp = item.anchorTimestamp,
            nextOccurrenceIndex = item.nextOccurrenceIndex,
            nextOccurrenceTimestamp = item.nextOccurrenceTimestamp,
            endTimestamp = item.endTimestamp,
            status = item.status.name,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }
}

@Entity(
    tableName = "recurring_transaction_tags",
    primaryKeys = ["recurringTransactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class RecurringTransactionTagEntity(
    val recurringTransactionId: String,
    val tagId: String
)

@Entity(
    tableName = "recurring_transaction_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTransactionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["recurringTransactionId", "occurrenceIndex"], unique = true),
        Index(value = ["journalEntryId"], unique = true)
    ]
)
data class RecurringTransactionOccurrenceEntity(
    @PrimaryKey val id: String,
    val recurringTransactionId: String,
    val occurrenceIndex: Long,
    val journalEntryId: String,
    val scheduledTimestamp: Long,
    val recordedTimestamp: Long,
    val createdAt: Long
)
