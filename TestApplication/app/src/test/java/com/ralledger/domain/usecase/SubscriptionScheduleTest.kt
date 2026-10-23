package com.ralledger.domain.usecase

import com.ralledger.domain.model.BillingFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar

class SubscriptionScheduleTest {
    @Test
    fun overdueDailyScheduleSkipsPaidOccurrences() {
        val scheduled = timestamp(2026, Calendar.JANUARY, 1)
        val payment = timestamp(2026, Calendar.JANUARY, 4) + 60 * 60 * 1000L

        val nextDate = SubscriptionSchedule.nextBillingDateAfterPayment(
            scheduled,
            payment,
            BillingFrequency.DAILY
        )

        assertEquals(timestamp(2026, Calendar.JANUARY, 5), nextDate)
    }

    @Test
    fun overdueMonthlyScheduleAdvancesFromOriginalMonthEndWithoutDateDrift() {
        val scheduled = timestamp(2026, Calendar.JANUARY, 31)
        val payment = timestamp(2026, Calendar.MARCH, 1)

        val nextDate = SubscriptionSchedule.nextBillingDateAfterPayment(
            scheduled,
            payment,
            BillingFrequency.MONTHLY
        )

        assertEquals(timestamp(2026, Calendar.MARCH, 31), nextDate)
    }

    @Test
    fun earlyPaymentAdvancesOneBillingCycle() {
        val scheduled = timestamp(2026, Calendar.JANUARY, 31)
        val payment = timestamp(2026, Calendar.JANUARY, 10)

        val nextDate = SubscriptionSchedule.nextBillingDateAfterPayment(
            scheduled,
            payment,
            BillingFrequency.MONTHLY
        )

        assertEquals(timestamp(2026, Calendar.FEBRUARY, 28), nextDate)
    }

    @Test
    fun overdueWeeklyScheduleSkipsPaidPeriods() {
        val scheduled = timestamp(2026, Calendar.JANUARY, 1)
        val payment = timestamp(2026, Calendar.JANUARY, 20)

        val nextDate = SubscriptionSchedule.nextBillingDateAfterPayment(
            scheduled,
            payment,
            BillingFrequency.WEEKLY
        )

        assertEquals(timestamp(2026, Calendar.JANUARY, 22), nextDate)
    }

    @Test
    fun rejectsInvalidDates() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionSchedule.nextBillingDateAfterPayment(0L, 1L, BillingFrequency.MONTHLY)
        }
    }

    private fun timestamp(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis
}
