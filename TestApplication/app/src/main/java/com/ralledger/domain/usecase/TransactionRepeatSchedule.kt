package com.ralledger.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Calendar cadence for an income or expense series. Occurrences require user confirmation. */
enum class TransactionRepeatFrequency(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Every 3 months"),
    ANNUALLY("Yearly")
}

/**
 * Computes a series occurrence from its original local-time anchor rather than from the prior
 * occurrence. This avoids month-end drift (Jan 31 -> Feb 28 -> Mar 28) and preserves wall-clock
 * time across daylight-saving changes.
 */
object TransactionRepeatSchedule {
    fun occurrenceAt(
        anchorTimestamp: Long,
        frequency: TransactionRepeatFrequency,
        occurrenceIndex: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        require(anchorTimestamp > 0L) { "Choose a valid start date and time" }
        require(occurrenceIndex >= 0L) { "Occurrence index cannot be negative" }

        val anchor = Instant.ofEpochMilli(anchorTimestamp).atZone(zoneId)
        val anchorDate = anchor.toLocalDate()
        val occurrenceDate = when (frequency) {
            TransactionRepeatFrequency.DAILY -> anchorDate.plusDays(occurrenceIndex)
            TransactionRepeatFrequency.WEEKLY -> anchorDate.plusWeeks(occurrenceIndex)
            TransactionRepeatFrequency.MONTHLY -> monthOccurrence(anchor, occurrenceIndex)
            TransactionRepeatFrequency.QUARTERLY -> monthOccurrence(anchor, Math.multiplyExact(occurrenceIndex, 3L))
            TransactionRepeatFrequency.ANNUALLY -> YearMonth.from(anchorDate)
                .plusYears(occurrenceIndex)
                .let { targetYear -> targetYear.atDay(minOf(anchorDate.dayOfMonth, targetYear.lengthOfMonth())) }
        }
        return LocalDateTime.of(occurrenceDate, anchor.toLocalTime())
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun monthOccurrence(anchor: ZonedDateTime, monthOffset: Long): LocalDate {
        val anchorDate = anchor.toLocalDate()
        val targetMonth = YearMonth.from(anchorDate).plusMonths(monthOffset)
        return targetMonth.atDay(minOf(anchorDate.dayOfMonth, targetMonth.lengthOfMonth()))
    }
}
