package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.time.ZoneId

data class FinancialProjectPortfolioResult(
    val personal: InsightsResult,
    val business: InsightsResult,
    val unassigned: InsightsResult
)

/** Builds Personal, Business, and unassigned reports from project-linked journal activity. */
object FinancialProjectPortfolioCalculator {
    fun calculate(
        transactions: List<Transaction>,
        categories: List<Category>,
        projects: List<FinancialProject>,
        period: InsightsPeriod,
        zone: ZoneId = ZoneId.systemDefault()
    ): FinancialProjectPortfolioResult {
        val personalIds = projects.filter { it.kind == FinancialProjectKind.PERSONAL }.mapTo(mutableSetOf()) { it.id }
        val businessIds = projects.filter { it.kind == FinancialProjectKind.BUSINESS }.mapTo(mutableSetOf()) { it.id }

        return FinancialProjectPortfolioResult(
            personal = calculateScope(transactions, categories, period, zone, personalIds),
            business = calculateScope(transactions, categories, period, zone, businessIds),
            unassigned = calculateScope(
                transactions,
                categories,
                period,
                zone,
                sourceEntryIds = transactions.asSequence()
                    .filter { it.transactionType != TransactionType.REVERSAL && it.projectId == null }
                    .map { it.journalEntryId }
                    .toSet()
            )
        )
    }

    private fun calculateScope(
        transactions: List<Transaction>,
        categories: List<Category>,
        period: InsightsPeriod,
        zone: ZoneId,
        projectIds: Set<String> = emptySet(),
        sourceEntryIds: Set<String>? = null
    ): InsightsResult {
        val selectedSourceIds = sourceEntryIds ?: transactions.asSequence()
            .filter { it.transactionType != TransactionType.REVERSAL }
            .filter { it.projectId in projectIds }
            .map { it.journalEntryId }
            .toSet()

        val selected = transactions.filter { transaction ->
            if (transaction.transactionType == TransactionType.REVERSAL) {
                transaction.reversesEntryId?.let { it in selectedSourceIds } == true
            } else {
                transaction.journalEntryId in selectedSourceIds
            }
        }
        return InsightsCalculator.calculate(selected, categories, period, zone)
    }
}
