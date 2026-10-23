package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.ExpectedIncomeStatus
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpectedIncomeManagementInstrumentedTest {
    @Test
    fun expectedIncomeCanBeManagedConvertedOnceAndRestoredWithItsReceivable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var encryptedBackup: ByteArray? = null
        try {
            source.categoryDao().insertCategories(
                listOf(
                    CategoryEntity("income_old", "Contract work", CategoryType.INCOME.name, "work", "#2E7D32", false),
                    CategoryEntity("income_target", "Creative work", CategoryType.INCOME.name, "palette", "#1565C0", false),
                    CategoryEntity("income_final", "Media work", CategoryType.INCOME.name, "movie", "#6A1B9A", false),
                    CategoryEntity("expense_test", "Supplies", CategoryType.EXPENSE.name, "receipt", "#C62828", false)
                )
            )
            val repository = RalLedgerRepository(source)
            val expectedDate = System.currentTimeMillis() + 7L * 86_400_000L
            assertTrue(
                runCatching {
                    repository.createExpectedIncome("Invalid", "", Money(100L), expectedDate, "expense_test")
                }.isFailure
            )
            assertTrue(source.expectedIncomeDao().getAllExpectedIncome().isEmpty())
            val id = repository.createExpectedIncome(
                description = "Video project invoice",
                counterparty = "Sample client",
                amount = Money(125_075L),
                expectedDate = expectedDate,
                incomeCategoryId = "income_old"
            )
            assertEquals(1, source.expectedIncomeDao().getAllExpectedIncome().size)
            assertTrue(runCatching { repository.deleteUnusedCategory("income_old") }.isFailure)

            repository.mergeCategory("income_old", "income_target")
            assertEquals("income_target", source.expectedIncomeDao().getById(id)?.incomeCategoryId)
            repository.updateExpectedIncome(
                id, "Updated project invoice", "Updated client", Money(130_025L), expectedDate,
                "income_target"
            )
            assertEquals("Updated project invoice", source.expectedIncomeDao().getById(id)?.description)
            assertEquals(130_025L, source.expectedIncomeDao().getById(id)?.amountCentavos)
            repository.cancelExpectedIncome(id)
            assertEquals(ExpectedIncomeStatus.CANCELLED.name, source.expectedIncomeDao().getById(id)?.status)
            repository.reactivateExpectedIncome(id)
            val disposableId = repository.createExpectedIncome(
                "Cancelled test forecast", "", Money(500L), expectedDate, "income_target"
            )
            repository.cancelExpectedIncome(disposableId)
            repository.deleteExpectedIncome(disposableId)
            assertTrue(source.expectedIncomeDao().getById(disposableId) == null)
            repository.setExpectedIncomeArchived(id, true)
            assertTrue(runCatching { repository.convertExpectedIncomeToReceivable(id) }.isFailure)
            repository.setExpectedIncomeArchived(id, false)

            val receivableId = repository.convertExpectedIncomeToReceivable(id)
            val expected = source.expectedIncomeDao().getById(id)!!
            val receivable = source.receivableDao().getReceivableById(receivableId)!!
            assertEquals(ExpectedIncomeStatus.CONVERTED.name, expected.status)
            assertEquals(receivableId, expected.convertedReceivableId)
            assertEquals("Updated client", receivable.clientName)
            assertEquals(expected.amountCentavos, receivable.originalAmountCentavos)
            assertEquals(expected.expectedDate, receivable.dueDate)
            assertEquals("income_target", receivable.linkedCategoryId)
            val journalEntry = source.journalDao().getFirstEntryByTypeAndPrimaryAccount(
                TransactionType.RECEIVABLE_RECOGNITION.name,
                receivableId
            )!!
            val lines = source.journalDao().getLinesForJournalEntry(journalEntry.id)
            assertEquals(
                lines.filter { it.lineType == "DEBIT" }.sumOf { it.amountCentavos },
                lines.filter { it.lineType == "CREDIT" }.sumOf { it.amountCentavos }
            )
            repository.mergeCategory("income_target", "income_final")
            assertEquals("income_final", source.expectedIncomeDao().getById(id)?.incomeCategoryId)
            assertEquals("income_final", source.receivableDao().getReceivableById(receivableId)?.linkedCategoryId)
            assertEquals("income_final", source.journalDao().getJournalEntryById(journalEntry.id)?.categoryId)
            assertTrue(runCatching { repository.convertExpectedIncomeToReceivable(id) }.isFailure)
            assertTrue(runCatching { repository.deleteExpectedIncome(id) }.isFailure)
            assertEquals(1, source.receivableDao().getAllReceivables().size)
            assertEquals(1, source.journalDao().getAllJournalEntries().size)
            repository.setExpectedIncomeArchived(id, true)

            val password = "expected-income-test-password"
            encryptedBackup = BackupRepository(source).createEncryptedBackup(password)
            val destinationBackup = BackupRepository(destination)
            val preview = destinationBackup.prepareRestore(encryptedBackup!!, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.expectedIncome)
            assertEquals(1, preview.receivables)
            destinationBackup.restorePreparedBackup()

            assertEquals(source.expectedIncomeDao().getAllExpectedIncome(), destination.expectedIncomeDao().getAllExpectedIncome())
            assertEquals(source.receivableDao().getAllReceivables(), destination.receivableDao().getAllReceivables())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertNotNull(destination.receivableDao().getReceivableById(receivableId))
            assertTrue(destination.expectedIncomeDao().getById(id)!!.isArchived)
            assertFalse(destination.expectedIncomeDao().getById(id)!!.convertedReceivableId.isNullOrBlank())
        } finally {
            encryptedBackup?.fill(0)
            source.close()
            destination.close()
        }
    }
}
