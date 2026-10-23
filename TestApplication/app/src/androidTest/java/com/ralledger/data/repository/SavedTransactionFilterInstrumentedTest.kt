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
import com.ralledger.domain.model.TransactionTypeFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedTransactionFilterInstrumentedTest {
    @Test
    fun savedFilterCrudAndEncryptedBackup_preserveJournal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            insertWallet(source)
            val repository = RalLedgerRepository(source)
            repository.createCategory(Category("saved_filter_food", "Food", CategoryType.EXPENSE))
            repository.recordExpense("saved_filter_wallet", "saved_filter_food", Money(1_250L), "Lunch")
            val entriesBefore = source.journalDao().getAllJournalEntries()
            val linesBefore = source.journalDao().getAllJournalLines()
            val criteria = TransactionFilter(
                query = "Lunch",
                type = TransactionTypeFilter.EXPENSE,
                accountId = "saved_filter_wallet",
                categoryId = "saved_filter_food",
                startInclusive = 1_000L,
                endExclusive = 2_000L
            )

            val id = repository.createSavedTransactionFilter("Lunch this month", criteria)
            assertEquals(criteria, repository.savedTransactionFiltersFlow.first().single().filter)
            assertTrue(runCatching { repository.createSavedTransactionFilter(" lunch THIS month ", criteria) }.isFailure)

            repository.renameSavedTransactionFilter(id, "Weekday lunch")
            repository.updateSavedTransactionFilterCriteria(id, criteria.copy(query = "Cafe"))
            val updated = repository.savedTransactionFiltersFlow.first().single()
            assertEquals("Weekday lunch", updated.name)
            assertEquals("Cafe", updated.filter.query)

            val password = "saved-filter-backup-password"
            val bytes = BackupRepository(source).createEncryptedBackup(password)
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(bytes, password)
            assertTrue(preview.canRestore)
            assertEquals(1, preview.savedTransactionFilters)
            restore.restorePreparedBackup()

            val restoredFilter = RalLedgerRepository(destination).savedTransactionFiltersFlow.first().single()
            assertEquals(updated, restoredFilter)
            assertEquals(entriesBefore, destination.journalDao().getAllJournalEntries())
            assertEquals(linesBefore, destination.journalDao().getAllJournalLines())

            repository.deleteSavedTransactionFilter(id)
            assertTrue(repository.savedTransactionFiltersFlow.first().isEmpty())
            assertEquals(entriesBefore, source.journalDao().getAllJournalEntries())
            assertEquals(linesBefore, source.journalDao().getAllJournalLines())
        } finally {
            source.close()
            destination.close()
        }
    }

    private suspend fun insertWallet(database: RalLedgerDatabase) {
        database.accountDao().insertAccount(
            AccountEntity(
                id = "saved_filter_wallet",
                name = "Filter test wallet",
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
