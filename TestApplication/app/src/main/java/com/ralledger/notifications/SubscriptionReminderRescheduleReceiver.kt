package com.ralledger.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ralledger.RalLedgerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubscriptionReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as RalLedgerApplication
                val subscriptions = app.container.repository.subscriptionsFlow.first()
                val settings = app.container.financialPreferences.settings.first()
                SubscriptionReminderScheduler.sync(context, subscriptions, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
