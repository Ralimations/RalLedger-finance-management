package com.ralledger.domain.usecase

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Calculates the local reminder time for a subscription's next displayed billing date. */
object SubscriptionReminderSchedule {
    val supportedDaysBefore = setOf(0, 1, 3)

    fun reminderAt(
        nextBillingDate: Long,
        daysBefore: Int,
        hourOfDay: Int = 9,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): Long? {
        require(nextBillingDate > 0L) { "Choose a valid billing date" }
        require(daysBefore in supportedDaysBefore) { "Reminder timing must be 0, 1, or 3 days before billing" }
        require(hourOfDay in 0..23) { "Reminder hour must be between 0 and 23" }

        val dueDate = Instant.ofEpochMilli(nextBillingDate).atZone(zoneId).toLocalDate()
        val reminderTime = dueDate.minusDays(daysBefore.toLong())
            .atTime(LocalTime.of(hourOfDay, 0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        return reminderTime.takeIf { it > now }
    }
}
