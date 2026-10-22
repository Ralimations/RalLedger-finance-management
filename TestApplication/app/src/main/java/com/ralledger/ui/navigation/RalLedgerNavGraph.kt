package com.ralledger.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ralledger.di.AppContainer
import com.ralledger.domain.model.Money
import com.ralledger.ui.accounts.AccountsScreen
import com.ralledger.ui.accounts.AccountsViewModel
import com.ralledger.ui.dashboard.DashboardScreen
import com.ralledger.ui.dashboard.DashboardViewModel
import com.ralledger.ui.insights.InsightsScreen
import com.ralledger.ui.insights.InsightsViewModel
import com.ralledger.ui.planning.PlanningScreen
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.settings.SettingsScreen
import com.ralledger.ui.settings.SettingsViewModel
import com.ralledger.ui.subscriptions.SubscriptionsViewModel
import com.ralledger.ui.transactions.TransactionsScreen
import com.ralledger.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Dashboard)
    object Accounts : Screen("accounts", "Accounts", Icons.Default.AccountBalance)
    object Transactions : Screen("transactions", "Activity", Icons.Default.ReceiptLong)
    object Planning : Screen("planning", "Planning", Icons.Default.EventNote)
    object Insights : Screen("insights", "Insights", Icons.Default.Analytics)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val navScreens = listOf(
    Screen.Dashboard,
    Screen.Accounts,
    Screen.Transactions,
    Screen.Planning,
    Screen.Insights
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RalLedgerMainScreen(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showAddDialog by remember { mutableStateOf(false) }

    val accountsVm: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory(appContainer.repository))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RalLedger v1.2") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                navScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(appContainer.repository))
                DashboardScreen(viewModel = vm)
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(viewModel = accountsVm)
            }
            composable(Screen.Transactions.route) {
                val vm: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory(appContainer.repository))
                TransactionsScreen(viewModel = vm)
            }
            composable(Screen.Planning.route) {
                val recVm: ReceivablesViewModel = viewModel(factory = ReceivablesViewModel.Factory(appContainer.repository))
                val subVm: SubscriptionsViewModel = viewModel(factory = SubscriptionsViewModel.Factory(appContainer.repository))
                PlanningScreen(receivablesViewModel = recVm, subscriptionsViewModel = subVm)
            }
            composable(Screen.Insights.route) {
                val vm: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory(appContainer.repository))
                InsightsScreen(viewModel = vm)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(appContainer.backupRepository))
                SettingsScreen(viewModel = vm)
            }
        }

        if (showAddDialog) {
            QuickAddTransactionDialog(
                appContainer = appContainer,
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

@Composable
fun QuickAddTransactionDialog(
    appContainer: AppContainer,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var txType by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME, TRANSFER
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val accountId by remember { mutableStateOf("bank_1") }
    val destAccountId by remember { mutableStateOf("bank_2") }
    val categoryId by remember { mutableStateOf("cat_food") }

    val accounts by appContainer.repository.accountsFlow.collectAsState(initial = emptyList())
    val categories by appContainer.repository.categoriesFlow.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Transaction") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { txType = "EXPENSE" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Expense") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { txType = "INCOME" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Income") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { txType = "TRANSFER" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Transfer") }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (PHP)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Account ID: $accountId (${accounts.find { it.id == accountId }?.name ?: ""})")
                if (txType == "TRANSFER") {
                    Text("Destination ID: $destAccountId")
                } else {
                    Text("Category ID: $categoryId")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val amount = Money.fromDouble(amountStr.toDoubleOrNull() ?: 0.0)
                        if (amount.isPositive) {
                            when (txType) {
                                "EXPENSE" -> appContainer.repository.recordExpense(
                                    accountId = accounts.firstOrNull()?.id ?: "bank_1",
                                    categoryId = categories.firstOrNull()?.id ?: "cat_food",
                                    amount = amount,
                                    description = description
                                )
                                "INCOME" -> appContainer.repository.recordIncome(
                                    accountId = accounts.firstOrNull()?.id ?: "bank_1",
                                    categoryId = categories.firstOrNull()?.id ?: "cat_salary",
                                    amount = amount,
                                    description = description
                                )
                                "TRANSFER" -> {
                                    val src = accounts.getOrNull(0)?.id ?: "bank_1"
                                    val dst = accounts.getOrNull(1)?.id ?: "bank_2"
                                    appContainer.repository.recordTransfer(
                                        sourceAccountId = src,
                                        destinationAccountId = dst,
                                        amount = amount,
                                        description = description
                                    )
                                }
                            }
                        }
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
