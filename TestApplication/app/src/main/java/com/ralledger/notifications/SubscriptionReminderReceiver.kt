package com.ralledger.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ralledger.MainActivity
import com.ralledger.R
import com.ralledger.RalLedgerApplication
import com.ralledger.domain.model.SubscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubscriptionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subscriptionId = intent.getStringExtra(SubscriptionReminderScheduler.EXTRA_SUBSCRIPTION_ID)
            ?: intent.data?.lastPathSegment
            ?: return
        val billingDate = intent.getLongExtra(SubscriptionReminderScheduler.EXTRA_BILLING_DATE, -1L)
        val daysBefore = intent.getIntExtra(SubscriptionReminderScheduler.EXTRA_DAYS_BEFORE, -1)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as RalLedgerApplication
                val settings = app.container.financialPreferences.settings.first()
                if (!settings.subscriptionRemindersEnabled ||
                    settings.subscriptionReminderDaysBefore != daysBefore ||
                    !SubscriptionReminderScheduler.notificationsAllowed(context)
                ) return@launch

                val subscription = app.container.repository.subscriptionsFlow.first()
                    .firstOrNull { it.id == subscriptionId }
                    ?: return@launch
                if (subscription.isArchived ||
                    subscription.status != SubscriptionStatus.ACTIVE ||
                    subscription.nextBillingDate != billingDate
                ) return@launch

                postNotification(context, subscriptionId, subscription.name, subscription.billingAmount.format(), daysBefore)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(
        context: Context,
        subscriptionId: String,
        name: String,
        amount: String,
        daysBefore: Int
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Subscription reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for upcoming subscription billing dates"
                }
            )
        }

        val title = when (daysBefore) {
            0 -> "Subscription due today"
            1 -> "Subscription due tomorrow"
            else -> "Subscription due in $daysBefore days"
        }
        val launchApp = PendingIntent.getActivity(
            context,
            subscriptionId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val privateNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_subscription_reminder)
            .setContentTitle(title)
            .setContentText("$name · $amount")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_subscription_reminder)
                    .setContentTitle("Upcoming subscription reminder")
                    .setContentText("Open PesoMind to view the details.")
                    .build()
            )
            .setContentIntent(launchApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(subscriptionId.hashCode(), privateNotification)
        } catch (_: SecurityException) {
            // The system may revoke notification access after the check above.
        }
    }

    private companion object {
        const val CHANNEL_ID = "subscription_reminders"
    }
}
