package com.ralledger.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ralledger.MainActivity
import com.ralledger.R
import com.ralledger.RalLedgerApplication
import com.ralledger.StartupState
import com.ralledger.domain.model.RecurringTransactionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecurringTransactionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(RecurringTransactionReminderScheduler.EXTRA_SCHEDULE_ID)
            ?: intent.data?.lastPathSegment
            ?: return
        val occurrenceIndex = intent.getLongExtra(RecurringTransactionReminderScheduler.EXTRA_OCCURRENCE_INDEX, -1L)
        val occurrenceTimestamp = intent.getLongExtra(RecurringTransactionReminderScheduler.EXTRA_OCCURRENCE_TIMESTAMP, -1L)
        if (occurrenceIndex <= 0L || occurrenceTimestamp <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!SubscriptionReminderScheduler.notificationsAllowed(context)) return@launch
                val app = context.applicationContext as RalLedgerApplication
                val startupState = app.startupState.first { it != StartupState.Recovering }
                if (startupState != StartupState.Ready) return@launch
                val schedule = app.container.repository.recurringTransactionsFlow.first()
                    .firstOrNull { it.id == scheduleId }
                    ?: return@launch
                if (!schedule.remindersEnabled ||
                    schedule.status != RecurringTransactionStatus.ACTIVE ||
                    schedule.nextOccurrenceIndex != occurrenceIndex ||
                    schedule.nextOccurrenceTimestamp != occurrenceTimestamp
                ) return@launch

                postNotification(context, scheduleId, occurrenceIndex, schedule.transactionType.name)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, scheduleId: String, occurrenceIndex: Long, transactionType: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Recurring transaction reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Prompts to review scheduled income and expenses"
                }
            )
        }

        val title = if (transactionType == "INCOME") "Income ready to review" else "Expense ready to review"
        val openSchedule = PendingIntent.getActivity(
            context,
            scheduleId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(RecurringTransactionReminderScheduler.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(RecurringTransactionReminderScheduler.EXTRA_OCCURRENCE_INDEX, occurrenceIndex)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_subscription_reminder)
            .setContentTitle("Scheduled transaction reminder")
            .setContentText("Open PesoMind to review it.")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_subscription_reminder)
            .setContentTitle(title)
            .setContentText("Review and confirm this scheduled transaction in PesoMind.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(openSchedule)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(scheduleId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android can revoke notification access after the check above.
        }
    }

    private companion object {
        const val CHANNEL_ID = "recurring_transaction_reminders"
    }
}
