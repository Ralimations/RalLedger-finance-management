package com.ralledger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionTest {
    @Test
    fun normalizedMonthlyCost_convertsBillingPeriodsUsingAverageMonth() {
        fun subscription(amount: Long, frequency: BillingFrequency) = Subscription(
            id = "sub",
            name = "Test",
            billingAmount = Money(amount),
            billingFrequency = frequency,
            assignedAccountId = "account",
            nextBillingDate = 0L
        )

        assertEquals(Money(43333L), subscription(10000L, BillingFrequency.WEEKLY).normalizedMonthlyCost())
        assertEquals(Money(10000L), subscription(10000L, BillingFrequency.MONTHLY).normalizedMonthlyCost())
        assertEquals(Money(3333L), subscription(10000L, BillingFrequency.QUARTERLY).normalizedMonthlyCost())
        assertEquals(Money(833L), subscription(10000L, BillingFrequency.ANNUALLY).normalizedMonthlyCost())
    }
}
