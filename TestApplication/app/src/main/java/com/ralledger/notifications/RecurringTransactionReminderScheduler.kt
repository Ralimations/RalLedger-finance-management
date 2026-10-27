package com.ralledger.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ralledger.domain.model.RecurringTransaction
import com.ralledger.domain.model.RecurringTransactionStatus

object RecurringTransactionReminderScheduler {
    private const val PREFERENCES = "recurring_transaction_reminders"
    private const val SCHEDULED_IDS = "scheduled_recurring_transaction_ids"
    internal const val ACTION_REMIND = "com.ralledger.action.RECURRING_TRANSACTION_REMINDER"
    internal const val EXTRA_SCHEDULE_ID = "recurring_schedule_id"
    internal const val EXTRA_OCCURRENCE_INDEX = "recurring_occurrence_index"
    internal const val EXTRA_OCCURRENCE_TIMESTAMP = "recurring_occurrence_timestamp"

    private val lock = Any()

    fun sync(
        context: Context,
        schedules: List<RecurringTransaction>,
        now: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val previouslyScheduled = preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty().toSet()
        val currentIds = schedules.mapTo(mutableSetOf()) { it.id }
        val nextScheduled = mutableSetOf<String>()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val canSchedule = SubscriptionReminderScheduler.notificationsAllowed(context)

        (previouslyScheduled - currentIds).forEach { cancel(context, alarmManager, it) }
        schedules.forEach { schedule ->
            val triggerAt = schedule.nextOccurrenceTimestamp.takeIf {
                canSchedule &&
                    schedule.remindersEnabled &&
                    schedule.status == RecurringTransactionStatus.ACTIVE &&
                    schedule.nextOccurrenceIndex > 0L &&
                    it > now
            }
            if (triggerAt == null || alarmManager == null) {
                cancel(context, alarmManager, schedule.id)
            } else {
                schedule(context, alarmManager, schedule, triggerAt)
                nextScheduled += schedule.id
            }
        }
        preferences.edit().putStringSet(SCHEDULED_IDS, nextScheduled).apply()
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        schedule: RecurringTransaction,
        triggerAt: Long
    ) {
        val intent = reminderIntent(context, schedule.id).apply {
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_OCCURRENCE_INDEX, schedule.nextOccurrenceIndex)
            putExtra(EXTRA_OCCURRENCE_TIMESTAMP, schedule.nextOccurrenceTimestamp)
        }
        val alarm = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
    }

    private fun cancel(context: Context, alarmManager: AlarmManager?, scheduleId: String) {
        val alarm = pendingIntent(context, scheduleId)
        alarmManager?.cancel(alarm)
        alarm.cancel()
    }

    private fun pendingIntent(context: Context, scheduleId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        scheduleId.hashCode(),
        reminderIntent(context, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun reminderIntent(context: Context, scheduleId: String): Intent =
        Intent(context, RecurringTransactionReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("pesomind://recurring-reminder/$scheduleId"))
}
