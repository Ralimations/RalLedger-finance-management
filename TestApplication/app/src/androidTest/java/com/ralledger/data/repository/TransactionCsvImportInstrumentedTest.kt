package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.importing.TransactionCsvImporter
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionCsvImportInstrumentedTest {
    @Test
    fun mappedImportPostsBalancedRowsAndSkipsDuplicatesWithoutRewritingHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "csv_wallet",
                    name = "Daily wallet",
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
            database.categoryDao().insertCategories(
                listOf(
                    category("csv_food", "Food", CategoryType.EXPENSE),
                    category("csv_salary", "Salary", CategoryType.INCOME)
                )
            )
            val repository = RalLedgerRepository(database)
            val document = TransactionCsvImporter.parse(
                "Date,Type,Amount,Account,Category,Description,Counterparty\n" +
                    "2026-05-10,Expense,12.50,Daily wallet,Food,Lunch,Cafe\n" +
                    "2026-05-10,Expense,12.50,Daily wallet,Food,Lunch,Cafe\n" +
                    "2026-05-11,Income,250.00,Daily wallet,Salary,Pay,Employer\n"
            )
            val preview = TransactionCsvImporter.preview(
                document,
                TransactionCsvImporter.suggestMapping(document.headers),
                repository.accountsFlow.first(),
                repository.categoriesFlow.first(),
                repository.transactionTagsFlow.first(),
                repository.transactionsFlow.first()
            )
            assertEquals(3, preview.importableCount)
            assertEquals(1, preview.sameFileDuplicateCount)
            assertEquals(0, preview.errorCount)

            val result = repository.importCsvTransactions(preview.transactions)
            assertEquals(3, result.imported)
            assertEquals(0, result.duplicates)
            assertTrue(result.failedRows.isEmpty())

            val entries = database.journalDao().getAllJournalEntries()
            assertEquals(3, entries.size)
            entries.forEach { entry ->
                val lines = database.journalDao().getLinesForJournalEntry(entry.id).map { it.toDomainModel() }
                assertTrue(
                    JournalEntry(entry.id, entry.timestamp, entry.description, lines).isBalanced()
                )
            }
            assertEquals(22_500L, database.journalDao().getBalanceAggregateForAccount("csv_wallet")
                .sumOf { if (it.lineType == "DEBIT") it.totalCentavos else -it.totalCentavos })

            val caseVariantDuplicates = preview.transactions.map { transaction ->
                transaction.copy(
                    description = transaction.description.uppercase(),
                    counterparty = transaction.counterparty.lowercase()
                )
            }
            val duplicateResult = repository.importCsvTransactions(caseVariantDuplicates)
            assertEquals(0, duplicateResult.imported)
            assertEquals(3, duplicateResult.duplicates)
            assertTrue(duplicateResult.failedRows.isEmpty())
            assertEquals(3, database.journalDao().getAllJournalEntries().size)
            assertEquals(3, repository.transactionsFlow.first().size)
        } finally {
            database.close()
        }
    }

    private fun category(id: String, name: String, type: CategoryType) = CategoryEntity(
        id = id,
        name = name,
        type = type.name,
        iconName = "label",
        colorHex = "#1565C0",
        isDefault = false,
        parentCategoryId = null,
        isArchived = false
    )
}
