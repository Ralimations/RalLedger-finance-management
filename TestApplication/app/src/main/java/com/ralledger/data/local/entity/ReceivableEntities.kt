package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivablePayment
import com.ralledger.domain.model.ReceivableStatus

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
    val status: String // ReceivableStatus.name
) {
    fun toDomainModel(payments: List<ReceivablePayment>): Receivable {
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
            payments = payments
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
