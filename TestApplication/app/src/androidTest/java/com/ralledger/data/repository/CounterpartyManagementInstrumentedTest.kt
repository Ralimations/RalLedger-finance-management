package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.CounterpartyKind
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterpartyManagementInstrumentedTest {
    @Test
    fun counterpartiesCanBeEditedArchivedMergedAndRestoredWithoutChangingLedgerHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backupBytes: ByteArray? = null
        try {
            source.categoryDao().insertCategories(
                listOf(
                    CategoryEntity("income_counterparty", "Client income", CategoryType.INCOME.name, "work", "#1565C0", false),
                    CategoryEntity("expense_counterparty", "Fees", CategoryType.EXPENSE.name, "receipt", "#C62828", false)
                )
            )
            val repository = RalLedgerRepository(source)
            val clientId = repository.createCounterparty("  Acme   Media ", CounterpartyKind.CLIENT, "Original notes")
            val client = source.counterpartyDao().getById(clientId)!!
            assertEquals("Acme Media", client.name)
            assertEquals("acme media", client.normalizedName)
            assertTrue(
                runCatching { repository.createCounterparty("ACME MEDIA", CounterpartyKind.CLIENT) }.isFailure
            )

            val expectedDate = System.currentTimeMillis() + 86_400_000L
            val forecastId = repository.createExpectedIncome(
                "Project invoice", "Acme Media", Money(125_000L), expectedDate, "income_counterparty", clientId
            )
            val standaloneReceivable = repository.createReceivable(
                "Acme Media", Money(80_000L), expectedDate, "income_counterparty", "Invoice A", clientId
            )
            val convertedReceivableId = repository.convertExpectedIncomeToReceivable(forecastId)
            assertEquals(clientId, source.expectedIncomeDao().getById(forecastId)?.counterpartyId)
            assertEquals(clientId, source.receivableDao().getReceivableById(convertedReceivableId)?.counterpartyId)

            val originalJournal = source.journalDao().getAllJournalEntries().sortedBy { it.id }
            repository.updateCounterparty(clientId, "Acme Media LLC", CounterpartyKind.BOTH, "Updated notes")
            assertEquals("Acme Media LLC", source.counterpartyDao().getById(clientId)?.name)
            assertEquals("Updated notes", source.counterpartyDao().getById(clientId)?.notes)
            assertEquals(
                "Acme Media LLC",
                repository.receivablesFlow.first { rows -> rows.any { it.id == standaloneReceivable.id } }
                    .first { it.id == standaloneReceivable.id }.clientName
            )
            assertEquals(originalJournal, source.journalDao().getAllJournalEntries().sortedBy { it.id })

            repository.setCounterpartyArchived(clientId, true)
            assertTrue(
                runCatching {
                    repository.createExpectedIncome(
                        "Blocked assignment", "", Money(500L), expectedDate, "income_counterparty", clientId
                    )
                }.isFailure
            )
            assertTrue(runCatching { repository.deleteCounterparty(clientId) }.isFailure)

            val targetId = repository.createCounterparty("Acme Group", CounterpartyKind.BOTH, "Merged destination")
            repository.mergeCounterparties(clientId, targetId)
            assertNull(source.counterpartyDao().getById(clientId))
            assertEquals(targetId, source.expectedIncomeDao().getById(forecastId)?.counterpartyId)
            assertEquals(targetId, source.receivableDao().getReceivableById(standaloneReceivable.id)?.counterpartyId)
            assertEquals(targetId, source.receivableDao().getReceivableById(convertedReceivableId)?.counterpartyId)
            assertEquals(originalJournal, source.journalDao().getAllJournalEntries().sortedBy { it.id })
            assertEquals(3L, repository.getCounterpartyReferenceCount(targetId))
            assertTrue(runCatching { repository.deleteCounterparty(targetId) }.isFailure)

            repository.setCounterpartyArchived(targetId, true)
            assertTrue(source.counterpartyDao().getById(targetId)!!.isArchived)
            repository.setCounterpartyArchived(targetId, false)
            assertFalse(source.counterpartyDao().getById(targetId)!!.isArchived)

            val password = "counterparty-backup-test"
            backupBytes = BackupRepository(source).createEncryptedBackup(password)
            val destinationBackup = BackupRepository(destination)
            val preview = destinationBackup.prepareRestore(backupBytes!!, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.counterparties)
            assertEquals(1, preview.expectedIncome)
            assertEquals(2, preview.receivables)
            destinationBackup.restorePreparedBackup()

            assertEquals(source.counterpartyDao().getAll(), destination.counterpartyDao().getAll())
            assertEquals(source.expectedIncomeDao().getAllExpectedIncome(), destination.expectedIncomeDao().getAllExpectedIncome())
            assertEquals(source.receivableDao().getAllReceivables(), destination.receivableDao().getAllReceivables())
            assertEquals(originalJournal, destination.journalDao().getAllJournalEntries().sortedBy { it.id })
        } finally {
            backupBytes?.fill(0)
            source.close()
            destination.close()
        }
    }
}
