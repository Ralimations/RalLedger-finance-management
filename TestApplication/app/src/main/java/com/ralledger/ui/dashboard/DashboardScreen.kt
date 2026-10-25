package com.ralledger.ui.dashboard
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.usecase.DashboardAttentionItem
import com.ralledger.domain.usecase.DashboardAttentionKind
import com.ralledger.domain.usecase.DashboardAttentionDestination
import com.ralledger.ui.settings.displayDate
import java.time.ZoneId

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    settings: DashboardSettings,
    onSeeAllTransactions: () -> Unit,
    onSeePlanning: (DashboardAttentionDestination?) -> Unit
) {
    val summary by viewModel.dashboardSummary.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val attentionItems by viewModel.attentionItems.collectAsState()
    val recentTransactions = remember(transactions) { recentDashboardTransactions(transactions) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Overview", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        if (attentionItems.isNotEmpty()) {
            item {
                DashboardAttentionCard(attentionItems, onSeePlanning)
            }
        }

        items(settings.visibleInOrder, key = { it.name }) { widget ->
            when (widget) {
                DashboardWidget.AVAILABLE_MONEY -> AvailableMoneyCard(summary.availableToSpendFunds)
                DashboardWidget.NET_WORTH -> NetWorthCard(summary)
                DashboardWidget.RESERVED_SAVINGS -> MetricCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Reserved savings",
                    value = summary.reservedSavings,
                    detail = "Kept for later"
                )
                DashboardWidget.MONEY_OWED -> MoneyOwedCard(summary)
                DashboardWidget.MONTHLY_FLOW -> CashFlowCard(summary)
                DashboardWidget.SUBSCRIPTIONS -> SubscriptionCard(summary.activeSubscriptionsMonthlyTotal)
                DashboardWidget.RECENT_TRANSACTIONS -> RecentTransactionsCard(recentTransactions, onSeeAllTransactions)
            }
        }
    }
}

@Composable
private fun DashboardAttentionCard(items: List<DashboardAttentionItem>, onSeePlanning: (DashboardAttentionDestination?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "${items.size} attention ${if (items.size == 1) "item" else "items"}",
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                TextButton(onClick = { onSeePlanning(null) }) { Text("View planning") }
            }
            items.take(4).forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AttentionRow(item = item, onClick = { onSeePlanning(item.destination) })
            }
            if (items.size > 4) {
                TextButton(onClick = { onSeePlanning(null) }) { Text("${items.size - 4} more") }
            }
        }
    }
}

@Composable
private fun AttentionRow(item: DashboardAttentionItem, onClick: () -> Unit) {
    val tone = when (item.kind) {
        DashboardAttentionKind.OVERDUE -> MaterialTheme.colorScheme.error
        DashboardAttentionKind.OVER_BUDGET -> MaterialTheme.colorScheme.tertiary
        DashboardAttentionKind.DUE_SOON, DashboardAttentionKind.UPCOMING -> MaterialTheme.colorScheme.primary
    }
    val state = when (item.kind) {
        DashboardAttentionKind.OVERDUE -> "Overdue"
        DashboardAttentionKind.OVER_BUDGET -> "Over budget"
        DashboardAttentionKind.DUE_SOON -> item.dueDate?.let { "Due ${it.asDisplayDate()}" } ?: "Due soon"
        DashboardAttentionKind.UPCOMING -> item.dueDate?.let { "Next ${it.asDisplayDate()}" } ?: "Upcoming"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(tone.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (item.kind == DashboardAttentionKind.OVERDUE) "!" else "•", color = tone, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.description} · $state", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(displayMoney(item.amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun java.time.LocalDate.asDisplayDate(): String =
    displayDate(atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

@Composable
private fun MoneyOwedCard(summary: DashboardSummary) {
    Column {
        SectionHeading(title = "Money owed")
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints {
            if (maxWidth < 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.fillMaxWidth(), "To you", summary.accountsReceivableTotal, "Uncollected")
                    MetricCard(Modifier.fillMaxWidth(), "You owe", summary.accountsPayableTotal, "Liabilities")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.weight(1f), "To you", summary.accountsReceivableTotal, "Uncollected")
                    MetricCard(Modifier.weight(1f), "You owe", summary.accountsPayableTotal, "Liabilities")
                }
            }
        }
    }
}

@Composable
private fun AvailableMoneyCard(availableMoney: Money) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AVAILABLE MONEY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f))
            Text(
                displayMoney(availableMoney),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text("Cash & banks after reserved savings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun NetWorthCard(summary: DashboardSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NET WORTH", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                displayMoney(summary.netWorth),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AmountLabel("Assets", summary.totalAssets)
                AmountLabel("Liabilities", summary.totalLiabilities)
            }
        }
    }
}

@Composable
private fun AmountLabel(label: String, amount: Money) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(displayMoney(amount), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: Money, detail: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayMoney(value), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CashFlowCard(summary: DashboardSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("This month", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FlowAmount("Income", summary.monthlyIncomeReceived, MaterialTheme.colorScheme.primary)
                FlowAmount("Expenses", summary.monthlyExpensesPaid, MaterialTheme.colorScheme.error)
                FlowAmount(
                    "Net flow", summary.netCashFlow,
                    if (summary.netCashFlow.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun FlowAmount(label: String, amount: Money, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(displayMoney(amount), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = tint)
    }
}

@Composable
private fun SubscriptionCard(monthlyTotal: Money) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Recurring subscriptions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Estimated monthly total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
            }
            Text(displayMoney(monthlyTotal), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun RecentTransactionsCard(transactions: List<Transaction>, onSeeAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Recent transactions",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
            if (transactions.isEmpty()) {
                Text("No recent transactions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                transactions.forEachIndexed { index, transaction ->
                    RecentTransactionRow(transaction)
                    if (index < transactions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionRow(transaction: Transaction) {
    val isIncoming = transaction.transactionType in setOf(
        TransactionType.INCOME,
        TransactionType.INTEREST_CREDIT,
        TransactionType.RECEIVABLE_COLLECTION,
        TransactionType.LOAN_DISBURSEMENT
    )
    val isOutgoing = transaction.transactionType in setOf(
        TransactionType.EXPENSE,
        TransactionType.SUBSCRIPTION_PAYMENT,
        TransactionType.PAYABLE_PAYMENT,
        TransactionType.LOAN_REPAYMENT
    )
    val tone = when {
        isIncoming -> MaterialTheme.colorScheme.secondary
        isOutgoing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val container = when {
        isIncoming -> MaterialTheme.colorScheme.secondaryContainer
        isOutgoing -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val icon = when {
        isIncoming -> Icons.Default.ArrowDownward
        isOutgoing -> Icons.Default.ArrowUpward
        transaction.transactionType == TransactionType.TRANSFER -> Icons.Default.SwapHoriz
        else -> Icons.Default.ReceiptLong
    }
    val displayedAmount = if (transaction.transactionType == TransactionType.INTEREST_CREDIT) {
        transaction.amount - transaction.fee
    } else transaction.amount
    val amountPrefix = when {
        isIncoming -> "+"
        isOutgoing -> "−"
        else -> ""
    }
    val title = transaction.description.ifBlank {
        transaction.counterparty.ifBlank { transaction.transactionType.displayName }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(container).padding(10.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(
                "${transaction.categoryName ?: transaction.transactionType.displayName} · ${displayDate(transaction.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            "$amountPrefix${displayMoney(displayedAmount)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tone,
            maxLines = 1
        )
    }
}
