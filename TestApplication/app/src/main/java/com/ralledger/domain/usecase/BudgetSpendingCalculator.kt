package com.ralledger.domain.usecase

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.util.Calendar

data class BudgetSpendingKey(val categoryId: String, val year: Int, val month: Int)

object BudgetSpendingCalculator {
    fun aggregate(transactions: List<Transaction>): Map<BudgetSpendingKey, Money> {
        val spending = mutableMapOf<BudgetSpendingKey, Long>()
        transactions.forEach { transaction ->
            if (transaction.isReversed) return@forEach
            val categoryId = transaction.categoryId ?: return@forEach
            val amount = when (transaction.transactionType) {
                TransactionType.EXPENSE -> transaction.amount.centavos
                TransactionType.TRANSFER -> transaction.fee.centavos
                else -> 0L
            }
            if (amount <= 0L) return@forEach

            val calendar = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }
            val key = BudgetSpendingKey(
                categoryId = categoryId,
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1
            )
            spending[key] = Math.addExact(spending[key] ?: 0L, amount)
        }
        return spending.mapValues { Money(it.value) }
    }
}
