package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.DebtPayment
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.Money

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(entity = CounterpartyEntity::class, parentColumns = ["id"], childColumns = ["counterpartyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["recognitionJournalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["fundingAccountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["recognitionJournalEntryId"], unique = true), Index(value = ["categoryId"]), Index(value = ["fundingAccountId"]), Index(value = ["counterpartyId"]), Index(value = ["dueDate"]), Index(value = ["kind", "status"])]
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val title: String,
    val counterparty: String,
    val kind: String,
    val originalAmountCentavos: Long,
    val outstandingAmountCentavos: Long,
    val dueDate: Long,
    val categoryId: String?,
    val fundingAccountId: String?,
    val recognitionJournalEntryId: String,
    val status: String,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val counterpartyId: String? = null
)

@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["paymentAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["interestCategoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["debtId"]), Index(value = ["journalEntryId"], unique = true), Index(value = ["paymentAccountId"]), Index(value = ["interestCategoryId"])]
)
data class DebtPaymentEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val journalEntryId: String,
    val paymentAccountId: String,
    val principalCentavos: Long,
    val interestCentavos: Long,
    val interestCategoryId: String?,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long? = null
) {
    fun toDomainModel() = DebtPayment(
        id, debtId, journalEntryId, paymentAccountId, Money(principalCentavos), Money(interestCentavos),
        interestCategoryId, paymentDate, notes, reversedAt
    )
}

fun DebtEntity.toDomainModel(
    categoryName: String?,
    payments: List<DebtPayment>
) = Debt(
    id = id,
    title = title,
    counterparty = counterparty,
    kind = DebtKind.valueOf(kind),
    originalAmount = Money(originalAmountCentavos),
    outstandingAmount = Money(outstandingAmountCentavos),
    dueDate = dueDate,
    categoryId = categoryId,
    categoryName = categoryName,
    fundingAccountId = fundingAccountId,
    recognitionJournalEntryId = recognitionJournalEntryId,
    status = DebtStatus.valueOf(status),
    isArchived = isArchived,
    createdAt = createdAt,
    payments = payments,
    counterpartyId = counterpartyId
)
