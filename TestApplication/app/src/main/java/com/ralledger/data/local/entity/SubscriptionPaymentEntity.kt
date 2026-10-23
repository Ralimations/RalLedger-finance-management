package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.SubscriptionPayment

@Entity(
    tableName = "subscription_payments",
    foreignKeys = [
        ForeignKey(entity = SubscriptionEntity::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["paymentAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["expenseCategoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index("subscriptionId"),
        Index(value = ["journalEntryId"], unique = true),
        Index("paymentAccountId"),
        Index("expenseCategoryId"),
        Index(value = ["subscriptionId", "paymentDate"])
    ]
)
data class SubscriptionPaymentEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val journalEntryId: String,
    val paymentAccountId: String,
    val expenseCategoryId: String,
    val amountCentavos: Long,
    val paymentDate: Long,
    val notes: String,
    val reversedAt: Long?,
    val previousLastPaymentDate: Long?,
    val previousNextBillingDate: Long,
    val nextBillingDateAfter: Long
)

fun SubscriptionPaymentEntity.toDomainModel(
    paymentAccountName: String,
    expenseCategoryName: String
) = SubscriptionPayment(
    id = id,
    subscriptionId = subscriptionId,
    journalEntryId = journalEntryId,
    paymentAccountId = paymentAccountId,
    paymentAccountName = paymentAccountName,
    expenseCategoryId = expenseCategoryId,
    expenseCategoryName = expenseCategoryName,
    amount = Money(amountCentavos),
    paymentDate = paymentDate,
    notes = notes,
    reversedAt = reversedAt,
    previousLastPaymentDate = previousLastPaymentDate,
    previousNextBillingDate = previousNextBillingDate,
    nextBillingDateAfter = nextBillingDateAfter
)
