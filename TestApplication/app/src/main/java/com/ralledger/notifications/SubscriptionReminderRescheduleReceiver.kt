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
        if (!isAllowedSubscriptionReminderRescheduleAction(intent.action)) return

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

internal fun isAllowedSubscriptionReminderRescheduleAction(action: String?): Boolean = when (action) {
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED -> true
    else -> false
}
