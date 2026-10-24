package com.ralledger

import android.app.Application
import com.ralledger.di.AppContainer
import com.ralledger.notifications.SubscriptionReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        startStartupRecovery()
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
                container.backupRepository.recoverPendingRestore()
                _startupState.value = StartupState.Ready
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
            }
        }
    }
}
