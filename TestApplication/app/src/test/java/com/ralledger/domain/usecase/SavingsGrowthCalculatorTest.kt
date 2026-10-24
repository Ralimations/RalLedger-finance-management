package com.ralledger.domain.usecase

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.JournalLine
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SavingsGrowthCalculatorTest {
    @Test
    fun replaysActiveSavingsDepositAndInterestProfileBalancesWithoutCountingChecking() {
        val accounts = listOf(
            Account("savings", "Savings", accountType = AccountType.PHYSICAL_SAVINGS),
            Account("deposit", "Time deposit", accountType = AccountType.TIME_DEPOSIT),
            Account("interest-bank", "Interest bank", accountType = AccountType.TRADITIONAL_BANK, includeInDashboardTotals = false),
            Account("checking", "Checking", accountType = AccountType.TRADITIONAL_BANK),
            Account("archived", "Old savings", accountType = AccountType.PHYSICAL_SAVINGS, isArchived = true)
        )
        val products = listOf(
            InterestProduct(
                id = "product",
                accountId = "interest-bank",
                accountName = "Interest bank",
                accountBalance = Money.ZERO,
                type = InterestProductType.SAVINGS_ACCOUNT,
                annualRateBasisPoints = 250,
                taxRateBasisPoints = 0,
                compounding = InterestCompounding.MONTHLY,
                openedAt = 1,
                maturityDate = null,
                principal = null,
                notes = "",
                isArchived = false,
                createdAt = 1
            )
        )
        val entries = listOf(
            entry("savings-opening", LocalDate.of(2026, 1, 2), "savings", LineType.DEBIT, 50_000),
            entry("deposit-opening", LocalDate.of(2026, 1, 2), "deposit", LineType.DEBIT, 100_000),
            entry("bank-opening", LocalDate.of(2026, 1, 2), "interest-bank", LineType.DEBIT, 20_000),
            entry("checking-opening", LocalDate.of(2026, 1, 2), "checking", LineType.DEBIT, 40_000),
            entry("archived-opening", LocalDate.of(2026, 1, 2), "archived", LineType.DEBIT, 90_000),
            entry("savings-withdrawal", LocalDate.of(2026, 2, 15), "savings", LineType.CREDIT, 5_000),
            entry("interest-credit", LocalDate.of(2026, 2, 20), "interest-bank", LineType.DEBIT, 500)
        )
        val buckets = listOf(
            InsightsBucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "Jan"),
            InsightsBucket(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), "Feb")
        )

        val result = SavingsGrowthCalculator.calculate(entries, accounts, products, buckets, ZoneOffset.UTC)

        assertEquals(listOf("Jan", "Feb"), result.snapshots.map { it.label })
        assertEquals(listOf(Money(170_000), Money(165_500)), result.snapshots.map { it.balance })
        assertEquals(Money.ZERO, result.startingBalance)
        assertEquals(Money(165_500), result.endingBalance)
        assertEquals(Money(165_500), result.change)

        val february = SavingsGrowthCalculator.calculate(entries, accounts, products, buckets.drop(1), ZoneOffset.UTC)
        assertEquals(Money(170_000), february.startingBalance)
        assertEquals(Money(165_500), february.endingBalance)
        assertEquals(Money(-4_500), february.change)
    }

    private fun entry(id: String, date: LocalDate, assetAccountId: String, assetLineType: LineType, centavos: Long) =
        JournalEntry(
            id = id,
            timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            description = id,
            lines = listOf(
                JournalLine("$id-asset", id, assetAccountId, assetLineType, Money(centavos)),
                JournalLine(
                    "$id-counterpart",
                    id,
                    "counterpart",
                    if (assetLineType == LineType.DEBIT) LineType.CREDIT else LineType.DEBIT,
                    Money(centavos)
                )
            )
        )
}
