package com.ralledger.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

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
) {
    /** Average monthly commitment using a 52-week year and exact centavo rounding. */
    fun normalizedMonthlyCost(): Money {
        val multiplier = if (billingFrequency == BillingFrequency.WEEKLY) 52L else 1L
        val divisor = when (billingFrequency) {
            BillingFrequency.WEEKLY, BillingFrequency.ANNUALLY -> 12L
            BillingFrequency.QUARTERLY -> 3L
            BillingFrequency.MONTHLY -> 1L
        }
        val monthly = BigDecimal.valueOf(billingAmount.centavos)
            .multiply(BigDecimal.valueOf(multiplier))
            .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP)
        return Money(monthly.longValueExact())
    }
}
