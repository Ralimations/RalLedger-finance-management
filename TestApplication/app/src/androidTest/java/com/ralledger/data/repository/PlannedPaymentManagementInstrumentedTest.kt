package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedPaymentManagementInstrumentedTest {
    @Test
    fun plannedPaymentPostsOnlyActualsPreservesReversalHistoryAndRestoresFromBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(account("planned_wallet"))
            source.categoryDao().insertCategories(
                listOf(
                    category("planned_old", "Old expenses"),
                    category("planned_food", "Food"),
                    category("planned_merged", "Home")
                )
            )
            val repository = RalLedgerRepository(source)
            val dueDate = System.currentTimeMillis() + 5L * 86_400_000L
            val planId = repository.createPlannedPayment(
                description = "Electric bill",
                counterparty = "Power company",
                amount = Money(10_000L),
                dueDate = dueDate,
                expenseCategoryId = "planned_old",
                notes = "Forecast only"
            )

            assertTrue(source.journalDao().getAllJournalEntries().isEmpty())
            assertTrue(runCatching { repository.deleteUnusedCategory("planned_old") }.isFailure)
            repository.mergeCategory("planned_old", "planned_merged")
            assertEquals("planned_merged", source.plannedPaymentDao().getById(planId)?.expenseCategoryId)
            repository.updatePlannedPayment(
                id = planId,
                description = "Updated electric bill",
                counterparty = "Power company",
                amount = Money(12_000L),
                dueDate = dueDate,
                expenseCategoryId = "planned_merged",
                notes = "Forecast revised"
            )
            assertEquals(12_000L, source.plannedPaymentDao().getById(planId)?.amountCentavos)

            val paymentDate = System.currentTimeMillis()
            val journalId = repository.recordPlannedPayment(planId, "planned_wallet", Money(11_250L), paymentDate, "Paid in app")
            val entry = source.journalDao().getJournalEntryById(journalId)!!
            assertEquals(TransactionType.EXPENSE.name, entry.transactionType)
            assertEquals(11_250L, entry.amountCentavos)
            assertEquals("planned_merged", entry.categoryId)
            val record = source.plannedPaymentDao().getRecordByJournalEntry(journalId)!!
            assertEquals(11_250L, record.amountCentavos)
            assertEquals(paymentDate, record.paymentDate)
            assertTrue(runCatching { repository.recordPlannedPayment(planId, "planned_wallet", Money(500L), paymentDate + 1L) }.isFailure)
            assertTrue(runCatching {
                repository.correctTransaction(
                    journalId,
                    com.ralledger.domain.model.TransactionCorrection(
                        transactionType = TransactionType.EXPENSE,
                        timestamp = paymentDate + 2L,
                        amount = Money(10_000L),
                        accountId = "planned_wallet",
                        categoryId = "planned_merged",
                        description = "Corrected"
                    )
                )
            }.isFailure)

            repository.reversePlannedPaymentRecord(record.id)
            assertTrue(source.plannedPaymentDao().getRecordById(record.id)!!.reversedAt != null)
            assertTrue(repository.plannedPaymentsFlow.first { plans -> plans.any { it.id == planId } }.single { it.id == planId }.activePayment == null)
            val secondJournalId = repository.recordPlannedPayment(planId, "planned_wallet", Money(11_250L), paymentDate + 3L, "Paid again")
            assertTrue(runCatching { repository.deletePlannedPayment(planId) }.isFailure)

            backup = BackupRepository(source).createEncryptedBackup("planned-payment-backup")
            val restored = BackupRepository(destination)
            val preview = restored.prepareRestore(backup!!, "planned-payment-backup")
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.plannedPayments)
            assertEquals(2, preview.plannedPaymentEvents)
            restored.restorePreparedBackup()
            assertEquals(source.plannedPaymentDao().getAll(), destination.plannedPaymentDao().getAll())
            assertEquals(source.plannedPaymentDao().getAllRecords(), destination.plannedPaymentDao().getAllRecords())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertFalse(destination.journalDao().getJournalEntryById(secondJournalId) == null)

            repository.reversePlannedPaymentRecord(source.plannedPaymentDao().getRecordByJournalEntry(secondJournalId)!!.id)
            repository.cancelPlannedPayment(planId)
            assertEquals("CANCELLED", source.plannedPaymentDao().getById(planId)?.status)
            repository.reactivatePlannedPayment(planId)
            repository.setPlannedPaymentArchived(planId, true)
            repository.setPlannedPaymentArchived(planId, false)
            assertFalse(source.plannedPaymentDao().getById(planId)!!.isArchived)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private fun account(id: String) = AccountEntity(
        id = id,
        name = "Wallet",
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
        type = CategoryType.EXPENSE.name,
        iconName = "receipt",
        colorHex = "#C62828",
        isDefault = false
    )
}
