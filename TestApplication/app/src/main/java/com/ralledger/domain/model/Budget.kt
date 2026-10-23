package com.ralledger.domain.model

import java.time.YearMonth

data class Budget(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val year: Int,
    val month: Int,
    val limit: Money,
    val spent: Money,
    val isArchived: Boolean
) {
    val remaining: Money get() = Money(limit.centavos - spent.centavos)
    val overspent: Money get() = Money((spent.centavos - limit.centavos).coerceAtLeast(0L))
    val isOverBudget: Boolean get() = spent > limit
    val progressFraction: Float get() = (spent.centavos.toDouble() / limit.centavos.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val period: YearMonth get() = YearMonth.of(year, month)
}
