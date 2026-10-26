package com.ralledger.di

import android.content.Context
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.repository.DataStoreBackupPreferencesStore
import com.ralledger.data.repository.DataStoreBackupRestoreJournalStore
import com.ralledger.data.repository.BackupRepository
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.ui.theme.ThemePreferences
import com.ralledger.ui.dashboard.DashboardPreferences
import com.ralledger.ui.settings.FinancialPreferences
import com.ralledger.ui.settings.AppLockPreferences
import com.ralledger.ui.onboarding.WelcomePreferences

class AppContainer(context: Context, existingInstall: Boolean) {
    val database: RalLedgerDatabase = RalLedgerDatabase.getInstance(context)
    val repository: RalLedgerRepository = RalLedgerRepository(database)
    val themePreferences: ThemePreferences = ThemePreferences(context)
    val dashboardPreferences: DashboardPreferences = DashboardPreferences(context)
    val financialPreferences: FinancialPreferences = FinancialPreferences(context)
    val appLockPreferences: AppLockPreferences = AppLockPreferences(context)
    val welcomePreferences: WelcomePreferences = WelcomePreferences(context, existingInstall)
    val backupRestoreJournalStore = DataStoreBackupRestoreJournalStore(context)
    val backupRepository: BackupRepository = BackupRepository(
        database = database,
        userPreferencesStore = DataStoreBackupPreferencesStore(
            financial = financialPreferences,
            theme = themePreferences,
            dashboard = dashboardPreferences
        ),
        restoreJournalStore = backupRestoreJournalStore
    )
}
