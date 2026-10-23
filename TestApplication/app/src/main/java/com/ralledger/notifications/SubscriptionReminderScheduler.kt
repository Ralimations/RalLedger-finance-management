package com.ralledger.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import com.ralledger.domain.usecase.SubscriptionReminderSchedule
import com.ralledger.ui.settings.FinancialSettings

object SubscriptionReminderScheduler {
    private const val PREFERENCES = "subscription_reminders"
    private const val SCHEDULED_IDS = "scheduled_subscription_ids"
    internal const val ACTION_REMIND = "com.ralledger.action.SUBSCRIPTION_REMINDER"
    internal const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
    internal const val EXTRA_BILLING_DATE = "billing_date"
    internal const val EXTRA_DAYS_BEFORE = "days_before"

    private val lock = Any()

    fun notificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun sync(
        context: Context,
        subscriptions: List<Subscription>,
        settings: FinancialSettings,
        now: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val previouslyScheduled = preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty().toSet()
        val currentIds = subscriptions.mapTo(mutableSetOf()) { it.id }
        val nextScheduled = mutableSetOf<String>()
        val canSchedule = settings.subscriptionRemindersEnabled && notificationsAllowed(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        (previouslyScheduled - currentIds).forEach { cancel(context, alarmManager, it) }
        subscriptions.forEach { subscription ->
            val triggerAt = if (
                canSchedule &&
                !subscription.isArchived &&
                subscription.status == SubscriptionStatus.ACTIVE &&
                subscription.nextBillingDate > 0L
            ) {
                runCatching {
                    SubscriptionReminderSchedule.reminderAt(
                        nextBillingDate = subscription.nextBillingDate,
                        daysBefore = settings.subscriptionReminderDaysBefore,
                        now = now
                    )
                }.getOrNull()
            } else {
                null
            }

            if (triggerAt == null || alarmManager == null) {
                cancel(context, alarmManager, subscription.id)
            } else {
                schedule(
                    context = context,
                    alarmManager = alarmManager,
                    subscriptionId = subscription.id,
                    billingDate = subscription.nextBillingDate,
                    daysBefore = settings.subscriptionReminderDaysBefore,
                    triggerAt = triggerAt
                )
                nextScheduled += subscription.id
            }
        }
        preferences.edit().putStringSet(SCHEDULED_IDS, nextScheduled).apply()
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        subscriptionId: String,
        billingDate: Long,
        daysBefore: Int,
        triggerAt: Long
    ) {
        val intent = reminderIntent(context, subscriptionId).apply {
            putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
            putExtra(EXTRA_BILLING_DATE, billingDate)
            putExtra(EXTRA_DAYS_BEFORE, daysBefore)
        }
        val alarm = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
    }

    private fun cancel(context: Context, alarmManager: AlarmManager?, subscriptionId: String) {
        val alarm = pendingIntent(context, subscriptionId)
        alarmManager?.cancel(alarm)
        alarm.cancel()
    }

    private fun pendingIntent(context: Context, subscriptionId: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(context, subscriptionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderIntent(context: Context, subscriptionId: String): Intent =
        Intent(context, SubscriptionReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("ralledger://subscription-reminder/$subscriptionId"))
}
