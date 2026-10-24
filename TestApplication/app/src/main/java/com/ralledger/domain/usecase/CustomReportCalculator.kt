package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CustomReport
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId

/** Runs a saved report against journal-backed transactions, retaining only matching events and their reversals. */
object CustomReportCalculator {
    fun calculate(
        report: CustomReport,
        transactions: List<Transaction>,
        categories: List<Category>,
        zone: ZoneId = ZoneId.systemDefault()
    ): InsightsResult {
        require(report.startDate > 0L && report.endDate >= report.startDate) { "Choose a valid report date range" }

        val selectedSourceIds = transactions.asSequence()
            .filter { it.transactionType != TransactionType.REVERSAL }
            .filter { report.accountId == null || it.accountId == report.accountId || it.destinationAccountId == report.accountId }
            .filter { report.categoryId == null || it.categoryId == report.categoryId || it.secondaryCategoryId == report.categoryId }
            .filter { report.tagId == null || report.tagId in it.tagIds }
            .map { it.journalEntryId }
            .toSet()
        val selectedTransactions = transactions.filter { transaction ->
            if (transaction.transactionType == TransactionType.REVERSAL) {
                transaction.reversesEntryId?.let { it in selectedSourceIds } == true
            } else {
                transaction.journalEntryId in selectedSourceIds
            }
        }
        val start = Instant.ofEpochMilli(report.startDate).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(report.endDate).atZone(zone).toLocalDate()
        val period = InsightsPeriod(
            type = InsightsPeriodType.CUSTOM,
            anchorDate = start,
            customRange = InsightsDateRange(start, end)
        )
        return InsightsCalculator.calculate(selectedTransactions, categories, period, zone)
    }
}
