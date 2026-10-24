package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.model.Money
import com.ralledger.domain.usecase.FinancialProjectCalculator
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
class FinancialProjectManagementInstrumentedTest {
    @Test
    fun projectLifecycleReportsMergesAndEncryptedBackupPreserveLinks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(account("project_wallet"))
            source.categoryDao().insertCategories(listOf(category("project_expense", "Materials")))
            val repository = RalLedgerRepository(source)
            val originalProjectId = repository.createFinancialProject(
                "Kitchen remodel", FinancialProjectKind.PERSONAL, "Cabinets and repairs", "#1565C0"
            )
            val destinationProjectId = repository.createFinancialProject(
                "Family home", FinancialProjectKind.PERSONAL, "Home costs", "#2E7D32"
            )
            assertTrue(runCatching {
                repository.createFinancialProject("  KITCHEN   REMODEL ", FinancialProjectKind.PERSONAL, "", "#000000")
            }.isFailure)

            repository.recordExpense(
                accountId = "project_wallet",
                categoryId = "project_expense",
                amount = Money(12_500),
                description = "Cabinet deposit",
                timestamp = LocalDate.of(2026, 10, 12).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            )
            val entryId = source.journalDao().getAllJournalEntries().single().id
            repository.setJournalEntryProject(entryId, originalProjectId)
            val reportStart = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val reportEnd = LocalDate.of(2026, 10, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val reportId = repository.createCustomReport(
                "Kitchen spend", reportStart, reportEnd, null, null, null, originalProjectId
            )

            assertEquals("Kitchen remodel", repository.financialProjectsFlow.first().first { it.id == originalProjectId }.name)
            assertEquals(originalProjectId, repository.transactionsFlow.first().single().projectId)
            assertTrue(runCatching { repository.deleteUnusedFinancialProject(originalProjectId) }.isFailure)

            repository.updateFinancialProject(
                originalProjectId, "Kitchen refresh", FinancialProjectKind.PERSONAL, "Updated scope", "#008577"
            )
            repository.setFinancialProjectArchived(originalProjectId, true)
            assertTrue(repository.financialProjectsFlow.first().first { it.id == originalProjectId }.isArchived)
            assertTrue(runCatching {
                repository.setJournalEntryProject(entryId, destinationProjectId)
            }.isSuccess)
            assertTrue(runCatching {
                repository.setJournalEntryProject(entryId, originalProjectId)
            }.isFailure)
            assertTrue(runCatching {
                repository.mergeFinancialProjects(originalProjectId, destinationProjectId)
            }.isSuccess)

            val mergedTransactions = repository.transactionsFlow.first()
            assertEquals(destinationProjectId, mergedTransactions.single().projectId)
            val mergedReport = repository.customReportsFlow.first().single()
            assertEquals(destinationProjectId, mergedReport.projectId)

            repository.reverseTransaction(entryId)
            val withReversal = repository.transactionsFlow.first()
            assertEquals(2, withReversal.size)
            assertTrue(withReversal.all { it.projectId == destinationProjectId })
            val project = repository.financialProjectsFlow.first().single { it.id == destinationProjectId }
            val result = FinancialProjectCalculator.calculate(project, withReversal, repository.categoriesFlow.first(), ZoneOffset.UTC)
            assertEquals(Money.ZERO, result.expenses)
            assertTrue(runCatching { repository.deleteUnusedFinancialProject(destinationProjectId) }.isFailure)

            repository.setFinancialProjectArchived(destinationProjectId, true)
            backup = BackupRepository(source).createEncryptedBackup("financial-project-backup-password")
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, "financial-project-backup-password")
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.financialProjects)
            assertEquals(1, preview.projectAssignments)
            assertEquals(1, preview.customReports)
            restore.restorePreparedBackup()

            val restoredRepository = RalLedgerRepository(destination)
            assertEquals(repository.financialProjectsFlow.first(), restoredRepository.financialProjectsFlow.first())
            assertEquals(repository.customReportsFlow.first(), restoredRepository.customReportsFlow.first())
            assertEquals(repository.transactionsFlow.first(), restoredRepository.transactionsFlow.first())

            repository.setJournalEntryProject(entryId, null)
            repository.updateCustomReport(reportId, "Kitchen spend", reportStart, reportEnd, null, null, null, null)
            repository.setFinancialProjectArchived(destinationProjectId, false)
            repository.deleteUnusedFinancialProject(destinationProjectId)
            assertFalse(repository.financialProjectsFlow.first().any { it.id == destinationProjectId })
            assertTrue(repository.customReportsFlow.first().single().projectId == null)
            assertEquals(1, restoredRepository.financialProjectsFlow.first().size)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private fun account(id: String) = AccountEntity(
        id = id,
        name = "Project wallet",
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
        iconName = "home",
        colorHex = "#C62828",
        isDefault = false,
        parentCategoryId = null,
        isArchived = false
    )
}
