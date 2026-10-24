package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SavingsGrowthSnapshot(
    val date: LocalDate,
    val label: String,
    val balance: Money
)

data class SavingsGrowthReport(
    val snapshots: List<SavingsGrowthSnapshot>,
    val startingBalance: Money,
    val endingBalance: Money,
    val change: Money
)

/** Replays journal balances for active savings and time-deposit accounts at each period cutoff. */
object SavingsGrowthCalculator {
    fun calculate(
        journalEntries: List<JournalEntry>,
        accounts: List<Account>,
        interestProducts: List<InterestProduct>,
        buckets: List<InsightsBucket>,
        zone: ZoneId = ZoneId.systemDefault()
    ): SavingsGrowthReport {
        if (buckets.isEmpty()) return SavingsGrowthReport(emptyList(), Money.ZERO, Money.ZERO, Money.ZERO)

        val interestAccountIds = interestProducts.mapTo(mutableSetOf()) { it.accountId }
        val savingsAccountIds = accounts.asSequence()
            .filter { !it.isArchived && it.accountType.isAsset && !it.accountType.isReceivable }
            .filter {
                it.accountType == AccountType.PHYSICAL_SAVINGS ||
                    it.accountType == AccountType.TIME_DEPOSIT ||
                    it.id in interestAccountIds
            }
            .mapTo(mutableSetOf()) { it.id }
        if (savingsAccountIds.isEmpty()) return SavingsGrowthReport(emptyList(), Money.ZERO, Money.ZERO, Money.ZERO)

        val entries = journalEntries.sortedBy { it.timestamp }
        val balances = mutableMapOf<String, Money>()
        var entryIndex = 0
        val orderedBuckets = buckets.sortedBy { it.endExclusive }
        val firstDate = orderedBuckets.first().start

        while (entryIndex < entries.size && entryDate(entries[entryIndex].timestamp, zone).isBefore(firstDate)) {
            applyEntry(entries[entryIndex], savingsAccountIds, balances)
            entryIndex += 1
        }
        val startingBalance = totalBalance(savingsAccountIds, balances)

        val snapshots = orderedBuckets.map { bucket ->
            while (
                entryIndex < entries.size &&
                entryDate(entries[entryIndex].timestamp, zone).isBefore(bucket.endExclusive)
            ) {
                applyEntry(entries[entryIndex], savingsAccountIds, balances)
                entryIndex += 1
            }

            SavingsGrowthSnapshot(
                date = bucket.endExclusive.minusDays(1),
                label = bucket.label,
                balance = totalBalance(savingsAccountIds, balances)
            )
        }
        val endingBalance = snapshots.lastOrNull()?.balance ?: startingBalance
        return SavingsGrowthReport(snapshots, startingBalance, endingBalance, endingBalance - startingBalance)
    }

    private fun applyEntry(entry: JournalEntry, savingsAccountIds: Set<String>, balances: MutableMap<String, Money>) {
        entry.lines.forEach { line ->
            if (line.accountId !in savingsAccountIds) return@forEach
            val current = balances[line.accountId] ?: Money.ZERO
            balances[line.accountId] = if (line.lineType == LineType.DEBIT) current + line.amount else current - line.amount
        }
    }

    private fun totalBalance(accountIds: Set<String>, balances: Map<String, Money>): Money =
        accountIds.fold(Money.ZERO) { total, id -> total + (balances[id] ?: Money.ZERO) }

    private fun entryDate(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
