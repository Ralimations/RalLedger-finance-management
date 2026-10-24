package com.ralledger.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.ralledger.MainActivity
import com.ralledger.R
import com.ralledger.RalLedgerApplication
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.ui.settings.FinancialSettings

class AvailableMoneyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        (context.applicationContext as RalLedgerApplication).refreshAvailableMoneyWidgets(appWidgetIds) {
            pendingResult.finish()
        }
    }

    companion object {
        fun installedWidgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, AvailableMoneyWidgetProvider::class.java)
        )

        fun update(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            summary: DashboardSummary?,
            settings: FinancialSettings,
            appLockEnabled: Boolean,
            unavailable: Boolean = false
        ) {
            widgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_available_money)
                val balanceText = availableMoneyWidgetText(summary, settings, appLockEnabled, unavailable)
                when {
                    unavailable -> {
                        views.setTextViewText(R.id.widget_balance, context.getString(R.string.widget_data_unavailable))
                        views.setTextViewText(R.id.widget_caption, context.getString(R.string.widget_recovery_caption))
                    }
                    appLockEnabled && !unavailable -> {
                        views.setTextViewText(R.id.widget_balance, context.getString(R.string.widget_balance_hidden))
                        views.setTextViewText(R.id.widget_caption, context.getString(R.string.widget_unlock_caption))
                    }
                    balanceText != null -> {
                        views.setTextViewText(R.id.widget_balance, balanceText)
                        views.setTextViewText(R.id.widget_caption, context.getString(R.string.widget_available_caption))
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_balance, context.getString(R.string.widget_loading))
                        views.setTextViewText(R.id.widget_caption, context.getString(R.string.widget_available_caption))
                    }
                }

                views.setOnClickPendingIntent(R.id.widget_root, activityPendingIntent(context, null, widgetId))
                views.setOnClickPendingIntent(R.id.widget_income, activityPendingIntent(context, "INCOME", widgetId))
                views.setOnClickPendingIntent(R.id.widget_expense, activityPendingIntent(context, "EXPENSE", widgetId))
                views.setOnClickPendingIntent(R.id.widget_transfer, activityPendingIntent(context, "TRANSFER", widgetId))
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun activityPendingIntent(context: Context, type: String?, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = if (type == null) Intent.ACTION_MAIN else "com.ralledger.widget.QUICK_ADD"
                data = "ralledger://widget/${type?.lowercase() ?: "home"}/$widgetId".toUri()
                if (type != null) putExtra(EXTRA_WIDGET_TRANSACTION_TYPE, type)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + (type?.let { listOf("INCOME", "EXPENSE", "TRANSFER").indexOf(it) + 1 } ?: 0),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
