package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "planned_payments",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["expenseCategoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CounterpartyEntity::class, parentColumns = ["id"], childColumns = ["counterpartyId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["expenseCategoryId"]), Index(value = ["counterpartyId"]), Index(value = ["dueDate"])]
)
data class PlannedPaymentEntity(
    @PrimaryKey val id: String,
    val description: String,
    val counterparty: String,
    val amountCentavos: Long,
    val dueDate: Long,
    val expenseCategoryId: String,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val status: String,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val counterpartyId: String? = null
)

@Entity(
    tableName = "planned_payment_records",
    foreignKeys = [
        ForeignKey(entity = PlannedPaymentEntity::class, parentColumns = ["id"], childColumns = ["plannedPaymentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["plannedPaymentId"]), Index(value = ["journalEntryId"], unique = true)]
)
data class PlannedPaymentRecordEntity(
    @PrimaryKey val id: String,
    val plannedPaymentId: String,
    val journalEntryId: String,
    val amountCentavos: Long,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long? = null
)
