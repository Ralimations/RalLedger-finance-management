package com.ralledger.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionCorrectionInstrumentedTest {
    @Test
    fun correctionPostsReversalAndReplacementAtomicallyAndKeepsOriginal() = runBlocking {
        val database = inMemoryDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            database.accountDao().insertAccounts(listOf(account("wallet_a", "Wallet A"), account("wallet_b", "Wallet B")))
            database.categoryDao().insertCategories(
                listOf(
                    category("expense_food", "Food", CategoryType.EXPENSE),
                    category("expense_home", "Home", CategoryType.EXPENSE),
                    category("income_salary", "Salary", CategoryType.INCOME)
                )
            )
            val repository = RalLedgerRepository(database)
            repository.recordExpense(
                accountId = "wallet_a",
                categoryId = "expense_food",
                amount = Money(1_000L),
                description = "Coffee",
                counterparty = "Cafe",
                notes = "Original note",
                timestamp = 10_000L
            )
            val original = database.journalDao().getAllJournalEntries().single()
            val originalLines = database.journalDao().getLinesForJournalEntry(original.id)

            repository.correctTransaction(
                original.id,
                TransactionCorrection(
                    transactionType = TransactionType.EXPENSE,
                    timestamp = 20_000L,
                    amount = Money(2_500L),
                    accountId = "wallet_b",
                    categoryId = "expense_home",
                    counterparty = "Home store",
                    description = "Corrected household purchase",
                    notes = "Corrected note"
                )
            )

            val entries = database.journalDao().getAllJournalEntries()
            assertEquals(3, entries.size)
            assertEquals(original, database.journalDao().getJournalEntryById(original.id))
            assertEquals(originalLines, database.journalDao().getLinesForJournalEntry(original.id))
            val reversal = entries.single { it.reversesEntryId == original.id }
            val replacement = entries.single { it.id != original.id && it.id != reversal.id }
            assertEquals(TransactionType.REVERSAL.name, reversal.transactionType)
            assertEquals(TransactionType.EXPENSE.name, replacement.transactionType)
            assertEquals(20_000L, replacement.timestamp)
            assertEquals(2_500L, replacement.amountCentavos)
            assertEquals("wallet_b", replacement.primaryAccountId)
            assertEquals("expense_home", replacement.categoryId)
            assertEquals("Corrected household purchase", replacement.description)
            assertEquals("Home store", replacement.counterparty)
            assertEquals("Corrected note", replacement.notes)
            entries.forEach { entry ->
                val lines = database.journalDao().getLinesForJournalEntry(entry.id).map { it.toDomainModel() }
                assertTrue("Every saved journal entry must remain balanced", com.ralledger.domain.model.JournalEntry(
                    id = entry.id,
                    timestamp = entry.timestamp,
                    description = entry.description,
                    lines = lines
                ).isBalanced())
            }
            assertEquals(0L, accountBalance(database, "wallet_a"))
            assertEquals(-2_500L, accountBalance(database, "wallet_b"))

            val correctedView = repository.transactionsFlow.first().single { it.journalEntryId == original.id }
            assertTrue(correctedView.isReversed)
            val countBeforeRejectedCorrection = entries.size
            val invalidCorrection = TransactionCorrection(
                transactionType = TransactionType.EXPENSE,
                timestamp = 30_000L,
                amount = Money(5_000L),
                accountId = "wallet_b",
                categoryId = "income_salary",
                description = "Invalid category correction"
            )
            assertTrue(runCatching { repository.correctTransaction(replacement.id, invalidCorrection) }.isFailure)
            assertEquals(countBeforeRejectedCorrection, database.journalDao().getAllJournalEntries().size)
            assertFalse(database.journalDao().getReversalForEntry(replacement.id) != null)
        } finally {
            database.close()
        }
    }

    @Test
    fun incomeCorrectionCanChangeToExpenseAndKeepsOriginalJournal() = runBlocking {
        val database = inMemoryDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            seedAccountsAndCategories(database)
            val repository = RalLedgerRepository(database)
            repository.recordIncome("wallet_a", "income_salary", Money(1_000L), "Pay", timestamp = 10_000L)
            val original = database.journalDao().getAllJournalEntries().single()
            val originalLines = database.journalDao().getLinesForJournalEntry(original.id)

            repository.correctTransaction(
                original.id,
                TransactionCorrection(
                    transactionType = TransactionType.EXPENSE,
                    timestamp = 20_000L,
                    amount = Money(750L),
                    accountId = "wallet_b",
                    categoryId = "expense_food",
                    description = "Corrected purchase"
                )
            )

            val entries = database.journalDao().getAllJournalEntries()
            val reversal = entries.single { it.reversesEntryId == original.id }
            val replacement = entries.single { it.id != original.id && it.id != reversal.id }
            assertEquals(original, database.journalDao().getJournalEntryById(original.id))
            assertEquals(originalLines, database.journalDao().getLinesForJournalEntry(original.id))
            assertEquals(TransactionType.REVERSAL.name, reversal.transactionType)
            assertEquals(TransactionType.EXPENSE.name, replacement.transactionType)
            assertEquals("expense_food", replacement.categoryId)
            assertEquals(0L, accountBalance(database, "wallet_a"))
            assertEquals(-750L, accountBalance(database, "wallet_b"))
            entries.forEach { entry -> assertBalanced(database, entry.id) }

            val unsupported = TransactionCorrection(
                transactionType = TransactionType.INTEREST_CREDIT,
                timestamp = 30_000L,
                amount = Money(100L),
                accountId = "wallet_a",
                categoryId = "income_salary"
            )
            assertTrue(runCatching { repository.correctTransaction(replacement.id, unsupported) }.isFailure)
            assertEquals(null, database.journalDao().getReversalForEntry(replacement.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun expenseCorrectionCanChangeToTransferWithFee() = runBlocking {
        val database = inMemoryDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            seedAccountsAndCategories(database)
            val repository = RalLedgerRepository(database)
            repository.recordExpense("wallet_a", "expense_food", Money(1_000L), "Purchase", timestamp = 10_000L)
            val original = database.journalDao().getAllJournalEntries().single()

            repository.correctTransaction(
                original.id,
                TransactionCorrection(
                    transactionType = TransactionType.TRANSFER,
                    timestamp = 20_000L,
                    amount = Money(2_000L),
                    fee = Money(100L),
                    accountId = "wallet_a",
                    destinationAccountId = "wallet_b",
                    categoryId = "expense_home",
                    description = "Corrected transfer"
                )
            )

            val entries = database.journalDao().getAllJournalEntries()
            val reversal = entries.single { it.reversesEntryId == original.id }
            val replacement = entries.single { it.id != original.id && it.id != reversal.id }
            assertEquals(TransactionType.REVERSAL.name, reversal.transactionType)
            assertEquals(TransactionType.TRANSFER.name, replacement.transactionType)
            assertEquals(2_000L, replacement.amountCentavos)
            assertEquals(100L, replacement.feeCentavos)
            assertEquals("wallet_b", replacement.destinationAccountId)
            assertEquals("expense_home", replacement.categoryId)
            assertEquals(-2_100L, accountBalance(database, "wallet_a"))
            assertEquals(2_000L, accountBalance(database, "wallet_b"))
            entries.forEach { entry -> assertBalanced(database, entry.id) }
        } finally {
            database.close()
        }
    }

    @Test
    fun transferCorrectionCanChangeToIncome() = runBlocking {
        val database = inMemoryDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            seedAccountsAndCategories(database)
            val repository = RalLedgerRepository(database)
            repository.recordTransfer(
                "wallet_a", "wallet_b", Money(500L), description = "Move money", timestamp = 10_000L
            )
            val original = database.journalDao().getAllJournalEntries().single()

            repository.correctTransaction(
                original.id,
                TransactionCorrection(
                    transactionType = TransactionType.INCOME,
                    timestamp = 20_000L,
                    amount = Money(700L),
                    accountId = "wallet_a",
                    categoryId = "income_salary",
                    description = "Corrected income"
                )
            )

            val entries = database.journalDao().getAllJournalEntries()
            val reversal = entries.single { it.reversesEntryId == original.id }
            val replacement = entries.single { it.id != original.id && it.id != reversal.id }
            assertEquals(TransactionType.REVERSAL.name, reversal.transactionType)
            assertEquals(TransactionType.INCOME.name, replacement.transactionType)
            assertEquals("income_salary", replacement.categoryId)
            assertEquals(700L, accountBalance(database, "wallet_a"))
            assertEquals(0L, accountBalance(database, "wallet_b"))
            entries.forEach { entry -> assertBalanced(database, entry.id) }
        } finally {
            database.close()
        }
    }

    @Test
    fun linkedReceivablePostingsCannotUseGeneralTransactionCorrection() = runBlocking {
        val database = inMemoryDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            database.accountDao().insertAccount(account("wallet_a", "Wallet A"))
            database.categoryDao().insertCategory(category("income_salary", "Salary", CategoryType.INCOME))
            val repository = RalLedgerRepository(database)
            val receivable = repository.createReceivable(
                clientName = "Synthetic client",
                amount = Money(3_000L),
                dueDate = 40_000L,
                incomeCategoryId = "income_salary",
                description = "Synthetic receivable"
            )
            val recognition = database.journalDao().getAllJournalEntries().single()
            val correction = TransactionCorrection(
                transactionType = TransactionType.RECEIVABLE_RECOGNITION,
                timestamp = 50_000L,
                amount = Money(4_000L),
                accountId = "wallet_a",
                categoryId = "income_salary",
                description = "Invalid linked correction"
            )
            assertTrue(runCatching { repository.correctTransaction(recognition.id, correction) }.isFailure)
            assertEquals(recognition, database.journalDao().getJournalEntryById(recognition.id))
            assertEquals(1, database.journalDao().getAllJournalEntries().size)
            assertEquals(3_000L, database.receivableDao().getReceivableById(receivable.id)?.originalAmountCentavos)
            assertEquals(null, database.journalDao().getReversalForEntry(recognition.id))
        } finally {
            database.close()
        }
    }

    private fun account(id: String, name: String) = AccountEntity(
        id = id,
        name = name,
        institution = "Synthetic",
        accountType = AccountType.PHYSICAL_WALLET.name,
        currency = "PHP",
        openingBalanceCentavos = 0L,
        iconName = "wallet",
        colorHex = "#1565C0",
        createdAt = 1L,
        isArchived = false,
        includeInDashboardTotals = true,
        lastReconciledAt = null
    )

    private fun category(id: String, name: String, type: CategoryType) = CategoryEntity(
        id = id,
        name = name,
        type = type.name,
        iconName = "label",
        colorHex = "#1565C0",
        isDefault = false
    )

    private suspend fun seedAccountsAndCategories(database: RalLedgerDatabase) {
        database.accountDao().insertAccounts(listOf(account("wallet_a", "Wallet A"), account("wallet_b", "Wallet B")))
        database.categoryDao().insertCategories(
            listOf(
                category("expense_food", "Food", CategoryType.EXPENSE),
                category("expense_home", "Home", CategoryType.EXPENSE),
                category("income_salary", "Salary", CategoryType.INCOME)
            )
        )
    }

    private suspend fun assertBalanced(database: RalLedgerDatabase, entryId: String) {
        val entry = database.journalDao().getJournalEntryById(entryId) ?: error("Journal entry missing")
        val lines = database.journalDao().getLinesForJournalEntry(entryId).map { it.toDomainModel() }
        assertTrue(
            "Every saved journal entry must remain balanced",
            com.ralledger.domain.model.JournalEntry(entry.id, entry.timestamp, entry.description, lines).isBalanced()
        )
    }

    private suspend fun accountBalance(database: RalLedgerDatabase, accountId: String): Long =
        database.journalDao().getBalanceAggregateForAccount(accountId).sumOf { aggregate ->
            if (aggregate.lineType == "DEBIT") aggregate.totalCentavos else -aggregate.totalCentavos
        }

    private fun inMemoryDatabase(context: Context): RalLedgerDatabase =
        Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
}
