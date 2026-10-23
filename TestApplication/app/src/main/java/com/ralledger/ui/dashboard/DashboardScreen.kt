package com.ralledger.ui.dashboard
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Money

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, settings: DashboardSettings) {
    val summary by viewModel.dashboardSummary.collectAsState()

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
            }
        }
    }
}

@Composable
private fun MoneyOwedCard(summary: DashboardSummary) {
    Column {
        SectionHeading(title = "Money owed")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "To you",
                value = summary.accountsReceivableTotal,
                detail = "Uncollected"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "You owe",
                value = summary.accountsPayableTotal,
                detail = "Liabilities"
            )
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
            Text("Cash & bank accounts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
        Text(displayMoney(amount), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer)
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
