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
            val (categoryId, amount) = when (transaction.transactionType) {
                TransactionType.EXPENSE, TransactionType.RECEIVABLE_WRITEOFF, TransactionType.PAYABLE_RECOGNITION,
                TransactionType.SUBSCRIPTION_PAYMENT ->
                    transaction.categoryId to transaction.amount.centavos
                TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT, TransactionType.TRANSFER ->
                    transaction.categoryId to transaction.fee.centavos
                TransactionType.INTEREST_CREDIT -> transaction.secondaryCategoryId to transaction.fee.centavos
                else -> null to 0L
            }
            if (categoryId == null || amount <= 0L) return@forEach

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
