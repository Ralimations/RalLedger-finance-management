package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.ui.receivables.ReceivablesScreen
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.subscriptions.SubscriptionsScreen
import com.ralledger.ui.subscriptions.SubscriptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    counterpartiesViewModel: CounterpartiesViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Budgets", "Receivables", "Expected income", "Planned payments", "Bills & loans", "Subscriptions", "Savings goals", "Interest & deposits", "Clients")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Financial Planning",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> BudgetsScreen(viewModel = budgetsViewModel)
            1 -> ReceivablesScreen(viewModel = receivablesViewModel)
            2 -> ExpectedIncomeScreen(
                viewModel = expectedIncomeViewModel,
                onViewReceivables = { selectedTab = 1 }
            )
            3 -> PlannedPaymentsScreen(viewModel = plannedPaymentsViewModel)
            4 -> DebtsScreen(viewModel = debtsViewModel)
            5 -> SubscriptionsScreen(viewModel = subscriptionsViewModel)
            6 -> SavingsGoalsScreen(viewModel = savingsGoalsViewModel)
            7 -> InterestProductsScreen(viewModel = interestProductsViewModel)
            8 -> CounterpartiesScreen(viewModel = counterpartiesViewModel)
        }
    }
}
