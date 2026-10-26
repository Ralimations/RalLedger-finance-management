package com.ralledger

import android.app.Application
import android.appwidget.AppWidgetManager
import com.ralledger.di.AppContainer
import com.ralledger.notifications.SubscriptionReminderScheduler
import com.ralledger.ui.widgets.AvailableMoneyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

sealed interface StartupState {
    data object Recovering : StartupState
    data object Ready : StartupState
    data object RecoveryFailed : StartupState
}

class RalLedgerApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _startupState = MutableStateFlow<StartupState>(StartupState.Recovering)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()
    private var startupJob: Job? = null
    private var reminderJob: Job? = null
    private var widgetRefreshJob: Job? = null
    private var existingInstall = false

    override fun onCreate() {
        super.onCreate()
        existingInstall = hasExistingInstallState()
        container = AppContainer(this, existingInstall)
        startStartupRecovery()
    }

    private fun hasExistingInstallState(): Boolean {
        if (getDatabasePath("ralledger_db").exists()) return true
        val dataStoreDirectory = File(filesDir, "datastore")
        return listOf(
            "appearance_preferences.preferences_pb",
            "dashboard_preferences.preferences_pb",
            "financial_preferences.preferences_pb",
            "app_lock_preferences.preferences_pb",
            "backup_restore_journal.preferences_pb"
        ).any { File(dataStoreDirectory, it).exists() }
    }

    fun retryStartupRecovery() {
        if (_startupState.value == StartupState.Recovering) return
        startStartupRecovery()
    }

    @Synchronized
    private fun startStartupRecovery() {
        if (startupJob?.isActive == true) return
        _startupState.value = StartupState.Recovering
        startupJob = applicationScope.launch {
            try {
                container.welcomePreferences.markCompleteForExistingInstall()
                container.backupRepository.recoverPendingRestore()
                _startupState.value = StartupState.Ready
                startWidgetRefreshes()
                if (reminderJob == null) {
                    reminderJob = applicationScope.launch {
                        combine(
                            container.repository.subscriptionsFlow,
                            container.financialPreferences.settings
                        ) { subscriptions, settings -> subscriptions to settings }
                            .collect { (subscriptions, settings) ->
                                SubscriptionReminderScheduler.sync(this@RalLedgerApplication, subscriptions, settings)
                            }
                    }
                }
            } catch (error: Exception) {
                _startupState.value = StartupState.RecoveryFailed
                refreshAllWidgets(unavailable = true)
            }
        }
    }

    fun refreshAvailableMoneyWidgets(widgetIds: IntArray, onComplete: () -> Unit) {
        if (widgetIds.isEmpty()) {
            onComplete()
            return
        }
        applicationScope.launch {
            try {
                val state = startupState.filter { it != StartupState.Recovering }.first()
                if (state != StartupState.Ready) {
                    updateWidgets(widgetIds, unavailable = true)
                    return@launch
                }
                val (summary, settings, appLockEnabled) = combine(
                    container.repository.dashboardSummaryFlow,
                    container.financialPreferences.settings,
                    container.appLockPreferences.enabled
                ) { summary, settings, locked -> Triple(summary, settings, locked) }.first()
                updateWidgets(widgetIds, summary, settings, appLockEnabled)
            } catch (_: Exception) {
                updateWidgets(widgetIds, unavailable = true)
            } finally {
                onComplete()
            }
        }
    }

    private fun startWidgetRefreshes() {
        if (widgetRefreshJob?.isActive == true) return
        widgetRefreshJob = applicationScope.launch {
            combine(
                container.repository.dashboardSummaryFlow,
                container.financialPreferences.settings,
                container.appLockPreferences.enabled
            ) { summary, settings, locked -> Triple(summary, settings, locked) }
                .collect { (summary, settings, locked) ->
                    val ids = AvailableMoneyWidgetProvider.installedWidgetIds(this@RalLedgerApplication)
                    updateWidgets(ids, summary, settings, locked)
                }
        }
    }

    private fun refreshAllWidgets(unavailable: Boolean) {
        val ids = AvailableMoneyWidgetProvider.installedWidgetIds(this)
        updateWidgets(ids, unavailable = unavailable)
    }

    private fun updateWidgets(
        widgetIds: IntArray,
        summary: com.ralledger.domain.model.DashboardSummary? = null,
        settings: com.ralledger.ui.settings.FinancialSettings = com.ralledger.ui.settings.FinancialSettings(),
        appLockEnabled: Boolean = false,
        unavailable: Boolean = false
    ) {
        if (widgetIds.isEmpty()) return
        AvailableMoneyWidgetProvider.update(
            this,
            AppWidgetManager.getInstance(this),
            widgetIds,
            summary,
            settings,
            appLockEnabled,
            unavailable
        )
    }
}
