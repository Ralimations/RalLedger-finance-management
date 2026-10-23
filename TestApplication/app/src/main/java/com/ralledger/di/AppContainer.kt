package com.ralledger.di

import android.content.Context
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.repository.BackupRepository
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.ui.theme.ThemePreferences
import com.ralledger.ui.dashboard.DashboardPreferences

class AppContainer(context: Context) {
    val database: RalLedgerDatabase = RalLedgerDatabase.getInstance(context)
    val repository: RalLedgerRepository = RalLedgerRepository(database)
    val backupRepository: BackupRepository = BackupRepository(database)
    val themePreferences: ThemePreferences = ThemePreferences(context)
    val dashboardPreferences: DashboardPreferences = DashboardPreferences(context)
}
