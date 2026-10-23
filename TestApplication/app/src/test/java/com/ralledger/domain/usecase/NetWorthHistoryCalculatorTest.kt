package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.JournalLine
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NetWorthHistoryCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val bank = Account(id = "bank", name = "Bank", accountType = AccountType.TRADITIONAL_BANK)
    private val firstPeriod = InsightsBucket(
        start = LocalDate.of(2026, 9, 1), endExclusive = LocalDate.of(2026, 10, 1), label = "Sep"
    )
    private val secondPeriod = InsightsBucket(
        start = LocalDate.of(2026, 10, 1), endExclusive = LocalDate.of(2026, 11, 1), label = "Oct"
    )

    @Test
    fun replaysAssetsReceivablesAndDebtThroughEachPeriodEndIncludingReversals() {
        val entries = listOf(
            entry("opening", LocalDate.of(2026, 8, 1),
                line("opening-bank", "bank", LineType.DEBIT, 100_000),
                line("opening-receivable", "receivable", LineType.DEBIT, 50_000),
                line("opening-loan", "loan", LineType.CREDIT, 30_000),
                line("opening-equity", "equity", LineType.CREDIT, 120_000)
            ),
            entry("expense", LocalDate.of(2026, 10, 3),
                line("expense-category", "food-category", LineType.DEBIT, 20_000),
                line("expense-bank", "bank", LineType.CREDIT, 20_000)
            ),
            entry("collection", LocalDate.of(2026, 10, 4),
                line("collection-bank", "bank", LineType.DEBIT, 15_000),
                line("collection-receivable", "receivable", LineType.CREDIT, 15_000)
            ),
            entry("repayment", LocalDate.of(2026, 10, 5),
                line("repayment-loan", "loan", LineType.DEBIT, 10_000),
                line("repayment-bank", "bank", LineType.CREDIT, 10_000)
            ),
            entry("expense-reversal", LocalDate.of(2026, 10, 6),
                line("reversal-bank", "bank", LineType.DEBIT, 20_000),
                line("reversal-expense", "food-category", LineType.CREDIT, 20_000)
            )
        )

        val result = NetWorthHistoryCalculator.calculate(
            journalEntries = entries,
            accounts = listOf(bank),
            receivableAccountIds = setOf("receivable"),
            debtAccountIds = setOf("loan"),
            buckets = listOf(firstPeriod, secondPeriod),
            zone = zone
        )

        assertEquals(2, result.size)
        assertEquals(Money(120_000), result[0].netWorth)
        assertEquals(Money(100_000), result[0].liquidAssets)
        assertEquals(Money(120_000), result[1].netWorth)
        assertEquals(Money(105_000), result[1].liquidAssets)
        assertEquals(LocalDate.of(2026, 10, 31), result[1].date)
    }

    @Test
    fun ignoresArchivedAndExcludedAccountsAndSkipsUnknownLedgerLines() {
        val excluded = bank.copy(id = "excluded", includeInDashboardTotals = false)
        val archived = bank.copy(id = "archived", isArchived = true)
        val entries = listOf(
            entry("balances", LocalDate.of(2026, 9, 15),
                line("bank", "bank", LineType.DEBIT, 1_000),
                line("excluded", "excluded", LineType.DEBIT, 2_000),
                line("archived", "archived", LineType.DEBIT, 3_000),
                line("unknown", "missing-account", LineType.DEBIT, 99_000)
            )
        )

        val result = NetWorthHistoryCalculator.calculate(
            journalEntries = entries,
            accounts = listOf(bank, excluded, archived),
            receivableAccountIds = emptySet(),
            debtAccountIds = emptySet(),
            buckets = listOf(firstPeriod),
            zone = zone
        ).single()

        assertEquals(Money(1_000), result.netWorth)
        assertEquals(Money(1_000), result.liquidAssets)
    }

    private fun entry(id: String, date: LocalDate, vararg lines: JournalLine) = JournalEntry(
        id = id,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        description = id,
        lines = lines.map { it.copy(journalEntryId = id) }
    )

    private fun line(id: String, accountId: String, type: LineType, centavos: Long) = JournalLine(
        id = id,
        journalEntryId = "unused",
        accountId = accountId,
        lineType = type,
        amount = Money(centavos)
    )
}
