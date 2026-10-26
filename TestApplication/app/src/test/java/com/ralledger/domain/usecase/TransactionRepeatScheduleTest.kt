package com.ralledger.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TransactionRepeatScheduleTest {
    private val manila = ZoneId.of("Asia/Manila")

    @Test
    fun monthlyOccurrenceKeepsOriginalMonthEndAnchor() {
        val anchor = LocalDateTime.of(2026, 1, 31, 9, 15).atZone(manila).toInstant().toEpochMilli()

        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.MONTHLY, 1))
        assertEquals(LocalDateTime.of(2026, 3, 31, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.MONTHLY, 2))
    }

    @Test
    fun quarterlyOccurrenceUsesThreeMonthIntervalsFromOriginalAnchor() {
        val anchor = LocalDateTime.of(2026, 1, 31, 9, 15).atZone(manila).toInstant().toEpochMilli()

        assertEquals(LocalDateTime.of(2026, 4, 30, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.QUARTERLY, 1))
        assertEquals(LocalDateTime.of(2026, 7, 31, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.QUARTERLY, 2))
    }

    @Test
    fun annualLeapDayReturnsToLeapDayWhenAvailable() {
        val anchor = LocalDateTime.of(2024, 2, 29, 8, 0).atZone(manila).toInstant().toEpochMilli()

        assertEquals(LocalDateTime.of(2025, 2, 28, 8, 0), localDateTime(anchor, TransactionRepeatFrequency.ANNUALLY, 1))
        assertEquals(LocalDateTime.of(2028, 2, 29, 8, 0), localDateTime(anchor, TransactionRepeatFrequency.ANNUALLY, 4))
    }

    @Test
    fun dailyAndWeeklyCadencePreserveLocalWallClockTime() {
        val anchor = LocalDateTime.of(2026, 10, 26, 9, 15).atZone(manila).toInstant().toEpochMilli()

        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.DAILY, 2))
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 15), localDateTime(anchor, TransactionRepeatFrequency.WEEKLY, 2))
    }

    @Test
    fun monthlyOccurrencePreservesLocalTimeAcrossDaylightSavingTransition() {
        val newYork = ZoneId.of("America/New_York")
        val anchor = LocalDateTime.of(2026, 2, 8, 9, 30).atZone(newYork).toInstant().toEpochMilli()

        assertEquals(
            LocalDateTime.of(2026, 3, 8, 9, 30),
            Instant.ofEpochMilli(TransactionRepeatSchedule.occurrenceAt(anchor, TransactionRepeatFrequency.MONTHLY, 1, newYork))
                .atZone(newYork)
                .toLocalDateTime()
        )
    }

    @Test
    fun rejectsInvalidAnchorAndOccurrenceIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionRepeatSchedule.occurrenceAt(0L, TransactionRepeatFrequency.MONTHLY, 1, manila)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionRepeatSchedule.occurrenceAt(Instant.now().toEpochMilli(), TransactionRepeatFrequency.MONTHLY, -1, manila)
        }
    }

    private fun localDateTime(
        anchor: Long,
        frequency: TransactionRepeatFrequency,
        occurrenceIndex: Long
    ): LocalDateTime = Instant.ofEpochMilli(
        TransactionRepeatSchedule.occurrenceAt(anchor, frequency, occurrenceIndex, manila)
    ).atZone(manila).toLocalDateTime()
}
