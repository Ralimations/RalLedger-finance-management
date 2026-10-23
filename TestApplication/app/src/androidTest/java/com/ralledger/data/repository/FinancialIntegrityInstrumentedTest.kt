package com.ralledger.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.local.entity.SubscriptionEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.BillingFrequency
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
                    CategoryEntity("cat_test_expense", "Synthetic Expense", CategoryType.EXPENSE.name, "receipt", "#C62828", false),
                    CategoryEntity("cat_test_sub_expense", "Synthetic Subcategory", CategoryType.EXPENSE.name, "receipt", "#C62828", false, "cat_test_expense")
                )
            )

            val repository = RalLedgerRepository(source)
            repository.createBudget("cat_test_expense", 2026, 10, Money(50_000L))
            source.subscriptionDao().insertSubscription(
                SubscriptionEntity(
                    id = "archived_subscription_test",
                    name = "Archived synthetic plan",
                    merchant = "Test merchant",
                    billingAmountCentavos = 1_299L,
                    billingFrequency = BillingFrequency.MONTHLY.name,
                    assignedAccountId = "wallet_test",
                    nextBillingDate = 1_800_000_000_000L,
                    lastPaymentDate = 1_799_000_000_000L,
                    status = "CANCELLED",
                    cancellationDate = 1_799_500_000_000L,
                    notes = "Preserve archived subscription metadata",
                    isArchived = true
                )
            )
            repository.recordIncome("wallet_test", "cat_test_income", Money(12345L), "Synthetic test income")
            val receivable = repository.createReceivable(
                clientName = "Synthetic Client",
                amount = Money(10000L),
                dueDate = System.currentTimeMillis() + 86_400_000L,
                incomeCategoryId = "cat_test_income",
                description = "Synthetic invoice"
            )
            repository.collectReceivable(receivable.id, "wallet_test", Money(2500L), "Partial test payment")

            val writtenOffReceivable = repository.createReceivable(
                clientName = "Synthetic uncollectible client",
                amount = Money(4_000L),
                dueDate = System.currentTimeMillis() + 86_400_000L,
                incomeCategoryId = "cat_test_income",
                description = "Synthetic write-off backup record"
            )
            repository.writeOffReceivable(
                writtenOffReceivable.id,
                Money(4_000L),
                "cat_test_expense",
                "Synthetic backup write-off"
            )
            repository.setReceivableArchived(writtenOffReceivable.id, true)

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
            assertEquals(1, preview.budgets)
            assertEquals(1, preview.writeOffs)
            destinationBackupRepository.restorePreparedBackup()

            assertEquals(source.accountDao().getAllAccounts(), destination.accountDao().getAllAccounts())
            assertEquals(source.categoryDao().getAllCategories(), destination.categoryDao().getAllCategories())
            assertEquals(source.budgetDao().getAllBudgets(), destination.budgetDao().getAllBudgets())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertEquals(source.journalDao().getAllJournalLines(), destination.journalDao().getAllJournalLines())
            assertEquals(source.receivableDao().getAllReceivables(), destination.receivableDao().getAllReceivables())
            assertEquals(source.receivableDao().getAllPayments(), destination.receivableDao().getAllPayments())
            assertEquals(source.receivableDao().getAllWriteOffs(), destination.receivableDao().getAllWriteOffs())
            assertEquals(source.receivableDao().getAllReceivables(), destination.receivableDao().getAllReceivables())
            assertTrue(destination.receivableDao().getReceivableById(writtenOffReceivable.id)!!.isArchived)
            assertEquals(source.subscriptionDao().getAllSubscriptions(), destination.subscriptionDao().getAllSubscriptions())
            assertTrue(destination.subscriptionDao().getAllSubscriptions().single().isArchived)
            assertEquals(source.journalDao().getBalanceAggregateForAccount("wallet_test"), destination.journalDao().getBalanceAggregateForAccount("wallet_test"))
            assertEquals(7500L, destination.receivableDao().getReceivableById(receivable.id)?.outstandingAmountCentavos)
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
