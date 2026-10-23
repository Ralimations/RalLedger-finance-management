package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterestProductManagementInstrumentedTest {
    @Test
    fun timeDepositInterestLifecycleIsAtomicReversibleAndBackedUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(
                AccountEntity(RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID, "Opening Equity", "System", AccountType.EQUITY.name,
                    "PHP", 0L, "account_balance", "#9E9E9E", 1L, true, false, null)
            )
            source.categoryDao().insertCategoriesForRestore(listOf(
                CategoryEntity("interest-income", "Interest income", CategoryType.INCOME.name, "trending_up", "#2E7D32", false),
                CategoryEntity("tax-expense", "Withholding tax", CategoryType.EXPENSE.name, "receipt_long", "#C62828", false)
            ))
            val repository = RalLedgerRepository(source)
            val openedAt = System.currentTimeMillis() - 30L * 86_400_000L
            repository.createAccount(
                Account(
                    id = "term-deposit-account", name = "One-year deposit", institution = "Synthetic Bank",
                    accountType = AccountType.TIME_DEPOSIT, openingBalance = Money(2_000_000L), createdAt = openedAt
                )
            )
            val productId = repository.createInterestProduct(
                accountId = "term-deposit-account", type = InterestProductType.TIME_DEPOSIT,
                annualRateBasisPoints = 500, taxRateBasisPoints = 2_000, compounding = InterestCompounding.MONTHLY,
                openedAt = openedAt, maturityDate = openedAt + 365L * 86_400_000L,
                principal = Money(2_000_000L), notes = "Synthetic fixed-term deposit"
            )

            assertTrue(runCatching {
                repository.createInterestProduct(
                    "term-deposit-account", InterestProductType.TIME_DEPOSIT, 500, 2_000,
                    InterestCompounding.MONTHLY, openedAt, openedAt + 365L * 86_400_000L, Money(2_000_000L), "Duplicate"
                )
            }.isFailure)

            repository.recordInterestCredit(
                productId, Money(10_000L), Money(2_000L), "interest-income", "tax-expense",
                System.currentTimeMillis(), "Synthetic posted interest"
            )
            val credit = source.interestDao().getAllCredits().single()
            val entry = source.journalDao().getJournalEntryById(credit.journalEntryId)!!
            val lines = source.journalDao().getLinesForJournalEntry(entry.id)
            assertEquals("INTEREST_CREDIT", entry.transactionType)
            assertEquals(3, lines.size)
            assertEquals(Money(2_008_000L), repository.getAccountById("term-deposit-account")!!.currentLedgerBalance)
            val posted = repository.transactionsFlow.first().single { it.transactionType == TransactionType.INTEREST_CREDIT }
            assertEquals(Money(10_000L), posted.amount)
            assertEquals(Money(2_000L), posted.fee)
            assertEquals("tax-expense", posted.secondaryCategoryId)
            assertEquals("Withholding tax", posted.secondaryCategoryName)
            var summary = repository.dashboardSummaryFlow.first()
            assertEquals(Money.ZERO, summary.liquidCashAndBankBalances)
            assertEquals(Money(2_008_000L), summary.totalAssets)
            assertEquals(Money(10_000L), summary.monthlyIncomeReceived)
            assertEquals(Money(2_000L), summary.monthlyExpensesPaid)

            repository.updateInterestProduct(
                productId, 600, 1_500, InterestCompounding.QUARTERLY, openedAt,
                openedAt + 400L * 86_400_000L, Money(2_000_000L), "Updated future rate"
            )
            assertTrue(runCatching {
                repository.updateInterestProduct(productId, 600, 1_500, InterestCompounding.MONTHLY,
                    openedAt, openedAt + 400L * 86_400_000L, Money(2_100_000L), "Principal edit")
            }.isFailure)
            repository.setInterestProductArchived(productId, true)
            assertTrue(runCatching {
                repository.recordInterestCredit(productId, Money(100L), Money.ZERO, "interest-income", null, System.currentTimeMillis(), "Blocked")
            }.isFailure)
            repository.setInterestProductArchived(productId, false)
            assertTrue(runCatching { repository.deleteUnusedInterestProduct(productId) }.isFailure)

            backup = BackupRepository(source).createEncryptedBackup("interest-credit-test-password")
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, "interest-credit-test-password")
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.interestProducts)
            assertEquals(1, preview.interestCredits)
            restore.restorePreparedBackup()
            assertEquals(source.interestDao().getAllProducts(), destination.interestDao().getAllProducts())
            assertEquals(source.interestDao().getAllCredits(), destination.interestDao().getAllCredits())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())

            repository.reverseTransaction(entry.id)
            assertFalse(source.interestDao().getAllCredits().single { it.journalEntryId == entry.id }.reversedAt == null)
            assertEquals(Money(2_000_000L), repository.getAccountById("term-deposit-account")!!.currentLedgerBalance)
            summary = repository.dashboardSummaryFlow.first()
            assertEquals(Money.ZERO, summary.monthlyIncomeReceived)
            assertEquals(Money.ZERO, summary.monthlyExpensesPaid)
            assertTrue(repository.transactionsFlow.first().single { it.journalEntryId == entry.id }.isReversed)

            repository.recordInterestCredit(
                productId, Money(1_000L), Money(1_000L), "interest-income", "tax-expense",
                System.currentTimeMillis(), "Fully withheld synthetic interest"
            )
            val fullyWithheld = source.interestDao().getAllCredits().single { it.notes == "Fully withheld synthetic interest" }
            val fullyWithheldLines = source.journalDao().getLinesForJournalEntry(fullyWithheld.journalEntryId)
            assertEquals(2, fullyWithheldLines.size)
            assertEquals(Money(2_000_000L), repository.getAccountById("term-deposit-account")!!.currentLedgerBalance)
            repository.reverseTransaction(fullyWithheld.journalEntryId)
            assertEquals(Money(2_000_000L), repository.getAccountById("term-deposit-account")!!.currentLedgerBalance)

            assertTrue(repository.setAccountArchived("term-deposit-account", true))
            assertTrue(source.interestDao().getProductById(productId)!!.isArchived)
            val archivedBackup = BackupRepository(source).createEncryptedBackup("interest-credit-test-password")
            try {
                assertTrue(BackupRepository(destination).prepareRestore(archivedBackup, "interest-credit-test-password").canRestore)
            } finally {
                archivedBackup.fill(0)
            }

            val unusedAccount = AccountEntity("unused-savings", "Unused savings", "", AccountType.DIGITAL_BANK.name,
                "PHP", 0L, "account_balance", "#1565C0", 1L, false, true, null)
            source.accountDao().insertAccount(unusedAccount)
            assertTrue(runCatching {
                repository.createInterestProduct(
                    "unused-savings", InterestProductType.SAVINGS_ACCOUNT, 300, 0,
                    InterestCompounding.SIMPLE, System.currentTimeMillis() + 86_400_000L, null, null, "Future opening date"
                )
            }.isFailure)
            val unusedProduct = repository.createInterestProduct(
                "unused-savings", InterestProductType.SAVINGS_ACCOUNT, 300, 0,
                InterestCompounding.SIMPLE, System.currentTimeMillis(), null, null, "Unused profile"
            )
            repository.deleteUnusedInterestProduct(unusedProduct)
            assertNull(source.interestDao().getProductById(unusedProduct))
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }
}
