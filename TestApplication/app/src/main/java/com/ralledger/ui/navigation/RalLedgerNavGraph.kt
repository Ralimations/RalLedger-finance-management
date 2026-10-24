package com.ralledger.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
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
import com.ralledger.domain.usecase.InsightsPeriodType
import com.ralledger.ui.accounts.AccountsScreen
import com.ralledger.ui.accounts.AccountsViewModel
import com.ralledger.ui.categories.CategoriesScreen
import com.ralledger.ui.categories.CategoriesViewModel
import com.ralledger.ui.dashboard.DashboardScreen
import com.ralledger.ui.dashboard.DashboardViewModel
import com.ralledger.ui.dashboard.DashboardSettings
import com.ralledger.ui.dashboard.DashboardWidget
import com.ralledger.ui.insights.InsightsScreen
import com.ralledger.ui.insights.InsightsViewModel
import com.ralledger.ui.planning.PlanningScreen
import com.ralledger.ui.planning.BudgetsViewModel
import com.ralledger.ui.planning.ExpectedIncomeViewModel
import com.ralledger.ui.planning.PlannedPaymentsViewModel
import com.ralledger.ui.planning.DebtsViewModel
import com.ralledger.ui.planning.SavingsGoalsViewModel
import com.ralledger.ui.planning.InterestProductsViewModel
import com.ralledger.ui.planning.CounterpartiesViewModel
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.settings.SettingsScreen
import com.ralledger.ui.settings.SettingsViewModel
import com.ralledger.ui.settings.FinancialSettings
import com.ralledger.ui.settings.ProvideDateDisplayFormat
import com.ralledger.ui.theme.ThemeMode
import com.ralledger.ui.theme.ThemePalette
import com.ralledger.ui.theme.ThemeSettings
import com.ralledger.ui.subscriptions.SubscriptionsViewModel
import com.ralledger.ui.transactions.TransactionsScreen
import com.ralledger.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Dashboard)
    object Accounts : Screen("accounts", "Accounts", Icons.Default.AccountBalance)
    object Transactions : Screen("transactions", "Activity", Icons.Default.ReceiptLong)
    object Planning : Screen("planning", "Planning", Icons.Default.EventNote)
    object Categories : Screen("categories", "Categories", Icons.Default.Label)
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
    navController: NavHostController = rememberNavController(),
    appLockAvailable: Boolean,
    onEnableAppLock: () -> Unit,
    onDisableAppLock: () -> Unit,
    onOpenSecuritySettings: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNavLabels = LocalConfiguration.current.screenWidthDp >= 320

    var showAddDialog by remember { mutableStateOf(false) }
    val themeSettings by appContainer.themePreferences.settings.collectAsState(initial = ThemeSettings())
    val dashboardSettings by appContainer.dashboardPreferences.settings.collectAsState(initial = DashboardSettings())
    val financialSettings by appContainer.financialPreferences.settings.collectAsState(initial = FinancialSettings())
    val appLockEnabled by appContainer.appLockPreferences.enabled.collectAsState(initial = false)
    val accounts by appContainer.repository.accountsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val accountsVm: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory(appContainer.repository))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RalLedger") },
                actions = {
                    if (currentRoute == Screen.Dashboard.route || currentRoute == Screen.Insights.route) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Record transaction")
                        }
                    }
                    IconButton(onClick = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }) {
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
                        label = if (showBottomNavLabels) {
                            {
                                Text(
                                    text = screen.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp
                                )
                            }
                        } else null,
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute == Screen.Settings.route || currentRoute == Screen.Categories.route) {
                                val returnedToTab = navController.popBackStack(screen.route, inclusive = false)
                                if (!returnedToTab) {
                                    while (navController.currentDestination?.route !in navScreens.map { it.route }) {
                                        if (!navController.popBackStack()) break
                                    }
                                }
                            }
                            if (navController.currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        },
    ) { innerPadding ->
        ProvideDateDisplayFormat(
            format = financialSettings.dateDisplayFormat,
            currencyFormat = financialSettings.currencyDisplayFormat
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
            composable(Screen.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(appContainer.repository))
                DashboardScreen(viewModel = vm, settings = dashboardSettings)
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(viewModel = accountsVm)
            }
            composable(Screen.Transactions.route) {
                val vm: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory(appContainer.repository))
                TransactionsScreen(
                    viewModel = vm,
                    onManageCategories = { navController.navigate(Screen.Categories.route) },
                    listDensity = financialSettings.transactionListDensity
                )
            }
            composable(Screen.Planning.route) {
                val budgetsVm: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory(appContainer.repository))
                val recVm: ReceivablesViewModel = viewModel(factory = ReceivablesViewModel.Factory(appContainer.repository))
                val subVm: SubscriptionsViewModel = viewModel(factory = SubscriptionsViewModel.Factory(appContainer.repository))
                val expectedIncomeVm: ExpectedIncomeViewModel = viewModel(factory = ExpectedIncomeViewModel.Factory(appContainer.repository))
                val plannedPaymentsVm: PlannedPaymentsViewModel = viewModel(factory = PlannedPaymentsViewModel.Factory(appContainer.repository))
                val debtsVm: DebtsViewModel = viewModel(factory = DebtsViewModel.Factory(appContainer.repository))
                val savingsGoalsVm: SavingsGoalsViewModel = viewModel(factory = SavingsGoalsViewModel.Factory(appContainer.repository))
                val interestProductsVm: InterestProductsViewModel = viewModel(factory = InterestProductsViewModel.Factory(appContainer.repository))
                val counterpartiesVm: CounterpartiesViewModel = viewModel(factory = CounterpartiesViewModel.Factory(appContainer.repository))
                PlanningScreen(
                    budgetsViewModel = budgetsVm,
                    receivablesViewModel = recVm,
                    subscriptionsViewModel = subVm,
                    expectedIncomeViewModel = expectedIncomeVm,
                    plannedPaymentsViewModel = plannedPaymentsVm,
                    debtsViewModel = debtsVm,
                    savingsGoalsViewModel = savingsGoalsVm,
                    interestProductsViewModel = interestProductsVm,
                    counterpartiesViewModel = counterpartiesVm
                )
            }
            composable(Screen.Insights.route) {
                val vm: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory(appContainer.repository))
                InsightsScreen(viewModel = vm, defaultPeriodType = financialSettings.defaultInsightsPeriod)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(appContainer.backupRepository, appContainer.repository)
                )
                SettingsScreen(
                    viewModel = vm,
                    accounts = accounts,
                    appearance = themeSettings,
                    dashboardSettings = dashboardSettings,
                    financialSettings = financialSettings,
                    appLockEnabled = appLockEnabled,
                    appLockAvailable = appLockAvailable,
                    onEnableAppLock = onEnableAppLock,
                    onDisableAppLock = onDisableAppLock,
                    onOpenSecuritySettings = onOpenSecuritySettings,
                    onThemeModeChange = { mode -> scope.launch { appContainer.themePreferences.setMode(mode) } },
                    onThemePaletteChange = { palette -> scope.launch { appContainer.themePreferences.setPalette(palette) } },
                    onDashboardWidgetVisibilityChange = { widget, visible ->
                        scope.launch { appContainer.dashboardPreferences.setVisible(widget, visible) }
                    },
                    onMoveDashboardWidget = { widget, offset ->
                        scope.launch { appContainer.dashboardPreferences.move(widget, offset) }
                    },
                    onDefaultTransactionAccountChange = { accountId ->
                        scope.launch { appContainer.financialPreferences.setDefaultTransactionAccount(accountId) }
                    },
                    onDefaultInsightsPeriodChange = { period ->
                        scope.launch { appContainer.financialPreferences.setDefaultInsightsPeriod(period) }
                    },
                    onDateDisplayFormatChange = { format ->
                        scope.launch { appContainer.financialPreferences.setDateDisplayFormat(format) }
                    },
                    onCurrencyDisplayFormatChange = { format ->
                        scope.launch { appContainer.financialPreferences.setCurrencyDisplayFormat(format) }
                    },
                    onTransactionListDensityChange = { density ->
                        scope.launch { appContainer.financialPreferences.setTransactionListDensity(density) }
                    },
                    onSubscriptionRemindersChange = { enabled ->
                        scope.launch { appContainer.financialPreferences.setSubscriptionRemindersEnabled(enabled) }
                    },
                    onSubscriptionReminderDaysBeforeChange = { days ->
                        scope.launch { appContainer.financialPreferences.setSubscriptionReminderDaysBefore(days) }
                    },
                    onManageCategories = { navController.navigate(Screen.Categories.route) }
                )
            }
            composable(Screen.Categories.route) {
                val vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory(appContainer.repository))
                CategoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }

            if (showAddDialog) {
                QuickAddTransactionDialog(
                    appContainer = appContainer,
                    defaultTransactionAccountId = financialSettings.defaultTransactionAccountId,
                    onDismiss = { showAddDialog = false }
                )
            }
        }
    }
}

