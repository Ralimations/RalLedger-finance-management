package com.ralledger.domain.usecase

import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InterestCalculatorTest {
    @Test
    fun simpleInterestSeparatesGrossTaxAndNet() {
        val result = InterestCalculator.estimate(
            principal = Money(1_000_000L),
            annualRateBasisPoints = 500,
            taxRateBasisPoints = 2_000,
            compounding = InterestCompounding.SIMPLE,
            from = DAY,
            through = DAY + YEAR
        )

        assertEquals(Money(50_000L), result.gross)
        assertEquals(Money(10_000L), result.tax)
        assertEquals(Money(40_000L), result.net)
    }

    @Test
    fun monthlyCompoundingExceedsSimpleInterestForTheSameTerms() {
        val simple = estimate(InterestCompounding.SIMPLE)
        val monthly = estimate(InterestCompounding.MONTHLY)
        assertTrue(monthly.gross > simple.gross)
    }

    @Test
    fun shortPeriodRoundsToCentavosAndNoElapsedDayProducesZero() {
        val singleDay = InterestCalculator.estimate(
            Money(1_000_000L), 500, 0, InterestCompounding.SIMPLE, DAY, DAY + DAY_MILLIS
        )
        val partialDay = InterestCalculator.estimate(
            Money(1_000_000L), 500, 0, InterestCompounding.SIMPLE, DAY, DAY + 1
        )
        assertEquals(Money(137L), singleDay.gross)
        assertEquals(Money.ZERO, partialDay.gross)
    }

    @Test
    fun rejectsInvalidRatesAndDateRanges() {
        assertTrue(runCatching { InterestCalculator.estimate(Money(1L), -1, 0, InterestCompounding.SIMPLE, DAY, DAY + YEAR) }.isFailure)
        assertTrue(runCatching { InterestCalculator.estimate(Money(1L), 1, 10_001, InterestCompounding.SIMPLE, DAY, DAY + YEAR) }.isFailure)
        assertTrue(runCatching { InterestCalculator.estimate(Money(1L), 1, 0, InterestCompounding.SIMPLE, DAY + 1, DAY) }.isFailure)
    }

    @Test
    fun countsCalendarDaysAcrossDaylightSavingChanges() {
        val newYork = ZoneId.of("America/New_York")
        val start = LocalDate.of(2024, 3, 9).atStartOfDay(newYork).toInstant().toEpochMilli()
        val end = LocalDate.of(2024, 3, 11).atStartOfDay(newYork).toInstant().toEpochMilli()

        val result = InterestCalculator.estimate(
            Money(36_500_000L), 500, 0, InterestCompounding.SIMPLE, start, end, newYork
        )

        assertEquals(Money(10_000L), result.gross)
    }

    private fun estimate(compounding: InterestCompounding) = InterestCalculator.estimate(
        Money(1_000_000L), 500, 2_000, compounding, DAY, DAY + YEAR
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val DAY = 1_700_000_000_000L
        const val YEAR = 365L * DAY_MILLIS
    }
}
