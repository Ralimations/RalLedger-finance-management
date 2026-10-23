package com.ralledger.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionReminderScheduleTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun schedulesOnDisplayedLocalDateAndHourAcrossDaylightSavingChange() {
        val dueDate = LocalDate.of(2024, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2024, 3, 9).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            expected,
            SubscriptionReminderSchedule.reminderAt(
                nextBillingDate = dueDate,
                daysBefore = 1,
                zoneId = zone,
                now = expected - 1
            )
        )
    }

    @Test
    fun returnsNullWhenReminderTimeHasPassed() {
        val dueDate = LocalDate.of(2026, 10, 24).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 10, 24).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        assertNull(
            SubscriptionReminderSchedule.reminderAt(
                nextBillingDate = dueDate,
                daysBefore = 0,
                zoneId = zone,
                now = now
            )
        )
    }

    @Test
    fun rejectsUnsupportedReminderTiming() {
        val dueDate = LocalDate.of(2026, 10, 24).atStartOfDay(zone).toInstant().toEpochMilli()

        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionReminderSchedule.reminderAt(dueDate, daysBefore = 2, zoneId = zone, now = 0)
        }
    }
}
