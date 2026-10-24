package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CustomReportManagementInstrumentedTest {
    @Test
    fun customReportCrudReferencesMergesAndEncryptedBackupAreSafe() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(account("report_wallet"))
            source.categoryDao().insertCategories(listOf(category("report_food", "Food"), category("report_meals", "Meals")))
            val repository = RalLedgerRepository(source)
            repository.recordExpense(
                accountId = "report_wallet",
                categoryId = "report_food",
                amount = Money(3_250),
                description = "Lunch",
                timestamp = LocalDate.of(2026, 10, 12).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            )
            val entryId = source.journalDao().getAllJournalEntries().single().id
            val originalTag = repository.createTransactionTag("Personal", "#1565C0")
            repository.setJournalEntryTags(entryId, setOf(originalTag))
            val replacementTag = repository.createTransactionTag("Everyday", "#2E7D32")
            val startDate = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endDate = LocalDate.of(2026, 10, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val id = repository.createCustomReport("October food", startDate, endDate, "report_wallet", "report_food", originalTag)

            assertTrue(runCatching {
                repository.createCustomReport("  OCTOBER   FOOD ", startDate, endDate, null, null, null)
            }.isFailure)
            assertTrue(runCatching { repository.deleteUnusedAccount("report_wallet") }.isFailure)
            assertTrue(runCatching { repository.deleteUnusedCategory("report_food") }.isFailure)
            assertTrue(runCatching { repository.deleteUnusedTransactionTag(originalTag) }.isFailure)

            repository.updateCustomReport(id, "October groceries", startDate, endDate, "report_wallet", "report_food", originalTag)
            assertEquals("October groceries", repository.customReportsFlow.first().single().name)
            repository.mergeCategory("report_food", "report_meals")
            repository.mergeTransactionTag(originalTag, replacementTag)
            val mergedReport = repository.customReportsFlow.first().single()
            assertEquals("report_meals", mergedReport.categoryId)
            assertEquals(replacementTag, mergedReport.tagId)

            repository.setCustomReportArchived(id, true)
            assertTrue(repository.customReportsFlow.first().single().isArchived)
            repository.setCustomReportArchived(id, false)
            assertFalse(repository.customReportsFlow.first().single().isArchived)

            backup = BackupRepository(source).createEncryptedBackup("custom-report-backup-password")
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, "custom-report-backup-password")
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.customReports)
            restore.restorePreparedBackup()
            assertEquals(repository.customReportsFlow.first(), RalLedgerRepository(destination).customReportsFlow.first())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertEquals(TransactionType.EXPENSE.name, destination.journalDao().getJournalEntryById(entryId)?.transactionType)

            repository.deleteCustomReport(id)
            assertTrue(repository.customReportsFlow.first().isEmpty())
            assertEquals(1, destination.customReportDao().getAll().size)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private fun account(id: String) = AccountEntity(
        id = id,
        name = "Report wallet",
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

    private fun category(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = "EXPENSE",
        iconName = "restaurant",
        colorHex = "#C62828",
        isDefault = false,
        parentCategoryId = null,
        isArchived = false
    )
}
