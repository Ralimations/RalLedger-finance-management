package com.ralledger.domain.usecase

import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestEstimate
import com.ralledger.domain.model.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Actual/365 interest projection. Amounts and final tax are rounded to the nearest centavo. */
object InterestCalculator {
    private val precision = MathContext(34, RoundingMode.HALF_EVEN)
    private val hundredPercentBasisPoints = BigDecimal.valueOf(10_000L)

    fun estimate(
        principal: Money,
        annualRateBasisPoints: Int,
        taxRateBasisPoints: Int,
        compounding: InterestCompounding,
        from: Long,
        through: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): InterestEstimate {
        require(principal.centavos >= 0L) { "Principal cannot be negative" }
        require(annualRateBasisPoints in 0..1_000_000) { "Annual rate must be from 0% to 10,000%" }
        require(taxRateBasisPoints in 0..10_000) { "Tax rate must be from 0% to 100%" }
        require(from > 0L && through >= from) { "Choose a valid interest period" }

        val startDate = Instant.ofEpochMilli(from).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(through).atZone(zoneId).toLocalDate()
        require(!endDate.isBefore(startDate)) { "Choose a valid interest period" }
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        if (principal.isZero || annualRateBasisPoints == 0 || days == 0L) {
            return InterestEstimate(Money.ZERO, Money.ZERO, Money.ZERO)
        }

        val annualRate = BigDecimal.valueOf(annualRateBasisPoints.toLong())
            .divide(hundredPercentBasisPoints, precision)
        val growth = when (compounding) {
            InterestCompounding.SIMPLE -> BigDecimal.ONE.add(
                annualRate.multiply(BigDecimal.valueOf(days), precision)
                    .divide(BigDecimal.valueOf(365L), precision),
                precision
            )
            else -> {
                val periods = BigDecimal.valueOf(days)
                    .multiply(BigDecimal.valueOf(compounding.periodsPerYear.toLong()), precision)
                    .divide(BigDecimal.valueOf(365L), precision)
                val wholePeriods = periods.setScale(0, RoundingMode.DOWN).intValueExact()
                val remainingFraction = periods.subtract(BigDecimal.valueOf(wholePeriods.toLong()), precision)
                val periodicRate = annualRate.divide(BigDecimal.valueOf(compounding.periodsPerYear.toLong()), precision)
                val completedGrowth = BigDecimal.ONE.add(periodicRate, precision).pow(wholePeriods, precision)
                val partialGrowth = BigDecimal.ONE.add(periodicRate.multiply(remainingFraction, precision), precision)
                completedGrowth.multiply(partialGrowth, precision)
            }
        }
        val grossCentavos = BigDecimal.valueOf(principal.centavos)
            .multiply(growth.subtract(BigDecimal.ONE, precision), precision)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val taxCentavos = BigDecimal.valueOf(grossCentavos)
            .multiply(BigDecimal.valueOf(taxRateBasisPoints.toLong()), precision)
            .divide(hundredPercentBasisPoints, 0, RoundingMode.HALF_UP)
            .longValueExact()
        val gross = Money(grossCentavos)
        val tax = Money(taxCentavos)
        return InterestEstimate(gross, tax, gross - tax)
    }

}
