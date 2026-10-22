package com.ralledger.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ralledger.di.AppContainer
import com.ralledger.ui.accounts.AccountsScreen
import com.ralledger.ui.accounts.AccountsViewModel
import com.ralledger.ui.dashboard.DashboardScreen
import com.ralledger.ui.dashboard.DashboardViewModel
import com.ralledger.ui.receivables.ReceivablesScreen
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.reconciliation.ReconciliationScreen
import com.ralledger.ui.reconciliation.ReconciliationViewModel
import com.ralledger.ui.settings.SettingsScreen
import com.ralledger.ui.settings.SettingsViewModel
import com.ralledger.ui.subscriptions.SubscriptionsScreen
import com.ralledger.ui.subscriptions.SubscriptionsViewModel
import com.ralledger.ui.transactions.TransactionsScreen
import com.ralledger.ui.transactions.TransactionsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Accounts : Screen("accounts", "Accounts", Icons.Default.AccountBalance)
    object Transactions : Screen("transactions", "Journal", Icons.Default.ReceiptLong)
    object Receivables : Screen("receivables", "Receivables", Icons.Default.Payments)
    object Subscriptions : Screen("subscriptions", "Subs", Icons.Default.Subscriptions)
    object Reconciliation : Screen("reconciliation", "Reconcile", Icons.Default.Tune)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val navScreens = listOf(
    Screen.Dashboard,
    Screen.Accounts,
    Screen.Transactions,
    Screen.Receivables,
    Screen.Subscriptions,
    Screen.Reconciliation,
    Screen.Settings
)

@Composable
fun RalLedgerMainScreen(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
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
                val vm: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory(appContainer.repository))
                AccountsScreen(viewModel = vm)
            }
            composable(Screen.Transactions.route) {
                val vm: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory(appContainer.repository))
                TransactionsScreen(viewModel = vm)
            }
            composable(Screen.Receivables.route) {
                val vm: ReceivablesViewModel = viewModel(factory = ReceivablesViewModel.Factory(appContainer.repository))
                ReceivablesScreen(viewModel = vm)
            }
            composable(Screen.Subscriptions.route) {
                val vm: SubscriptionsViewModel = viewModel(factory = SubscriptionsViewModel.Factory(appContainer.repository))
                SubscriptionsScreen(viewModel = vm)
            }
            composable(Screen.Reconciliation.route) {
                val vm: ReconciliationViewModel = viewModel(factory = ReconciliationViewModel.Factory(appContainer.repository))
                ReconciliationScreen(viewModel = vm)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(appContainer.backupRepository))
                SettingsScreen(viewModel = vm)
            }
        }
    }
}