@Composable
fun QuickAddTransactionDialog(
    appContainer: AppContainer,
    defaultTransactionAccountId: String?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var txType by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME, TRANSFER
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var destAccountId by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var destinationMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val accounts by appContainer.repository.accountsFlow.collectAsState(initial = emptyList())
    val categories by appContainer.repository.categoriesFlow.collectAsState(initial = emptyList())
    val eligibleAccounts = accounts.filter { it.accountType.isAsset && !it.isArchived }
    val eligibleCategories = categories.filter {
        !it.isArchived && it.type.name == if (txType == "INCOME") "INCOME" else "EXPENSE"
    }
    LaunchedEffect(eligibleAccounts, defaultTransactionAccountId) {
        val eligibleIds = eligibleAccounts.map { it.id }.toSet()
        val defaultAccount = defaultTransactionAccountId?.takeIf { it in eligibleIds }
            ?: eligibleAccounts.firstOrNull()?.id.orEmpty()
        if (accountId.isBlank() || accountId !in eligibleIds) accountId = defaultAccount
        val fallbackDestination = eligibleAccounts.firstOrNull { it.id != accountId }?.id.orEmpty()
        if (destAccountId.isBlank() || destAccountId !in eligibleIds || destAccountId == accountId) {
            destAccountId = fallbackDestination
        }
    }
    LaunchedEffect(eligibleCategories, txType) {
        if (categoryId !in eligibleCategories.map { it.id }) categoryId = eligibleCategories.firstOrNull()?.id ?: ""
    }

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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (type, label) ->
                        FilterChip(
                            selected = txType == type,
                            onClick = { txType = type; inputError = null },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; inputError = null },
                    label = { Text("Amount (PHP)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    TextButton(onClick = { accountMenuExpanded = true }) {
                        Text("Account: ${eligibleAccounts.find { it.id == accountId }?.name ?: "Choose an account"}")
                    }
                    DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                        eligibleAccounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.name) }, onClick = {
                                accountId = account.id
                                accountMenuExpanded = false
                            })
                        }
                    }
                }
                if (txType == "TRANSFER") {
                    Box {
                        TextButton(onClick = { destinationMenuExpanded = true }) {
                            Text("To: ${eligibleAccounts.find { it.id == destAccountId }?.name ?: "Choose destination"}")
                        }
                        DropdownMenu(expanded = destinationMenuExpanded, onDismissRequest = { destinationMenuExpanded = false }) {
                            eligibleAccounts.filter { it.id != accountId }.forEach { account ->
                                DropdownMenuItem(text = { Text(account.name) }, onClick = {
                                    destAccountId = account.id
                                    destinationMenuExpanded = false
                                })
                            }
                        }
                    }
                } else {
                    Box {
                        TextButton(onClick = { categoryMenuExpanded = true }) {
                            Text("${if (txType == "INCOME") "Income source" else "Category"}: ${eligibleCategories.find { it.id == categoryId }?.name ?: "Choose"}")
                        }
                        DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                            eligibleCategories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                    categoryId = category.id
                                    categoryMenuExpanded = false
                                })
                            }
                        }
                    }
                }
                inputError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val amount = Money.parse(amountStr)
                        try {
                            when {
                                amount == null || !amount.isPositive -> inputError = "Enter a valid amount greater than zero."
                                accountId.isBlank() -> inputError = "Add or choose a financial account first."
                                txType != "TRANSFER" && categoryId.isBlank() -> inputError = "Choose a category or income source."
                                txType == "TRANSFER" && (destAccountId.isBlank() || destAccountId == accountId) -> inputError = "Choose a different destination account."
                                txType == "EXPENSE" -> {
                                    appContainer.repository.recordExpense(accountId, categoryId, amount, description)
                                    onDismiss()
                                }
                                txType == "INCOME" -> {
                                    appContainer.repository.recordIncome(accountId, categoryId, amount, description)
                                    onDismiss()
                                }
                                else -> {
                                    appContainer.repository.recordTransfer(accountId, destAccountId, amount, description = description)
                                    onDismiss()
                                }
                            }
                        } catch (error: Exception) {
                            inputError = error.localizedMessage ?: "The transaction could not be saved."
                        }
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
