package com.ralledger.domain.usecase

import com.ralledger.domain.model.BillingFrequency
import java.util.Calendar

/** Advances a due date by billing periods until it lands after the date just paid. */
object SubscriptionSchedule {
    fun nextBillingDateAfterPayment(
        scheduledDate: Long,
        paymentDate: Long,
        frequency: BillingFrequency
    ): Long {
        require(scheduledDate > 0L && paymentDate > 0L) { "Choose valid billing and payment dates" }
        var periods = 1
        var nextDate: Long
        do {
            // Calculate every occurrence from the original due date. Repeatedly adding
            // one month to a clamped February date would otherwise move Jan-31 schedules
            // permanently to the 28th (or 29th).
            val calendar = Calendar.getInstance().apply { timeInMillis = scheduledDate }
            when (frequency) {
                BillingFrequency.WEEKLY -> calendar.add(Calendar.DAY_OF_YEAR, Math.multiplyExact(7, periods))
                BillingFrequency.MONTHLY -> calendar.add(Calendar.MONTH, periods)
                BillingFrequency.QUARTERLY -> calendar.add(Calendar.MONTH, Math.multiplyExact(3, periods))
                BillingFrequency.ANNUALLY -> calendar.add(Calendar.YEAR, periods)
            }
            nextDate = calendar.timeInMillis
            periods = Math.incrementExact(periods)
        } while (nextDate <= paymentDate)
        return nextDate
    }
}
