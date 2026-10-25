package com.ralledger.ui.planning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.ui.receivables.ReceivablesScreen
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.subscriptions.SubscriptionsScreen
import com.ralledger.ui.subscriptions.SubscriptionsViewModel

private data class PlanningModule(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private data class PlanningGroup(
    val title: String,
    val modules: List<Int>
)

private val planningModules = listOf(
    PlanningModule("Budgets", "Set category limits and track spending", Icons.Default.AccountBalanceWallet),
    PlanningModule("Receivables", "Track money owed to you and collect payments", Icons.Default.Payments),
    PlanningModule("Expected income", "Plan income before it arrives", Icons.Default.ArrowDownward),
    PlanningModule("Planned payments", "Track upcoming payments and due dates", Icons.Default.ReceiptLong),
    PlanningModule("Bills & loans", "Manage bills, loans, and repayments", Icons.Default.AccountBalance),
    PlanningModule("Subscriptions", "Review recurring charges and payment history", Icons.Default.EventRepeat),
    PlanningModule("Savings goals", "Set targets and track reserved funds", Icons.Default.Savings),
    PlanningModule("Interest & deposits", "Review deposit profiles and credited interest", Icons.Default.Savings),
    PlanningModule("Clients", "Organize clients, suppliers, and counterparties", Icons.Default.Payments),
    PlanningModule("Reports", "Save and revisit financial reports", Icons.Default.Analytics),
    PlanningModule("Projects", "Separate personal and business activity", Icons.Default.Work)
)

private val planningGroups = listOf(
    PlanningGroup("Money in and commitments", listOf(1, 2, 3, 4, 5)),
    PlanningGroup("Budgets and savings", listOf(0, 6, 7)),
    PlanningGroup("Organization and reports", listOf(8, 9, 10))
)

@Composable
fun PlanningScreen(
    budgetsViewModel: BudgetsViewModel,
    receivablesViewModel: ReceivablesViewModel,
    subscriptionsViewModel: SubscriptionsViewModel,
    expectedIncomeViewModel: ExpectedIncomeViewModel,
    plannedPaymentsViewModel: PlannedPaymentsViewModel,
    debtsViewModel: DebtsViewModel,
    savingsGoalsViewModel: SavingsGoalsViewModel,
    interestProductsViewModel: InterestProductsViewModel,
    counterpartiesViewModel: CounterpartiesViewModel,
    customReportsViewModel: CustomReportsViewModel,
    financialProjectsViewModel: FinancialProjectsViewModel,
    quickActionRequest: PlanningQuickAction? = null,
    onQuickActionHandled: () -> Unit = {}
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(quickActionRequest) {
        quickActionRequest?.let { request ->
            selectedSection = request.tabIndex
            if (!request.opensEditor) onQuickActionHandled()
        }
    }

    BackHandler(enabled = selectedSection >= 0) {
        selectedSection = -1
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        if (selectedSection < 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Planning",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Stay ahead of what is due, expected, and worth saving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                planningGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        group.modules.forEach { index ->
                            PlanningModuleCard(
                                module = planningModules[index],
                                onClick = { selectedSection = index }
                            )
                        }
                    }
                }
            }
        } else {
            if (selectedSection !in planningModules.indices) {
                selectedSection = -1
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedSection = -1 }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Planning")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Planning",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedSection) {
                        0 -> BudgetsScreen(
                            viewModel = budgetsViewModel,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        1 -> ReceivablesScreen(
                            viewModel = receivablesViewModel,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        2 -> ExpectedIncomeScreen(
                            viewModel = expectedIncomeViewModel,
                            onViewReceivables = { selectedSection = 1 },
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        3 -> PlannedPaymentsScreen(viewModel = plannedPaymentsViewModel)
                        4 -> DebtsScreen(
                            viewModel = debtsViewModel,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        5 -> SubscriptionsScreen(
                            viewModel = subscriptionsViewModel,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        6 -> SavingsGoalsScreen(
                            viewModel = savingsGoalsViewModel,
                            quickActionRequest = quickActionRequest,
                            onQuickActionHandled = onQuickActionHandled
                        )
                        7 -> InterestProductsScreen(viewModel = interestProductsViewModel)
                        8 -> CounterpartiesScreen(viewModel = counterpartiesViewModel)
                        9 -> CustomReportsScreen(viewModel = customReportsViewModel)
                        10 -> FinancialProjectsScreen(viewModel = financialProjectsViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningModuleCard(module: PlanningModule, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(9.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(module.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
