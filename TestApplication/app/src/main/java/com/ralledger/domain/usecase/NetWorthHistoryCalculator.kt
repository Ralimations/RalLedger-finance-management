package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class NetWorthSnapshot(
    val date: LocalDate,
    val label: String,
    val netWorth: Money,
    val liquidAssets: Money
)

/** Replays posted journal lines to produce period-end balance-sheet history. */
object NetWorthHistoryCalculator {
    fun calculate(
        journalEntries: List<JournalEntry>,
        accounts: List<Account>,
        receivableAccountIds: Set<String>,
        debtAccountIds: Set<String>,
        buckets: List<InsightsBucket>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<NetWorthSnapshot> {
        if (buckets.isEmpty()) return emptyList()

        val includedAccounts = accounts.filter { !it.isArchived && it.includeInDashboardTotals }
        val assetAccountIds = includedAccounts.filter { it.accountType.isAsset && !it.accountType.isReceivable }
            .mapTo(mutableSetOf()) { it.id } + receivableAccountIds
        val liabilityAccountIds = includedAccounts.filter { it.accountType.isLiability }
            .mapTo(mutableSetOf()) { it.id } + debtAccountIds
        val liquidAccountIds = includedAccounts.filter { it.accountType.isLiquid }.mapTo(mutableSetOf()) { it.id }
        val knownBalanceAccountIds = assetAccountIds + liabilityAccountIds
        val balances = mutableMapOf<String, Money>()
        val entries = journalEntries.sortedBy { it.timestamp }
        var entryIndex = 0

        return buckets.sortedBy { it.endExclusive }.map { bucket ->
            while (entryIndex < entries.size && entryDate(entries[entryIndex].timestamp, zone).isBefore(bucket.endExclusive)) {
                entries[entryIndex].lines.forEach { line ->
                    if (line.accountId !in knownBalanceAccountIds) return@forEach
                    val normalIncrease = when {
                        line.accountId in assetAccountIds -> line.lineType == LineType.DEBIT
                        else -> line.lineType == LineType.CREDIT
                    }
                    val current = balances[line.accountId] ?: Money.ZERO
                    balances[line.accountId] = if (normalIncrease) current + line.amount else current - line.amount
                }
                entryIndex += 1
            }

            val assets = assetAccountIds.fold(Money.ZERO) { sum, id -> sum + (balances[id] ?: Money.ZERO) }
            val liabilities = liabilityAccountIds.fold(Money.ZERO) { sum, id -> sum + (balances[id] ?: Money.ZERO) }
            val liquidAssets = liquidAccountIds.fold(Money.ZERO) { sum, id -> sum + (balances[id] ?: Money.ZERO) }
            NetWorthSnapshot(
                date = bucket.endExclusive.minusDays(1),
                label = bucket.label,
                netWorth = assets - liabilities,
                liquidAssets = liquidAssets
            )
        }
    }

    private fun entryDate(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
