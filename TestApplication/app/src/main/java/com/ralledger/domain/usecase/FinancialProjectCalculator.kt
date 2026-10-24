package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/** Calculates a project's journal-backed totals, including later reversals of its transactions. */
object FinancialProjectCalculator {
    fun calculate(
        project: FinancialProject,
        transactions: List<Transaction>,
        categories: List<Category>,
        zone: ZoneId = ZoneId.systemDefault()
    ): InsightsResult {
        val selectedSourceIds = transactions.asSequence()
            .filter { it.transactionType != TransactionType.REVERSAL }
            .filter { it.projectId == project.id }
            .map { it.journalEntryId }
            .toSet()
        val projectTransactions = transactions.filter { transaction ->
            if (transaction.transactionType == TransactionType.REVERSAL) {
                transaction.reversesEntryId?.let { it in selectedSourceIds } == true
            } else {
                transaction.journalEntryId in selectedSourceIds
            }
        }
        return InsightsCalculator.calculate(
            projectTransactions,
            categories,
            InsightsPeriod(InsightsPeriodType.ALL_TIME, LocalDate.now(zone)),
            zone
        )
    }
}
