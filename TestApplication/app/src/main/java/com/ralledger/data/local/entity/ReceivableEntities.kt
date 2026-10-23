package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivablePayment
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.ReceivableWriteOff

@Entity(tableName = "receivables")
data class ReceivableEntity(
    @PrimaryKey val id: String,
    val clientName: String,
    val originalAmountCentavos: Long,
    val outstandingAmountCentavos: Long,
    val recognitionDate: Long,
    val dueDate: Long,
    val linkedCategoryId: String?,
    val description: String,
    val status: String, // ReceivableStatus.name
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false
) {
    fun toDomainModel(
        payments: List<ReceivablePayment>,
        writeOffs: List<ReceivableWriteOff> = emptyList()
    ): Receivable {
        return Receivable(
            id = id,
            clientName = clientName,
            originalAmount = Money(originalAmountCentavos),
            outstandingAmount = Money(outstandingAmountCentavos),
            recognitionDate = recognitionDate,
            dueDate = dueDate,
            linkedCategoryId = linkedCategoryId,
            description = description,
            status = ReceivableStatus.valueOf(status),
            payments = payments,
            writeOffs = writeOffs,
            isArchived = isArchived
        )
    }
}

@Entity(
    tableName = "receivable_payments",
    foreignKeys = [
        ForeignKey(
            entity = ReceivableEntity::class,
            parentColumns = ["id"],
            childColumns = ["receivableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["receivableId"]),
        Index(value = ["journalEntryId"])
    ]
)
data class ReceivablePaymentEntity(
    @PrimaryKey val id: String,
    val receivableId: String,
    val journalEntryId: String,
    val amountCentavos: Long,
    val destinationAccountId: String,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long? = null
) {
    fun toDomainModel(): ReceivablePayment {
        return ReceivablePayment(
            id = id,
            receivableId = receivableId,
            journalEntryId = journalEntryId,
            amount = Money(amountCentavos),
            destinationAccountId = destinationAccountId,
            paymentDate = paymentDate,
            notes = notes,
            reversedAt = reversedAt
        )
    }
}

@Entity(
    tableName = "receivable_write_offs",
    foreignKeys = [
        ForeignKey(
            entity = ReceivableEntity::class,
            parentColumns = ["id"],
            childColumns = ["receivableId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["receivableId"]), Index(value = ["journalEntryId"], unique = true)]
)
data class ReceivableWriteOffEntity(
    @PrimaryKey val id: String,
    val receivableId: String,
    val journalEntryId: String,
    val amountCentavos: Long,
    val expenseCategoryId: String,
    val date: Long,
    val notes: String,
    val reversedAt: Long? = null
) {
    fun toDomainModel() = ReceivableWriteOff(
        id = id,
        receivableId = receivableId,
        journalEntryId = journalEntryId,
        amount = Money(amountCentavos),
        expenseCategoryId = expenseCategoryId,
        date = date,
        notes = notes,
        reversedAt = reversedAt
    )
}
