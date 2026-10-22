package com.ralledger.domain.model

import java.text.NumberFormat
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

    operator fun plus(other: Money) = Money(this.centavos + other.centavos)
    operator fun minus(other: Money) = Money(this.centavos - other.centavos)
    operator fun unaryMinus() = Money(-this.centavos)

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
            return Money((amount * 100.0).toLong())
        }

        fun parseCentavosOrZero(text: String): Money {
            val clean = text.replace(",", "").replace("₱", "").replace("$", "").trim()
            val doubleVal = clean.toDoubleOrNull() ?: 0.0
            return fromDouble(doubleVal)
        }
    }
}
