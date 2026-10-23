package com.ralledger.data.repository

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountManagementInstrumentedTest {
    @Test
    fun migrationFromSchemaTwo_preservesAccountAndAddsBlankDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "ralledger_schema2_migration_test.db"
        context.deleteDatabase(databaseName)
        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        try {
            createSchemaTwoTables(legacy)
            legacy.execSQL(
                """INSERT INTO accounts (id, name, institution, accountType, currency, openingBalanceCentavos,
                    iconName, colorHex, createdAt, isArchived, includeInDashboardTotals, lastReconciledAt)
                    VALUES ('legacy_account', 'Legacy Wallet', 'Test Bank', 'E_WALLET', 'PHP', 12500,
                    'account_balance_wallet', '#1565C0', 100, 0, 1, NULL)"""
            )
            legacy.version = 2
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, RalLedgerDatabase::class.java, databaseName)
            .addMigrations(RalLedgerDatabase.MIGRATION_1_2, RalLedgerDatabase.MIGRATION_2_3)
            .build()
        try {
            val account = kotlinx.coroutines.runBlocking { migrated.accountDao().getAccountById("legacy_account") }
            assertEquals("Legacy Wallet", account?.name)
            assertEquals(12_500L, account?.openingBalanceCentavos)
            assertEquals("", account?.description)
            assertEquals(3, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun accountMetadataAndLifecycle_preserveLedgerAndRestrictDeletionToUnusedAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            val repository = RalLedgerRepository(database)
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "account_used",
                    name = "Old name",
                    institution = "Old institution",
                    accountType = AccountType.E_WALLET.name,
                    currency = "PHP",
                    openingBalanceCentavos = 0L,
                    iconName = "account_balance_wallet",
                    colorHex = "#1565C0",
                    createdAt = 1L,
                    isArchived = false,
                    includeInDashboardTotals = true,
                    lastReconciledAt = null,
                    description = "Old description"
                )
            )
            database.categoryDao().insertCategory(
                CategoryEntity("cat_test_income", "Test income", CategoryType.INCOME.name, "payments", "#2E7D32", false)
            )
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "account_unused",
                    name = "Unused account",
                    institution = "",
                    accountType = AccountType.TRADITIONAL_BANK.name,
                    currency = "PHP",
                    openingBalanceCentavos = 0L,
                    iconName = "account_balance",
                    colorHex = "#1565C0",
                    createdAt = 2L,
                    isArchived = false,
                    includeInDashboardTotals = true,
                    lastReconciledAt = null
                )
            )

            repository.recordIncome("account_used", "cat_test_income", Money(12345L), "Test income")
            val balanceBeforeEdit = repository.getAccountById("account_used")!!.currentLedgerBalance
            val entriesBeforeEdit = database.journalDao().getAllJournalEntries()

            repository.updateAccountMetadata(
                id = "account_used",
                name = "Updated name",
                institution = "Updated institution",
                description = "Updated description",
                iconName = "savings",
                colorHex = "#00695C",
                includeInDashboardTotals = false
            )

            val updated = database.accountDao().getAccountById("account_used")!!
            assertEquals("Updated name", updated.name)
            assertEquals("Updated institution", updated.institution)
            assertEquals("Updated description", updated.description)
            assertEquals("savings", updated.iconName)
            assertEquals("#00695C", updated.colorHex)
            assertFalse(updated.includeInDashboardTotals)
            assertEquals(AccountType.E_WALLET.name, updated.accountType)
            assertEquals(0L, updated.openingBalanceCentavos)
            assertEquals(balanceBeforeEdit, repository.getAccountById("account_used")!!.currentLedgerBalance)
            assertEquals(entriesBeforeEdit, database.journalDao().getAllJournalEntries())

            assertTrue(runCatching { repository.deleteUnusedAccount("account_used") }.isFailure)
            assertTrue(database.accountDao().getAccountById("account_used") != null)

            repository.setAccountArchived("account_used", true)
            assertTrue(database.accountDao().getAccountById("account_used")!!.isArchived)
            repository.setAccountArchived("account_used", false)
            assertFalse(database.accountDao().getAccountById("account_used")!!.isArchived)

            repository.deleteUnusedAccount("account_unused")
            assertNull(database.accountDao().getAccountById("account_unused"))
            assertEquals(1, database.journalDao().getAllJournalEntries().size)
        } finally {
            database.close()
        }
    }

    private fun createSchemaTwoTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts (id TEXT NOT NULL, name TEXT NOT NULL, institution TEXT NOT NULL, accountType TEXT NOT NULL, currency TEXT NOT NULL, openingBalanceCentavos INTEGER NOT NULL, iconName TEXT NOT NULL, colorHex TEXT NOT NULL, createdAt INTEGER NOT NULL, isArchived INTEGER NOT NULL, includeInDashboardTotals INTEGER NOT NULL, lastReconciledAt INTEGER, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE journal_entries (id TEXT NOT NULL, timestamp INTEGER NOT NULL, description TEXT NOT NULL, transactionType TEXT NOT NULL, amountCentavos INTEGER NOT NULL, feeCentavos INTEGER NOT NULL, primaryAccountId TEXT NOT NULL, destinationAccountId TEXT, categoryId TEXT, counterparty TEXT NOT NULL, notes TEXT NOT NULL, reversesEntryId TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_journal_entries_reversesEntryId ON journal_entries(reversesEntryId)")
        db.execSQL("CREATE TABLE journal_lines (id TEXT NOT NULL, journalEntryId TEXT NOT NULL, accountId TEXT NOT NULL, lineType TEXT NOT NULL, amountCentavos INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_journal_lines_journalEntryId ON journal_lines(journalEntryId)")
        db.execSQL("CREATE INDEX index_journal_lines_accountId ON journal_lines(accountId)")
        db.execSQL("CREATE TABLE categories (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, iconName TEXT NOT NULL, colorHex TEXT NOT NULL, isDefault INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE receivables (id TEXT NOT NULL, clientName TEXT NOT NULL, originalAmountCentavos INTEGER NOT NULL, outstandingAmountCentavos INTEGER NOT NULL, recognitionDate INTEGER NOT NULL, dueDate INTEGER NOT NULL, linkedCategoryId TEXT, description TEXT NOT NULL, status TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE receivable_payments (id TEXT NOT NULL, receivableId TEXT NOT NULL, journalEntryId TEXT NOT NULL, amountCentavos INTEGER NOT NULL, destinationAccountId TEXT NOT NULL, paymentDate INTEGER NOT NULL, notes TEXT NOT NULL, reversedAt INTEGER, PRIMARY KEY(id), FOREIGN KEY(receivableId) REFERENCES receivables(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_receivable_payments_receivableId ON receivable_payments(receivableId)")
        db.execSQL("CREATE INDEX index_receivable_payments_journalEntryId ON receivable_payments(journalEntryId)")
        db.execSQL("CREATE TABLE subscriptions (id TEXT NOT NULL, name TEXT NOT NULL, merchant TEXT NOT NULL, billingAmountCentavos INTEGER NOT NULL, billingFrequency TEXT NOT NULL, assignedAccountId TEXT NOT NULL, nextBillingDate INTEGER NOT NULL, lastPaymentDate INTEGER, status TEXT NOT NULL, cancellationDate INTEGER, notes TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE reconciliations (id TEXT NOT NULL, accountId TEXT NOT NULL, timestamp INTEGER NOT NULL, recordedBalanceCentavos INTEGER NOT NULL, actualBalanceCentavos INTEGER NOT NULL, differenceCentavos INTEGER NOT NULL, adjustmentReason TEXT NOT NULL, journalEntryId TEXT, PRIMARY KEY(id))")
    }
}
