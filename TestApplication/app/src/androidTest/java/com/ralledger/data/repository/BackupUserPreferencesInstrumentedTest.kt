package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupUserPreferencesInstrumentedTest {
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
            dashboardWidgetOrder = listOf("MONTHLY_FLOW", "AVAILABLE_MONEY", "NET_WORTH", "RESERVED_SAVINGS", "MONEY_OWED", "SUBSCRIPTIONS"),
            visibleDashboardWidgets = listOf("AVAILABLE_MONEY", "MONTHLY_FLOW", "SUBSCRIPTIONS")
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
            val restore = BackupRepository(destination, targetPreferences)
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
    fun preferenceWriteFailureRollsBackRecordsAndSettingsAndCanBeRetried() = runBlocking {
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
        val targetPreferences = FailOnceBackupPreferencesStore(currentSettings)

        try {
            source.accountDao().insertAccount(testAccount("source_wallet"))
            destination.accountDao().insertAccount(testAccount("destination_wallet"))

            val encrypted = BackupRepository(source, MemoryBackupPreferencesStore(backupSettings))
                .createEncryptedBackup("rollback-preferences-backup-password")
            val restore = BackupRepository(destination, targetPreferences)
            val preview = restore.prepareRestore(encrypted, "rollback-preferences-backup-password")
            assertTrue(preview.canRestore)

            val failure = runCatching { restore.restorePreparedBackup() }.exceptionOrNull()
            assertEquals("Preference write failed", failure?.message)
            assertEquals("destination_wallet", destination.accountDao().getAllAccounts().single().id)
            assertEquals(currentSettings, targetPreferences.value)

            restore.restorePreparedBackup()
            assertEquals("source_wallet", destination.accountDao().getAllAccounts().single().id)
            assertEquals(backupSettings, targetPreferences.value)
        } finally {
            source.close()
            destination.close()
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

    private class FailOnceBackupPreferencesStore(
        var value: BackupUserPreferences
    ) : BackupPreferencesStore {
        private var failNextWrite = true

        override suspend fun read(): BackupUserPreferences = value

        override suspend fun write(preferences: BackupUserPreferences) {
            value = preferences
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("Preference write failed")
            }
        }
    }
}
