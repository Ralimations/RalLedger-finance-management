package com.ralledger.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Money

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val summary by viewModel.dashboardSummary.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Financial Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "RalLedger: Know your money. Own your data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        // Net Worth Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Net Worth",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary.netWorth.format(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryColumn("Total Assets", summary.totalAssets.format())
                        SummaryColumn("Total Liabilities", summary.totalLiabilities.format())
                    }
                }
            }
        }

        // Cash & Savings Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Liquid Cash & Banks",
                    value = summary.liquidCashAndBankBalances,
                    backgroundColor = Color(0xFFE3F2FD),
                    contentColor = Color(0xFF0D47A1)
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Reserved Savings",
                    value = summary.reservedSavings,
                    backgroundColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF1B5E20)
                )
            }
        }

        // Accounts Receivable & Payable Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Accounts Receivable",
                    subtitle = "Uncollected (Not Cash)",
                    value = summary.accountsReceivableTotal,
                    backgroundColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Accounts Payable",
                    subtitle = "Debts / Liabilities",
                    value = summary.accountsPayableTotal,
                    backgroundColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFB71C1C)
                )
            }
        }

        // Monthly Cash Flow
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Monthly Performance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CashFlowItem("Income Received", summary.monthlyIncomeReceived.format(), Color(0xFF2E7D32))
                        CashFlowItem("Expenses Paid", summary.monthlyExpensesPaid.format(), Color(0xFFC62828))
                        CashFlowItem("Net Flow", summary.netCashFlow.format(), if (summary.netCashFlow.isNegative) Color(0xFFC62828) else Color(0xFF1565C0))
                    }
                }
            }
        }

        // Subscriptions Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Recurring Subscriptions",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF4A148C)
                        )
                        Text(
                            text = "Monthly Commitment",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7B1FA2)
                        )
                    }
                    Text(
                        text = summary.activeSubscriptionsMonthlyTotal.format(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF4A148C)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryColumn(label: String, amount: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(text = amount, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    value: Money,
    backgroundColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = contentColor)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = contentColor.copy(alpha = 0.7f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value.format(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun CashFlowItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = color)
    }
}
