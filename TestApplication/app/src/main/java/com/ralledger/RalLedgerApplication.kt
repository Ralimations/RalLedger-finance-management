package com.ralledger

import android.app.Application
import com.ralledger.di.AppContainer
import com.ralledger.notifications.SubscriptionReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RalLedgerApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            combine(
                container.repository.subscriptionsFlow,
                container.financialPreferences.settings
            ) { subscriptions, settings -> subscriptions to settings }
                .collect { (subscriptions, settings) ->
                    SubscriptionReminderScheduler.sync(this@RalLedgerApplication, subscriptions, settings)
                }
        }
    }
}
