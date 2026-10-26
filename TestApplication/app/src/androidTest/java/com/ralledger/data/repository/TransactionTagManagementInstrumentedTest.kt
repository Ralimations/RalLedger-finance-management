package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionTagManagementInstrumentedTest {
    @Test
    fun labelLifecycle_preservesJournalAndProtectsAssignments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            insertWallet(database)
            val repository = RalLedgerRepository(database)
            repository.createCategory(Category("tag_expense", "Food", CategoryType.EXPENSE))
            repository.recordExpense("tag_wallet", "tag_expense", Money(1_250L), "Lunch")
            val originalEntry = database.journalDao().getAllJournalEntries().single()
            val originalLines = database.journalDao().getLinesForJournalEntry(originalEntry.id)

            val groceriesId = repository.createTransactionTag("Groceries", "#1565C0")
            val homeId = repository.createTransactionTag("Home", "#008577")
            assertTrue(runCatching { repository.createTransactionTag(" groceries ", "#D32F2F") }.isFailure)
            repository.setJournalEntryTags(originalEntry.id, setOf(groceriesId, homeId))

            val tagged = repository.transactionsFlow.first().single()
            assertEquals(setOf(groceriesId, homeId), tagged.tagIds)
            assertEquals(listOf("Groceries", "Home"), tagged.tagNames)
            assertTrue(TransactionFilter(tagId = groceriesId).matches(tagged))
            assertTrue(TransactionFilter(query = "home").matches(tagged))

            repository.updateTransactionTag(groceriesId, "Market", "#C62828")
            assertTrue(repository.transactionsFlow.first().single().tagNames.contains("Market"))
            assertTrue(runCatching { repository.deleteUnusedTransactionTag(groceriesId) }.isFailure)
            repository.mergeTransactionTag(homeId, groceriesId)
            val merged = repository.transactionsFlow.first().single()
            assertEquals(setOf(groceriesId), merged.tagIds)
            assertEquals(listOf("Market"), merged.tagNames)
            assertEquals(originalEntry, database.journalDao().getJournalEntryById(originalEntry.id))
            assertEquals(originalLines, database.journalDao().getLinesForJournalEntry(originalEntry.id))

            repository.setTransactionTagArchived(groceriesId, true)
            repository.setJournalEntryTags(originalEntry.id, setOf(groceriesId))
            val entriesBeforeRejectedPost = database.journalDao().getAllJournalEntries().size
            assertTrue(
                runCatching {
                    repository.recordExpense(
                        "tag_wallet",
                        "tag_expense",
                        Money(500L),
                        "Dinner",
                        tagIds = setOf(groceriesId)
                    )
                }.isFailure
            )
            assertEquals(entriesBeforeRejectedPost, database.journalDao().getAllJournalEntries().size)
            repository.setTransactionTagArchived(groceriesId, false)
            repository.recordExpense(
                "tag_wallet",
                "tag_expense",
                Money(500L),
                "Dinner",
                tagIds = setOf(groceriesId)
            )
            val taggedDinner = repository.transactionsFlow.first().single { it.description == "Dinner" }
            assertEquals(setOf(groceriesId), taggedDinner.tagIds)
            assertEquals(listOf("Market"), taggedDinner.tagNames)
            assertFalse(taggedDinner.tagNames.isEmpty())
            assertTrue(runCatching { repository.deleteUnusedTransactionTag(groceriesId) }.isFailure)
        } finally {
            database.close()
        }
    }

    @Test
    fun encryptedBackup_restoresLabelsAndJournalAssignments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            insertWallet(source)
            val repository = RalLedgerRepository(source)
            repository.createCategory(Category("backup_tag_expense", "Food", CategoryType.EXPENSE))
            repository.recordExpense("tag_wallet", "backup_tag_expense", Money(2_500L), "Market run")
            val entryId = source.journalDao().getAllJournalEntries().single().id
            val tagId = repository.createTransactionTag("Household", "#00695C")
            repository.setJournalEntryTags(entryId, setOf(tagId))

            val password = "transaction-tag-backup-password"
            val bytes = BackupRepository(source).createEncryptedBackup(password)
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(bytes, password)
            assertTrue(preview.canRestore)
            assertEquals(1, preview.transactionTags)
            assertEquals(1, preview.taggedJournalEntries)
            restore.restorePreparedBackup()

            val restored = RalLedgerRepository(destination).transactionsFlow.first().single()
            assertEquals(setOf(tagId), restored.tagIds)
            assertEquals(listOf("Household"), restored.tagNames)
        } finally {
            source.close()
            destination.close()
        }
    }

    private suspend fun insertWallet(database: RalLedgerDatabase) {
        database.accountDao().insertAccount(
            AccountEntity(
                id = "tag_wallet",
                name = "Label test wallet",
                institution = "",
                accountType = AccountType.E_WALLET.name,
                currency = "PHP",
                openingBalanceCentavos = 0L,
                iconName = "account_balance_wallet",
                colorHex = "#1565C0",
                createdAt = 1L,
                isArchived = false,
                includeInDashboardTotals = true,
                lastReconciledAt = null
            )
        )
    }
}
