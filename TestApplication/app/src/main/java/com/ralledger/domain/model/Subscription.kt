package com.ralledger.domain.model

enum class BillingFrequency(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    ANNUALLY("Annually")
}

enum class SubscriptionStatus(val displayName: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    CANCELLED("Cancelled")
}

data class Subscription(
    val id: String,
    val name: String,
    val merchant: String = "",
    val billingAmount: Money,
    val billingFrequency: BillingFrequency = BillingFrequency.MONTHLY,
    val assignedAccountId: String,
    val assignedAccountName: String = "",
    val nextBillingDate: Long,
    val lastPaymentDate: Long? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val cancellationDate: Long? = null,
    val notes: String = ""
)
