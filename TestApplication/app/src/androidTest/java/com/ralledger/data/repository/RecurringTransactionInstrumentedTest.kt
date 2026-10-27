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
import com.ralledger.domain.model.RecurringTransactionStatus
import com.ralledger.domain.model.TransactionRepeatFrequency
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringTransactionInstrumentedTest {
    @Test
    fun editingScheduleChangesFutureTemplateAndPreservesPostedHistoryAndCadence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            seed(database)
            val repository = RalLedgerRepository(database)
            val anchor = Instant.parse("2026-10-01T01:30:00Z").toEpochMilli()
            repository.recordExpense(
                accountId = "wallet",
                categoryId = "food",
                amount = Money(15_000L),
                description = "Coffee",
                timestamp = anchor,
                submissionId = "recurring-edit-original",
                repeatFrequency = TransactionRepeatFrequency.MONTHLY
            )

            val original = database.recurringTransactionDao().getAll().single()
            assertEquals(false, original.remindersEnabled)
            val originalOccurrence = database.recurringTransactionDao().getOccurrences(original.id).single()
            repository.updateRecurringTransactionDetails(
                id = original.id,
                expectedUpdatedAt = original.updatedAt,
                amount = Money(18_500L),
                categoryId = "groceries",
                counterparty = "Market",
                description = "Weekly groceries",
                notes = "Use the household card",
                remindersEnabled = true
            )

            val updated = database.recurringTransactionDao().getById(original.id)!!
            assertEquals(original.id, updated.id)
            assertEquals(original.frequency, updated.frequency)
            assertEquals(original.timeZoneId, updated.timeZoneId)
            assertEquals(original.anchorTimestamp, updated.anchorTimestamp)
            assertEquals(original.nextOccurrenceIndex, updated.nextOccurrenceIndex)
            assertEquals(original.nextOccurrenceTimestamp, updated.nextOccurrenceTimestamp)
            assertEquals(original.endTimestamp, updated.endTimestamp)
            assertEquals(18_500L, updated.amountCentavos)
            assertEquals("groceries", updated.categoryId)
            assertEquals("Market", updated.counterparty)
            assertEquals("Weekly groceries", updated.description)
            assertEquals("Use the household card", updated.notes)
            assertEquals(true, updated.remindersEnabled)
            assertEquals(originalOccurrence, database.recurringTransactionDao().getOccurrences(original.id).single())

            val historicalEntry = database.journalDao().getJournalEntryById("recurring-edit-original")!!
            assertEquals(15_000L, historicalEntry.amountCentavos)
            assertEquals("food", historicalEntry.categoryId)
            assertEquals("Coffee", historicalEntry.description)

            val staleUpdate = runCatching {
                repository.updateRecurringTransactionDetails(
                    id = original.id,
                    expectedUpdatedAt = original.updatedAt,
                    amount = Money(20_000L),
                    categoryId = "food",
                    counterparty = "",
                    description = "Stale edit",
                    notes = ""
                )
            }
            assertTrue("A stale edit must be rejected", staleUpdate.isFailure)
            assertEquals(18_500L, database.recurringTransactionDao().getById(original.id)?.amountCentavos)
        } finally {
            database.close()
        }
    }

    @Test
    fun confirmedOccurrenceAdvancesOnceAndEncryptedBackupRestoresHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            seed(source)
            val repository = RalLedgerRepository(source)
            val anchor = Instant.parse("2026-10-01T01:30:00Z").toEpochMilli()
            repository.recordExpense(
                accountId = "wallet",
                categoryId = "food",
                amount = Money(15_000L),
                description = "Coffee",
                timestamp = anchor,
                submissionId = "repeat-first-entry",
                repeatFrequency = TransactionRepeatFrequency.MONTHLY
            )
            val schedule = source.recurringTransactionDao().getAll().single()
            assertEquals(1L, schedule.nextOccurrenceIndex)
            assertEquals(1L, source.recurringTransactionDao().countOccurrences(schedule.id))

            repository.recordExpense(
                accountId = schedule.accountId,
                categoryId = schedule.categoryId,
                amount = Money(18_500L),
                description = "Coffee with a different total",
                timestamp = schedule.nextOccurrenceTimestamp,
                submissionId = "${schedule.id}:1",
                recurringTransactionId = schedule.id,
                recurrenceOccurrenceIndex = 1L
            )
            repository.recordExpense(
                accountId = schedule.accountId,
                categoryId = schedule.categoryId,
                amount = Money(18_500L),
                description = "Coffee with a different total",
                timestamp = schedule.nextOccurrenceTimestamp,
                submissionId = "${schedule.id}:1",
                recurringTransactionId = schedule.id,
                recurrenceOccurrenceIndex = 1L
            )
            val advanced = source.recurringTransactionDao().getById(schedule.id)!!
            assertEquals(2L, advanced.nextOccurrenceIndex)
            assertEquals(2L, source.recurringTransactionDao().countOccurrences(schedule.id))
            assertEquals(18_500L, source.journalDao().getJournalEntryById("${schedule.id}:1")?.amountCentavos)

            repository.setRecurringTransactionStatus(schedule.id, RecurringTransactionStatus.PAUSED)
            assertEquals(RecurringTransactionStatus.PAUSED.name, source.recurringTransactionDao().getById(schedule.id)?.status)
            repository.setRecurringTransactionStatus(schedule.id, RecurringTransactionStatus.ACTIVE)

            val current = source.recurringTransactionDao().getById(schedule.id)!!
            repository.updateRecurringTransactionDetails(
                id = current.id,
                expectedUpdatedAt = current.updatedAt,
                amount = Money(current.amountCentavos),
                categoryId = current.categoryId,
                counterparty = current.counterparty,
                description = current.description,
                notes = current.notes,
                remindersEnabled = true
            )

            val password = "recurring-backup-test-password"
            val encrypted = BackupRepository(source).createEncryptedBackup(password)
            val backupRestore = BackupRepository(destination)
            val preview = backupRestore.prepareRestore(encrypted, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.recurringTransactions)
            assertEquals(2, preview.recurringOccurrences)
            backupRestore.restorePreparedBackup()

            val restored = destination.recurringTransactionDao().getAll().single()
            assertEquals(advanced.id, restored.id)
            assertEquals(2L, restored.nextOccurrenceIndex)
            assertTrue(restored.remindersEnabled)
            assertEquals(2L, destination.recurringTransactionDao().countOccurrences(restored.id))
            val secondPreview = BackupRepository(destination).prepareRestore(
                BackupRepository(destination).createEncryptedBackup(password), password
            )
            assertTrue(secondPreview.validationErrors.joinToString(), secondPreview.canRestore)
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun schema23MigrationDefaultsExistingRecurringRemindersOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "recurring-reminder-migration-${System.nanoTime()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE recurring_transactions (id TEXT NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO recurring_transactions (id) VALUES ('existing-schedule')")
            RalLedgerDatabase.MIGRATION_23_24.migrate(db)
            db.query("SELECT remindersEnabled FROM recurring_transactions WHERE id = 'existing-schedule'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun seed(database: RalLedgerDatabase) {
        database.accountDao().insertAccount(
            AccountEntity(
                id = "wallet",
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
        )
        database.categoryDao().insertCategory(
            CategoryEntity("food", "Food", CategoryType.EXPENSE.name, "restaurant", "#FF5722", false)
        )
        database.categoryDao().insertCategory(
            CategoryEntity("groceries", "Groceries", CategoryType.EXPENSE.name, "shopping_cart", "#4CAF50", false)
        )
    }
}
