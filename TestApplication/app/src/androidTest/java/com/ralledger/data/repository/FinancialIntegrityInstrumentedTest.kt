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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialIntegrityInstrumentedTest {
    @Test
    fun concurrentCollectionsAndEncryptedRestore_preserveLedgerAndPaymentHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = inMemoryDatabase(context, "source")
        val destination = inMemoryDatabase(context, "destination")
        val password = "synthetic-ledger-passphrase"
        var encryptedBackup: ByteArray? = null

        try {
            source.accountDao().insertAccount(
                AccountEntity(
                    id = "wallet_test",
                    name = "Synthetic E-Wallet",
                    institution = "Test Institution",
                    accountType = AccountType.E_WALLET.name,
                    currency = "PHP",
                    openingBalanceCentavos = 0L,
                    iconName = "account_balance_wallet",
                    colorHex = "#1565C0",
                    createdAt = 1L,
                    isArchived = false,
                    includeInDashboardTotals = true,
                    lastReconciledAt = null,
                    description = "Synthetic account backup metadata"
                )
            )
            source.categoryDao().insertCategories(
                listOf(
                    CategoryEntity("cat_test_income", "Synthetic Income", CategoryType.INCOME.name, "payments", "#2E7D32", false),
                    CategoryEntity("cat_test_expense", "Synthetic Expense", CategoryType.EXPENSE.name, "receipt", "#C62828", false)
                )
            )

            val repository = RalLedgerRepository(source)
            repository.recordIncome("wallet_test", "cat_test_income", Money(12345L), "Synthetic test income")
            val receivable = repository.createReceivable(
                clientName = "Synthetic Client",
                amount = Money(10000L),
                dueDate = System.currentTimeMillis() + 86_400_000L,
                incomeCategoryId = "cat_test_income",
                description = "Synthetic invoice"
            )
            repository.collectReceivable(receivable.id, "wallet_test", Money(2500L), "Partial test payment")

            val concurrentResults = coroutineScope {
                listOf(
                    async { runCatching { repository.collectReceivable(receivable.id, "wallet_test", Money(4000L), "Concurrent test A") } },
                    async { runCatching { repository.collectReceivable(receivable.id, "wallet_test", Money(4000L), "Concurrent test B") } }
                ).awaitAll()
            }
            assertEquals("Only one concurrent collection may fit the remaining balance", 1, concurrentResults.count { it.isSuccess })
            assertEquals(1, concurrentResults.count { it.isFailure })

            val concurrentPayment = source.receivableDao().getAllPayments().single { it.amountCentavos == 4_000L }
            repository.reverseTransaction(concurrentPayment.journalEntryId)

            encryptedBackup = BackupRepository(source).createEncryptedBackup(password)
            val destinationBackupRepository = BackupRepository(destination)
            val preview = destinationBackupRepository.prepareRestore(encryptedBackup!!, password)
            assertTrue(preview.canRestore)
            destinationBackupRepository.restorePreparedBackup()

            assertEquals(source.accountDao().getAllAccounts(), destination.accountDao().getAllAccounts())
            assertEquals(source.categoryDao().getAllCategories(), destination.categoryDao().getAllCategories())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertEquals(source.journalDao().getAllJournalLines(), destination.journalDao().getAllJournalLines())
            assertEquals(source.receivableDao().getAllReceivables(), destination.receivableDao().getAllReceivables())
            assertEquals(source.receivableDao().getAllPayments(), destination.receivableDao().getAllPayments())
            assertEquals(source.journalDao().getBalanceAggregateForAccount("wallet_test"), destination.journalDao().getBalanceAggregateForAccount("wallet_test"))
            assertEquals(7500L, destination.receivableDao().getAllReceivables().single().outstandingAmountCentavos)
            assertTrue(destination.receivableDao().getAllPayments().single { it.id == concurrentPayment.id }.reversedAt != null)
        } finally {
            encryptedBackup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private fun inMemoryDatabase(context: Context, name: String): RalLedgerDatabase =
        Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java)
            .build()
}
