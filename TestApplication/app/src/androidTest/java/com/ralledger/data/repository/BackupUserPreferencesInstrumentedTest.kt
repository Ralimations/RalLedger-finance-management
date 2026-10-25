package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.ui.dashboard.DashboardWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupUserPreferencesInstrumentedTest {
    @Test
    fun durableRestoreJournalRoundTripsAndClears() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val journalStore = DataStoreBackupRestoreJournalStore(context)
        val journal = PendingBackupRestore(
            id = "restore-journal-round-trip",
            previousPreferences = BackupUserPreferences(themeMode = "LIGHT", themePalette = "INDIGO"),
            targetPreferences = BackupUserPreferences(themeMode = "DARK", themePalette = "SAGE")
        )

        try {
            journalStore.clear()
            journalStore.write(journal)
            assertEquals(journal, journalStore.read())
        } finally {
            journalStore.clear()
        }
    }

    @Test
    fun encryptedRestoreIncludesUserPreferencesAndKeepsAccountReferenceValid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = inMemoryDatabase(context)
        val destination = inMemoryDatabase(context)
        val settings = BackupUserPreferences(
            defaultTransactionAccountId = "wallet_backup",
            defaultInsightsPeriod = "WEEK",
            dateDisplayFormat = "DAY_MONTH_YEAR",
            transactionListDensity = "COMPACT",
            currencyDisplayFormat = "CODE",
            subscriptionRemindersEnabled = true,
            subscriptionReminderDaysBefore = 3,
            themeMode = "DARK",
            themePalette = "SAGE",
            dashboardWidgetOrder = listOf("MONTHLY_FLOW", "AVAILABLE_MONEY", "NET_WORTH", "RESERVED_SAVINGS", "MONEY_OWED", "SUBSCRIPTIONS", "RECENT_TRANSACTIONS"),
            visibleDashboardWidgets = listOf("AVAILABLE_MONEY", "MONTHLY_FLOW", "SUBSCRIPTIONS", "RECENT_TRANSACTIONS")
        )
        val sourcePreferences = MemoryBackupPreferencesStore(settings)
        val targetPreferences = MemoryBackupPreferencesStore(BackupUserPreferences())

        try {
            source.accountDao().insertAccount(
                AccountEntity(
                    id = "wallet_backup",
                    name = "Backup wallet",
                    institution = "Test institution",
                    accountType = AccountType.E_WALLET.name,
                    currency = "PHP",
                    openingBalanceCentavos = 0L,
                    iconName = "account_balance_wallet",
                    colorHex = "#1565C0",
                    createdAt = 1L,
                    isArchived = false,
                    includeInDashboardTotals = true,
                    lastReconciledAt = null,
                    description = "Synthetic backup account"
                )
            )

            val password = "user-preferences-backup-password"
            val encrypted = BackupRepository(source, sourcePreferences).createEncryptedBackup(password)
            val restore = BackupRepository(destination, targetPreferences, MemoryBackupRestoreJournalStore())
            val preview = restore.prepareRestore(encrypted, password)

            assertTrue(preview.canRestore)
            assertTrue(preview.userPreferencesIncluded)
            restore.restorePreparedBackup()
            assertEquals(settings, targetPreferences.value)
            assertEquals("wallet_backup", destination.accountDao().getAllAccounts().single().id)
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun olderBackupAddsRecentTransactionsWidgetWithoutChangingExistingOrder() {
        val previousWidgets = DashboardWidget.entries
            .filterNot { it == DashboardWidget.RECENT_TRANSACTIONS }
            .map { it.name }
        val previouslyVisible = listOf(DashboardWidget.AVAILABLE_MONEY.name, DashboardWidget.MONTHLY_FLOW.name)
        val oldBackupPreferences = BackupUserPreferences(
            dashboardWidgetOrder = previousWidgets,
            visibleDashboardWidgets = previouslyVisible
        ).toJson()

        val migrated = BackupUserPreferences.fromJson(oldBackupPreferences)

        assertEquals(previousWidgets + DashboardWidget.RECENT_TRANSACTIONS.name, migrated.dashboardWidgetOrder)
        assertEquals(previouslyVisible + DashboardWidget.RECENT_TRANSACTIONS.name, migrated.visibleDashboardWidgets)
        assertTrue(migrated.validationErrors(emptyList()).isEmpty())
    }

    @Test
    fun legacyBackupWithoutUserPreferencesLeavesCurrentSettingsAlone() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = inMemoryDatabase(context)
        val destination = inMemoryDatabase(context)
        val currentSettings = BackupUserPreferences(themeMode = "DARK", themePalette = "INDIGO")
        val targetPreferences = MemoryBackupPreferencesStore(currentSettings)

        try {
            val password = "legacy-backup-with-no-preferences"
            val encrypted = BackupRepository(source).createEncryptedBackup(password)
            val restore = BackupRepository(destination, targetPreferences)
            val preview = restore.prepareRestore(encrypted, password)

            assertTrue(preview.canRestore)
            assertFalse(preview.userPreferencesIncluded)
            restore.restorePreparedBackup()
            assertEquals(currentSettings, targetPreferences.value)
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun preferenceWriteFailureLeavesJournalAndRecoveryCompletesCommittedRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = inMemoryDatabase(context)
        val destination = inMemoryDatabase(context)
        val backupSettings = BackupUserPreferences(
            defaultTransactionAccountId = "source_wallet",
            themeMode = "DARK",
            themePalette = "SAGE"
        )
        val currentSettings = BackupUserPreferences(
            defaultTransactionAccountId = "destination_wallet",
            themeMode = "LIGHT",
            themePalette = "INDIGO"
        )
        val targetPreferences = FailTwiceBackupPreferencesStore(currentSettings)
        val journalStore = MemoryBackupRestoreJournalStore()

        try {
            source.accountDao().insertAccount(testAccount("source_wallet"))
            destination.accountDao().insertAccount(testAccount("destination_wallet"))

            val encrypted = BackupRepository(source, MemoryBackupPreferencesStore(backupSettings))
                .createEncryptedBackup("rollback-preferences-backup-password")
            val restore = BackupRepository(destination, targetPreferences, journalStore)
            val preview = restore.prepareRestore(encrypted, "rollback-preferences-backup-password")
            assertTrue(preview.canRestore)

            val failure = runCatching { restore.restorePreparedBackup() }.exceptionOrNull()
            assertEquals("Preference write failed", failure?.message)
            assertEquals("source_wallet", destination.accountDao().getAllAccounts().single().id)
            assertEquals(currentSettings, targetPreferences.value)
            assertTrue(journalStore.value != null)

            assertTrue(restore.recoverPendingRestore())
            assertEquals("source_wallet", destination.accountDao().getAllAccounts().single().id)
            assertEquals(backupSettings, targetPreferences.value)
            assertNull(journalStore.value)
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun recoveryRestoresPreviousPreferencesWhenDatabaseRestoreDidNotCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = inMemoryDatabase(context)
        val previous = BackupUserPreferences(themeMode = "LIGHT", themePalette = "INDIGO")
        val target = BackupUserPreferences(themeMode = "DARK", themePalette = "SAGE")
        val preferences = MemoryBackupPreferencesStore(target)
        val journalStore = MemoryBackupRestoreJournalStore().apply {
            value = PendingBackupRestore("restore-not-committed", previous, target)
        }

        try {
            val restore = BackupRepository(database, preferences, journalStore)
            assertTrue(restore.recoverPendingRestore())
            assertEquals(previous, preferences.value)
            assertNull(journalStore.value)
            assertNull(database.backupRestoreStateDao().getLastSuccessfulRestoreId())
        } finally {
            database.close()
        }
    }

    @Test
    fun recoveryAppliesTargetPreferencesWhenDatabaseRestoreCommitted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = inMemoryDatabase(context)
        val previous = BackupUserPreferences(themeMode = "LIGHT", themePalette = "INDIGO")
        val target = BackupUserPreferences(themeMode = "DARK", themePalette = "SAGE")
        val preferences = MemoryBackupPreferencesStore(previous)
        val journalStore = MemoryBackupRestoreJournalStore().apply {
            value = PendingBackupRestore("restore-committed", previous, target)
        }

        try {
            database.backupRestoreStateDao().recordSuccessfulRestore(
                com.ralledger.data.local.entity.BackupRestoreStateEntity(
                    lastSuccessfulRestoreId = "restore-committed"
                )
            )
            val restore = BackupRepository(database, preferences, journalStore)
            assertTrue(restore.recoverPendingRestore())
            assertEquals(target, preferences.value)
            assertNull(journalStore.value)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(context: android.content.Context) =
        Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()

    private fun testAccount(id: String) = AccountEntity(
        id = id,
        name = id,
        institution = "Test institution",
        accountType = AccountType.E_WALLET.name,
        currency = "PHP",
        openingBalanceCentavos = 0L,
        iconName = "account_balance_wallet",
        colorHex = "#1565C0",
        createdAt = 1L,
        isArchived = false,
        includeInDashboardTotals = true,
        lastReconciledAt = null,
        description = "Synthetic account"
    )

    private class MemoryBackupPreferencesStore(
        var value: BackupUserPreferences
    ) : BackupPreferencesStore {
        override suspend fun read(): BackupUserPreferences = value
        override suspend fun write(preferences: BackupUserPreferences) {
            value = preferences
        }
    }

    private class FailTwiceBackupPreferencesStore(
        var value: BackupUserPreferences
    ) : BackupPreferencesStore {
        private var failuresRemaining = 2

        override suspend fun read(): BackupUserPreferences = value

        override suspend fun write(preferences: BackupUserPreferences) {
            if (failuresRemaining > 0) {
                failuresRemaining--
                throw IllegalStateException("Preference write failed")
            }
            value = preferences
        }
    }

    private class MemoryBackupRestoreJournalStore : BackupRestoreJournalStore {
        var value: PendingBackupRestore? = null

        override suspend fun read(): PendingBackupRestore? = value
        override suspend fun write(journal: PendingBackupRestore) { value = journal }
        override suspend fun clear() { value = null }
    }
}
