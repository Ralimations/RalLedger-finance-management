package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val merchant: String,
    val billingAmountCentavos: Long,
    val billingFrequency: String, // BillingFrequency.name
    val assignedAccountId: String,
    val nextBillingDate: Long,
    val lastPaymentDate: Long?,
    val status: String, // SubscriptionStatus.name
    val cancellationDate: Long?,
    val notes: String,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false
) {
    fun toDomainModel(assignedAccountName: String): Subscription {
        return Subscription(
            id = id,
            name = name,
            merchant = merchant,
            billingAmount = Money(billingAmountCentavos),
            billingFrequency = BillingFrequency.valueOf(billingFrequency),
            assignedAccountId = assignedAccountId,
            assignedAccountName = assignedAccountName,
            nextBillingDate = nextBillingDate,
            lastPaymentDate = lastPaymentDate,
            status = SubscriptionStatus.valueOf(status),
            cancellationDate = cancellationDate,
            notes = notes,
            isArchived = isArchived
        )
    }

    companion object {
        fun fromDomainModel(sub: Subscription): SubscriptionEntity {
            return SubscriptionEntity(
                id = sub.id,
                name = sub.name,
                merchant = sub.merchant,
                billingAmountCentavos = sub.billingAmount.centavos,
                billingFrequency = sub.billingFrequency.name,
                assignedAccountId = sub.assignedAccountId,
                nextBillingDate = sub.nextBillingDate,
                lastPaymentDate = sub.lastPaymentDate,
                status = sub.status.name,
                cancellationDate = sub.cancellationDate,
                notes = sub.notes,
                isArchived = sub.isArchived
            )
        }
    }
}
