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
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivableManagementInstrumentedTest {
    @Test
    fun receivableEditCollectionWriteOffCancellationAndArchive_preserveJournalHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "receivable_wallet",
                    name = "Synthetic wallet",
                    institution = "Test",
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
            database.categoryDao().insertCategories(
                listOf(
                    CategoryEntity("receivable_income", "Freelance", CategoryType.INCOME.name, "work", "#2E7D32", false),
                    CategoryEntity("receivable_expense", "Bad debt", CategoryType.EXPENSE.name, "receipt", "#C62828", false)
                )
            )
            val repository = RalLedgerRepository(database)
            val dueDate = System.currentTimeMillis() - 3_600_000L
            val receivable = repository.createReceivable(
                clientName = "Original client",
                amount = Money(10_000L),
                dueDate = dueDate,
                incomeCategoryId = "receivable_income",
                description = "Original invoice"
            )
            val recognition = database.journalDao().getFirstEntryByTypeAndPrimaryAccount(
                TransactionType.RECEIVABLE_RECOGNITION.name,
                receivable.id
            )!!

            repository.collectReceivable(receivable.id, "receivable_wallet", Money(2_500L), "Synthetic partial payment")
            assertEquals(7_500L, database.receivableDao().getReceivableById(receivable.id)?.outstandingAmountCentavos)
            assertEquals(ReceivableStatus.OVERDUE.name, database.receivableDao().getReceivableById(receivable.id)?.status)
            val adjustedDueDate = System.currentTimeMillis() + 86_400_000L
            repository.updateReceivableDetails(receivable.id, "Updated client", adjustedDueDate, "Updated invoice note")
            var updated = database.receivableDao().getReceivableById(receivable.id)!!
            assertEquals("Updated client", updated.clientName)
            assertEquals(adjustedDueDate, updated.dueDate)
            assertEquals("Updated invoice note", updated.description)
            assertEquals(10_000L, updated.originalAmountCentavos)
            assertEquals(ReceivableStatus.PARTIALLY_PAID.name, updated.status)
            assertEquals("Original client", database.journalDao().getJournalEntryById(recognition.id)?.counterparty)
            assertTrue(runCatching { repository.cancelReceivable(receivable.id) }.isFailure)

            assertTrue(
                runCatching {
                    repository.writeOffReceivable(receivable.id, Money(1_500L), "receivable_income", "invalid category")
                }.isFailure
            )
            assertEquals(0, database.receivableDao().getAllWriteOffs().size)
            repository.writeOffReceivable(receivable.id, Money(1_500L), "receivable_expense", "Uncollectible remainder")
            updated = database.receivableDao().getReceivableById(receivable.id)!!
            assertEquals(6_000L, updated.outstandingAmountCentavos)
            assertEquals(ReceivableStatus.PARTIALLY_PAID.name, updated.status)
            val writeOff = database.receivableDao().getWriteOffsForReceivable(receivable.id).single()
            assertEquals(1_500L, writeOff.amountCentavos)
            val writeOffEntry = database.journalDao().getJournalEntryById(writeOff.journalEntryId)!!
            assertEquals(TransactionType.RECEIVABLE_WRITEOFF.name, writeOffEntry.transactionType)
            assertTrue(database.journalDao().getLinesForJournalEntry(writeOffEntry.id).let { lines ->
                lines.filter { it.lineType == "DEBIT" }.sumOf { it.amountCentavos } ==
                    lines.filter { it.lineType == "CREDIT" }.sumOf { it.amountCentavos }
            })

            repository.reverseTransaction(writeOff.journalEntryId)
            assertEquals(7_500L, database.receivableDao().getReceivableById(receivable.id)?.outstandingAmountCentavos)
            repository.reverseTransaction(database.receivableDao().getPaymentsForReceivable(receivable.id).single().journalEntryId)
            updated = database.receivableDao().getReceivableById(receivable.id)!!
            assertEquals(10_000L, updated.outstandingAmountCentavos)
            assertEquals(ReceivableStatus.CONFIRMED.name, updated.status)
            repository.cancelReceivable(receivable.id)
            updated = database.receivableDao().getReceivableById(receivable.id)!!
            assertEquals(ReceivableStatus.CANCELLED.name, updated.status)
            assertEquals(0L, updated.outstandingAmountCentavos)
            assertNotNull(database.journalDao().getReversalForEntry(recognition.id))
            repository.setReceivableArchived(receivable.id, true)
            assertTrue(database.receivableDao().getReceivableById(receivable.id)!!.isArchived)
            repository.setReceivableArchived(receivable.id, false)
            assertFalse(database.receivableDao().getReceivableById(receivable.id)!!.isArchived)

            val fullyWrittenOff = repository.createReceivable(
                clientName = "Uncollectible client",
                amount = Money(4_000L),
                dueDate = dueDate,
                incomeCategoryId = "receivable_income",
                description = "Synthetic bad debt"
            )
            repository.writeOffReceivable(fullyWrittenOff.id, Money(4_000L), "receivable_expense", "Synthetic full write-off")
            val closed = database.receivableDao().getReceivableById(fullyWrittenOff.id)!!
            assertEquals(ReceivableStatus.WRITTEN_OFF.name, closed.status)
            assertEquals(0L, closed.outstandingAmountCentavos)
            repository.setReceivableArchived(fullyWrittenOff.id, true)
            assertTrue(database.receivableDao().getReceivableById(fullyWrittenOff.id)!!.isArchived)
        } finally {
            database.close()
        }
    }
}
