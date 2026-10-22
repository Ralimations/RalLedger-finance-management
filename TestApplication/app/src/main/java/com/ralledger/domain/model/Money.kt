package com.ralledger.domain.model

import java.text.NumberFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Value wrapper around monetary centavos (Long) to enforce exact monetary math.
 * Prevents floating point rounding errors.
 */
data class Money(val centavos: Long = 0L) : Comparable<Money> {

    val isZero: Boolean get() = centavos == 0L
    val isPositive: Boolean get() = centavos > 0L
    val isNegative: Boolean get() = centavos < 0L

    operator fun plus(other: Money) = Money(Math.addExact(this.centavos, other.centavos))
    operator fun minus(other: Money) = Money(Math.subtractExact(this.centavos, other.centavos))
    operator fun unaryMinus() = Money(Math.negateExact(this.centavos))

    override fun compareTo(other: Money): Int = this.centavos.compareTo(other.centavos)

    fun format(currencyCode: String = "PHP"): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
            currency = Currency.getInstance(currencyCode)
        }
        return format.format(centavos / 100.0)
    }

    companion object {
        val ZERO = Money(0L)

        fun fromDouble(amount: Double): Money {
            require(amount.isFinite()) { "Amount must be finite" }
            val scaled = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
            return Money(scaled.longValueExact())
        }

        /** Parses user-entered decimal money without passing through binary floating point. */
        fun parse(text: String): Money? {
            val clean = text.trim()
                .replace("₱", "")
                .replace("$", "")
                .replace(" ", "")
            if (clean.isEmpty() || !AMOUNT_PATTERN.matches(clean)) return null

            return runCatching {
                val decimal = BigDecimal(clean.replace(",", ""))
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                Money(decimal.longValueExact())
            }.getOrNull()
        }

        private val AMOUNT_PATTERN = Regex("^[+-]?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?$")
    }
}
