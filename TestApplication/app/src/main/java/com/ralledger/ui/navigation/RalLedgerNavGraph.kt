package com.ralledger.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import com.ralledger.domain.model.RecurringTransaction
import com.ralledger.domain.model.TransactionRepeatFrequency
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.usecase.InsightsPeriodType
import com.ralledger.ui.accounts.AccountsScreen
import com.ralledger.ui.components.AccountBalancesSummary
import com.ralledger.ui.components.FinancialDateTimeSelector
import com.ralledger.ui.components.FinancialNotesField
import com.ralledger.ui.components.MoneyAmountField
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
import com.ralledger.ui.planning.PlanningQuickAction
import com.ralledger.domain.usecase.DashboardAttentionDestination
import com.ralledger.ui.planning.CustomReportsViewModel
import com.ralledger.ui.planning.FinancialProjectsViewModel
import com.ralledger.ui.planning.RecurringTransactionsViewModel
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
import com.ralledger.ui.transactions.TransactionTemplatesDialog
import com.ralledger.ui.transactions.TransactionsScreen
import com.ralledger.ui.transactions.TransactionsViewModel
import com.ralledger.ui.widgets.normalizeWidgetTransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val compactTitle: String = title
) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Dashboard)
    object Accounts : Screen("accounts", "Accounts", Icons.Default.AccountBalance, "Acct")
    object Transactions : Screen("transactions", "Activity", Icons.Default.ReceiptLong)
    object Planning : Screen("planning", "Planning", Icons.Default.EventNote, "Plan")
    object Categories : Screen("categories", "Categories", Icons.Default.Label)
    object Insights : Screen("insights", "Insights", Icons.Default.Analytics, "Stats")
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
    widgetQuickAddType: String?,
    onWidgetQuickAddHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
    appLockAvailable: Boolean,
    onEnableAppLock: () -> Unit,
    onDisableAppLock: () -> Unit,
    onOpenSecuritySettings: () -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale
    val showBottomNavLabels = screenWidthDp >= 320

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickActionHub by remember { mutableStateOf(false) }
    var dialogInitialTransactionType by rememberSaveable { mutableStateOf("EXPENSE") }
    var dialogInitialAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogInitialRecurringId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogRecurringOccurrenceIndex by rememberSaveable { mutableStateOf<Long?>(null) }
    val recurringTransactions by appContainer.repository.recurringTransactionsFlow.collectAsState(initial = emptyList())
    var pendingPlanningAction by remember { mutableStateOf<PlanningQuickAction?>(null) }
    val themeSettings by appContainer.themePreferences.settings.collectAsState(initial = ThemeSettings())
    val dashboardSettings by appContainer.dashboardPreferences.settings.collectAsState(initial = DashboardSettings())
    val financialSettings by appContainer.financialPreferences.settings.collectAsState(initial = FinancialSettings())
    val appLockEnabled by appContainer.appLockPreferences.enabled.collectAsState(initial = false)
    val accounts by appContainer.repository.accountsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(widgetQuickAddType) {
        normalizeWidgetTransactionType(widgetQuickAddType)?.let { type ->
            dialogInitialTransactionType = type
            dialogInitialAccountId = null
            dialogInitialRecurringId = null
            dialogRecurringOccurrenceIndex = null
            showAddDialog = true
            onWidgetQuickAddHandled()
        }
    }

    val accountsVm: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory(appContainer.repository))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RalLedger") },
                actions = {
                    if (currentRoute in navScreens.map { it.route }) {
                        IconButton(
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Quick actions"
                            },
                            onClick = {
                                showQuickActionHub = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                    IconButton(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Settings"
                        },
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                navScreens.forEach { screen ->
                    NavigationBarItem(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = screen.title
                        },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = if (showBottomNavLabels) {
                            {
                                Text(
                                    text = displayNavigationLabel(
                                        title = screen.title,
                                        compactTitle = screen.compactTitle,
                                        screenWidthDp = screenWidthDp,
                                        fontScale = fontScale
                                    ),
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
                DashboardScreen(
                    viewModel = vm,
                    settings = dashboardSettings,
                    onSeeAllTransactions = {
                        navController.navigate(Screen.Transactions.route) { launchSingleTop = true }
                    },
                    onSeePlanning = { destination ->
                        pendingPlanningAction = when (destination) {
                            DashboardAttentionDestination.BUDGETS -> PlanningQuickAction.OPEN_BUDGETS
                            DashboardAttentionDestination.RECEIVABLES -> PlanningQuickAction.OPEN_RECEIVABLES
                            DashboardAttentionDestination.PLANNED_PAYMENTS -> PlanningQuickAction.OPEN_PLANNED_PAYMENTS
                            DashboardAttentionDestination.BILLS_AND_LOANS -> PlanningQuickAction.OPEN_BILLS_AND_LOANS
                            DashboardAttentionDestination.SUBSCRIPTIONS -> PlanningQuickAction.OPEN_SUBSCRIPTIONS
                            null -> null
                        }
                        navController.navigate(Screen.Planning.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    viewModel = accountsVm,
                    onQuickAddForAccount = { type, accountId ->
                        dialogInitialTransactionType = type
                        dialogInitialAccountId = accountId
                        dialogInitialRecurringId = null
                        dialogRecurringOccurrenceIndex = null
                        showAddDialog = true
                    }
                )
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
                val customReportsVm: CustomReportsViewModel = viewModel(factory = CustomReportsViewModel.Factory(appContainer.repository))
                val financialProjectsVm: FinancialProjectsViewModel = viewModel(factory = FinancialProjectsViewModel.Factory(appContainer.repository))
                val recurringTransactionsVm: RecurringTransactionsViewModel = viewModel(factory = RecurringTransactionsViewModel.Factory(appContainer.repository))
                PlanningScreen(
                    budgetsViewModel = budgetsVm,
                    receivablesViewModel = recVm,
                    subscriptionsViewModel = subVm,
                    expectedIncomeViewModel = expectedIncomeVm,
                    plannedPaymentsViewModel = plannedPaymentsVm,
                    debtsViewModel = debtsVm,
                    savingsGoalsViewModel = savingsGoalsVm,
                    interestProductsViewModel = interestProductsVm,
                    counterpartiesViewModel = counterpartiesVm,
                    customReportsViewModel = customReportsVm,
                    financialProjectsViewModel = financialProjectsVm,
                    recurringTransactionsViewModel = recurringTransactionsVm,
                    onRecordRecurringTransaction = { schedule ->
                        dialogInitialTransactionType = schedule.transactionType.name
                        dialogInitialAccountId = schedule.accountId
                        dialogInitialRecurringId = schedule.id
                        dialogRecurringOccurrenceIndex = schedule.nextOccurrenceIndex
                        showAddDialog = true
                    },
                    quickActionRequest = pendingPlanningAction,
                    onQuickActionHandled = { pendingPlanningAction = null }
                )
            }
            composable(Screen.Insights.route) {
                val vm: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory(appContainer.repository))
                InsightsScreen(viewModel = vm, defaultPeriodType = financialSettings.defaultInsightsPeriod)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(appContainer.backupRepository, appContainer.repository, applicationContext)
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

            if (showQuickActionHub) {
                QuickActionHubSheet(
                    onDismiss = { showQuickActionHub = false },
                    onTransactionSelected = { type ->
                        dialogInitialTransactionType = type
                        dialogInitialAccountId = null
                        dialogInitialRecurringId = null
                        dialogRecurringOccurrenceIndex = null
                        showQuickActionHub = false
                        showAddDialog = true
                    },
                    onPlanningActionSelected = { action ->
                        pendingPlanningAction = action
                        showQuickActionHub = false
                        navController.navigate(Screen.Planning.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            if (showAddDialog) {
                QuickAddTransactionDialog(
                    appContainer = appContainer,
                    defaultTransactionAccountId = financialSettings.defaultTransactionAccountId,
                    initialTransactionType = dialogInitialTransactionType,
                    initialAccountId = dialogInitialAccountId,
                    initialRecurringTransaction = recurringTransactions.firstOrNull { it.id == dialogInitialRecurringId },
                    recurringOccurrenceIndex = dialogRecurringOccurrenceIndex,
                    onDismiss = {
                        showAddDialog = false
                        dialogInitialAccountId = null
                        dialogInitialRecurringId = null
                        dialogRecurringOccurrenceIndex = null
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickAddTransactionDialog(
    appContainer: AppContainer,
    defaultTransactionAccountId: String?,
    initialTransactionType: String = "EXPENSE",
    initialAccountId: String? = null,
    initialRecurringTransaction: RecurringTransaction? = null,
    recurringOccurrenceIndex: Long? = null,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var txType by rememberSaveable(initialTransactionType, initialRecurringTransaction?.id) {
        mutableStateOf(initialRecurringTransaction?.transactionType?.name ?: normalizeWidgetTransactionType(initialTransactionType) ?: "EXPENSE")
    } // EXPENSE, INCOME, TRANSFER
    var draftSubmissionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var amountStr by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.amount?.let(::quickAddAmountInput).orEmpty()) }
    var description by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.description.orEmpty()) }
    var notes by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.notes.orEmpty()) }
    var counterparty by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.counterparty.orEmpty()) }
    var selectedTagIds by rememberSaveable(
        initialRecurringTransaction?.id,
        stateSaver = listSaver(
            save = { selectedIds: Set<String> -> selectedIds.toList() },
            restore = { restoredIds: List<String> -> restoredIds.toSet() }
        )
    ) { mutableStateOf(initialRecurringTransaction?.tagIds.orEmpty()) }
    var selectedTimestamp by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.nextOccurrenceTimestamp ?: System.currentTimeMillis()) }
    var feeAmountStr by rememberSaveable { mutableStateOf("") }
    var feeCategoryId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable(initialAccountId) { mutableStateOf(initialAccountId.orEmpty()) }
    var hasManuallySelectedAccount by rememberSaveable(initialAccountId) { mutableStateOf(false) }
    var accountDefaultSource by remember { mutableStateOf(QuickAddAccountDefaultSource.NONE) }
    var destAccountId by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(initialRecurringTransaction?.categoryId.orEmpty()) }
    var hasManuallySelectedCategory by rememberSaveable { mutableStateOf(false) }
    var categoryWasSuggested by rememberSaveable { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var destinationMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var feeCategoryMenuExpanded by remember { mutableStateOf(false) }
    var counterpartyMenuExpanded by remember { mutableStateOf(false) }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var repeatEnabled by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(false) }
    var repeatFrequency by rememberSaveable(initialRecurringTransaction?.id) { mutableStateOf(TransactionRepeatFrequency.MONTHLY) }
    var repeatFrequencyMenuExpanded by remember { mutableStateOf(false) }
    var showAdvancedDetails by rememberSaveable { mutableStateOf(false) }
    var showTemplateManager by rememberSaveable { mutableStateOf(false) }
    var templateMessage by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || !isSaving }
    )
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale
    val useTwoColumnTypeSelector = screenWidthDp < 380 || (fontScale >= 1.2f && screenWidthDp < 460)
    val transactionTypes = listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer")
    val transactionTypeRows = if (useTwoColumnTypeSelector) transactionTypes.chunked(2) else listOf(transactionTypes)
    val requestDismiss = {
        when (quickAddDismissAction(hasUserEdited, isSaving)) {
            QuickAddDismissAction.IGNORE -> Unit
            QuickAddDismissAction.CONFIRM_DISCARD -> showDiscardConfirmation = true
            QuickAddDismissAction.DISMISS -> onDismiss()
        }
    }

    fun runTemplateOperation(successMessage: String, operation: suspend () -> Unit) {
        scope.launch {
            templateMessage = null
            try {
                operation()
                templateMessage = successMessage
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                templateMessage = error.localizedMessage ?: "The template could not be updated."
            }
        }
    }

    val accounts by appContainer.repository.accountsFlow.collectAsState(initial = emptyList())
    val categories by appContainer.repository.categoriesFlow.collectAsState(initial = emptyList())
    val transactions by appContainer.repository.transactionsFlow.collectAsState(initial = emptyList())
    val transactionTags by appContainer.repository.transactionTagsFlow.collectAsState(initial = emptyList())
    val transactionTemplates by appContainer.repository.transactionTemplatesFlow.collectAsState(initial = emptyList())
    val counterparties by appContainer.repository.counterpartiesFlow.collectAsState(initial = emptyList())
    val eligibleAccounts = accounts.filter { it.accountType.isAsset && !it.isArchived }
    val eligibleCategories = categories.filter {
        !it.isArchived && it.type.name == if (txType == "INCOME") "INCOME" else "EXPENSE"
    }
    val eligibleFeeCategories = categories.filter { !it.isArchived && it.type.name == "EXPENSE" }
    val eligibleTransactionTags = transactionTags.filterNot { it.isArchived }
    val eligibleTransactionTagIds = remember(eligibleTransactionTags) { eligibleTransactionTags.map { it.id }.toSet() }
    val eligibleCounterparties = counterparties.filterNot { it.isArchived }
    val expectedTransactionType = when (txType) {
        "INCOME" -> "INCOME"
        "EXPENSE" -> "EXPENSE"
        else -> null
    }
    val lastRelevantTransaction = expectedTransactionType?.let { type ->
        transactions.asSequence()
            .filter { it.transactionType.name == type && !it.isReversed }
            .maxByOrNull { it.timestamp }
    }
    val lastUsedAccountId = lastRelevantTransaction?.accountId
    val lastUsedCategoryId = lastRelevantTransaction?.categoryId
    val eligibleAccountIds = remember(eligibleAccounts) { eligibleAccounts.map { it.id } }
    val eligibleCategoryIds = remember(eligibleCategories) { eligibleCategories.map { it.id } }
    val accountDefault = remember(initialAccountId, defaultTransactionAccountId, lastUsedAccountId, eligibleAccountIds) {
        resolveQuickAddAccountDefault(
            contextualAccountId = initialAccountId,
            preferredAccountId = defaultTransactionAccountId,
            lastUsedAccountId = lastUsedAccountId,
            eligibleAccountIds = eligibleAccountIds
        )
    }
    val categoryDefault = remember(lastUsedCategoryId, eligibleCategoryIds) {
        resolveQuickAddCategoryDefault(lastUsedCategoryId, eligibleCategoryIds)
    }
    LaunchedEffect(eligibleAccountIds, accountDefault, hasManuallySelectedAccount) {
        if (accountId !in eligibleAccountIds) {
            hasManuallySelectedAccount = false
        }
        if (!hasManuallySelectedAccount) {
            accountId = accountDefault.accountId
            accountDefaultSource = accountDefault.source
        }
        val eligibleIds = eligibleAccountIds.toSet()
        val fallbackDestination = eligibleAccounts.firstOrNull { it.id != accountId }?.id.orEmpty()
        if (destAccountId.isBlank() || destAccountId !in eligibleIds || destAccountId == accountId) {
            destAccountId = fallbackDestination
        }
    }
    LaunchedEffect(eligibleCategoryIds, categoryDefault, txType) {
        if (categoryId !in eligibleCategoryIds) {
            categoryId = categoryDefault.categoryId
            categoryWasSuggested = categoryDefault.source == QuickAddCategoryDefaultSource.RECENT
            hasManuallySelectedCategory = false
        }
    }
    LaunchedEffect(eligibleFeeCategories) {
        if (feeCategoryId !in eligibleFeeCategories.map { it.id }) {
            feeCategoryId = eligibleFeeCategories.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(eligibleTransactionTagIds) {
        selectedTagIds = selectedTagIds.intersect(eligibleTransactionTagIds)
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (txType) {
                            "INCOME" -> "Add income"
                            "TRANSFER" -> "Transfer money"
                            else -> "Add expense"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                    )
                    Text("A few details, then save.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = requestDismiss, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Close transaction form")
                }
            }

            TextButton(
                onClick = { showTemplateManager = true },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Templates") }

            transactionTypeRows.forEach { typeRow ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeRow.forEach { (type, label) ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = txType == type,
                            onClick = {
                                txType = type
                                if (type == "TRANSFER") repeatEnabled = false
                                inputError = null
                                hasUserEdited = true
                            },
                            label = { Text(label, maxLines = 1, fontSize = 12.sp) }
                        )
                    }
                    if (typeRow.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            AccountBalancesSummary(accounts)

            MoneyAmountField(
                value = amountStr,
                onValueChange = { amountStr = it; inputError = null; hasUserEdited = true },
                label = "Amount (${eligibleAccounts.find { it.id == accountId }?.currency ?: "PHP"})"
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { accountMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            eligibleAccounts.find { it.id == accountId }?.name ?: "Choose account",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                        eligibleAccounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.name) }, onClick = {
                                accountId = account.id
                                hasManuallySelectedAccount = true
                                accountMenuExpanded = false
                                hasUserEdited = true
                            })
                        }
                    }
                }
                if (!hasManuallySelectedAccount && accountDefaultSource == QuickAddAccountDefaultSource.RECENT) {
                    Text(
                        "Suggested from recent ${if (txType == "INCOME") "income" else "expense"} transactions",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (txType == "TRANSFER") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("To account", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { destinationMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                eligibleAccounts.find { it.id == destAccountId }?.name ?: "Choose destination",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(expanded = destinationMenuExpanded, onDismissRequest = { destinationMenuExpanded = false }) {
                            eligibleAccounts.filter { it.id != accountId }.forEach { account ->
                                DropdownMenuItem(text = { Text(account.name) }, onClick = {
                                    destAccountId = account.id
                                    destinationMenuExpanded = false
                                    hasUserEdited = true
                                })
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (txType == "INCOME") "Income source" else "Category",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                eligibleCategories.find { it.id == categoryId }?.name
                                    ?: if (txType == "INCOME") "Choose income source" else "Choose category",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                            eligibleCategories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                    categoryId = category.id
                                        hasManuallySelectedCategory = true
                                        categoryWasSuggested = false
                                    categoryMenuExpanded = false
                                    hasUserEdited = true
                                })
                            }
                        }
                    }
                }
                    if (categoryWasSuggested && !hasManuallySelectedCategory) {
                        Text(
                            "Suggested from recent ${if (txType == "INCOME") "income" else "expense"} transactions",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }

            if (txType != "TRANSFER" && initialRecurringTransaction == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = repeatEnabled,
                        onClick = { repeatEnabled = !repeatEnabled; hasUserEdited = true },
                        label = { Text("Repeat this transaction") }
                    )
                    if (repeatEnabled) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { repeatFrequencyMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Repeats ${repeatFrequency.name.lowercase().replace('_', ' ')}")
                            }
                            DropdownMenu(
                                expanded = repeatFrequencyMenuExpanded,
                                onDismissRequest = { repeatFrequencyMenuExpanded = false }
                            ) {
                                TransactionRepeatFrequency.entries.forEach { frequency ->
                                    DropdownMenuItem(
                                        text = { Text(frequency.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                                        onClick = {
                                            repeatFrequency = frequency
                                            repeatFrequencyMenuExpanded = false
                                            hasUserEdited = true
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            "Future entries appear in Planning and are only posted when you confirm them.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(onClick = { showAdvancedDetails = !showAdvancedDetails }) {
                Text(if (showAdvancedDetails) "Hide details" else "Add details (optional)")
            }
            if (showAdvancedDetails) {
                if (txType == "TRANSFER") {
                    MoneyAmountField(
                        value = feeAmountStr,
                        onValueChange = { feeAmountStr = it; inputError = null; hasUserEdited = true },
                        label = "Transfer fee (optional)"
                    )
                    if (Money.parse(feeAmountStr)?.isPositive == true) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Fee category", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { feeCategoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        eligibleFeeCategories.find { it.id == feeCategoryId }?.name ?: "Choose fee category",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = feeCategoryMenuExpanded,
                                    onDismissRequest = { feeCategoryMenuExpanded = false }
                                ) {
                                    eligibleFeeCategories.forEach { category ->
                                        DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                            feeCategoryId = category.id
                                            feeCategoryMenuExpanded = false
                                            hasUserEdited = true
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (txType != "TRANSFER") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tags (optional)", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        if (eligibleTransactionTags.isEmpty()) {
                            Text(
                                "Create labels from Activity to use them here.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            eligibleTransactionTags.chunked(2).forEach { tagRow ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    tagRow.forEach { tag ->
                                        FilterChip(
                                            modifier = Modifier.weight(1f),
                                            selected = tag.id in selectedTagIds,
                                            onClick = {
                                                selectedTagIds = if (tag.id in selectedTagIds) {
                                                    selectedTagIds - tag.id
                                                } else {
                                                    selectedTagIds + tag.id
                                                }
                                                hasUserEdited = true
                                            },
                                            label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                    if (tagRow.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Counterparty (optional)", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = counterparty,
                                onValueChange = { counterparty = it; hasUserEdited = true },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (eligibleCounterparties.isNotEmpty()) {
                                        IconButton(
                                            onClick = { counterpartyMenuExpanded = true },
                                            modifier = Modifier.semantics { contentDescription = "Choose saved counterparty" }
                                        ) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = counterpartyMenuExpanded,
                                onDismissRequest = { counterpartyMenuExpanded = false }
                            ) {
                                eligibleCounterparties
                                    .filter { it.name.contains(counterparty, ignoreCase = true) }
                                    .forEach { savedCounterparty ->
                                        DropdownMenuItem(
                                            text = { Text(savedCounterparty.name) },
                                            onClick = {
                                                counterparty = savedCounterparty.name
                                                counterpartyMenuExpanded = false
                                                hasUserEdited = true
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
                FinancialDateTimeSelector(
                    timestamp = selectedTimestamp,
                    onTimestampChange = { selectedTimestamp = it; hasUserEdited = true }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; hasUserEdited = true },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FinancialNotesField(
                    value = notes,
                    onValueChange = { notes = it; hasUserEdited = true },
                    label = "Notes (optional)"
                )
            }

            inputError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = requestDismiss, enabled = !isSaving) {
                    Text("Cancel")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isSaving) return@Button
                        val amount = Money.parse(amountStr)
                        val fee = if (txType == "TRANSFER") {
                            if (feeAmountStr.isBlank()) Money.ZERO else Money.parse(feeAmountStr)
                        } else Money.ZERO
                        val validationError = when {
                            amount == null || !amount.isPositive -> "Enter a valid amount greater than zero."
                            accountId.isBlank() -> "Add or choose a financial account first."
                            txType != "TRANSFER" && categoryId.isBlank() -> "Choose a category or income source."
                            txType == "TRANSFER" && (destAccountId.isBlank() || destAccountId == accountId) -> "Choose a different destination account."
                            txType == "TRANSFER" && feeAmountStr.isNotBlank() && fee == null -> "Enter a valid transfer fee."
                            txType == "TRANSFER" && fee != null && fee.centavos < 0 -> "A transfer fee cannot be negative."
                            txType == "TRANSFER" && fee?.isPositive == true && feeCategoryId.isBlank() -> "Choose a category for the transfer fee."
                            else -> null
                        }
                        if (validationError != null) {
                            inputError = validationError
                            return@Button
                        }

                        val amountToSave = amount ?: return@Button
                        val feeToSave = fee ?: Money.ZERO
                        isSaving = true
                        scope.launch {
                            try {
                                when (txType) {
                                    "EXPENSE" -> appContainer.repository.recordExpense(
                                        accountId,
                                        categoryId,
                                        amountToSave,
                                        description,
                                        counterparty = counterparty.trim(),
                                        notes = notes,
                                        timestamp = selectedTimestamp,
                                        tagIds = selectedTagIds,
                                        submissionId = initialRecurringTransaction?.let { "${it.id}:${recurringOccurrenceIndex ?: it.nextOccurrenceIndex}" } ?: draftSubmissionId,
                                        repeatFrequency = repeatFrequency.takeIf { repeatEnabled },
                                        recurringTransactionId = initialRecurringTransaction?.id,
                                        recurrenceOccurrenceIndex = initialRecurringTransaction?.let { recurringOccurrenceIndex ?: it.nextOccurrenceIndex }
                                    )
                                    "INCOME" -> appContainer.repository.recordIncome(
                                        accountId,
                                        categoryId,
                                        amountToSave,
                                        description,
                                        counterparty = counterparty.trim(),
                                        notes = notes,
                                        timestamp = selectedTimestamp,
                                        tagIds = selectedTagIds,
                                        submissionId = initialRecurringTransaction?.let { "${it.id}:${recurringOccurrenceIndex ?: it.nextOccurrenceIndex}" } ?: draftSubmissionId,
                                        repeatFrequency = repeatFrequency.takeIf { repeatEnabled },
                                        recurringTransactionId = initialRecurringTransaction?.id,
                                        recurrenceOccurrenceIndex = initialRecurringTransaction?.let { recurringOccurrenceIndex ?: it.nextOccurrenceIndex }
                                    )
                                    else -> appContainer.repository.recordTransfer(
                                        accountId,
                                        destAccountId,
                                        amountToSave,
                                        fee = feeToSave,
                                        feeCategoryId = feeCategoryId.takeIf { feeToSave.isPositive },
                                        description = description,
                                        notes = notes,
                                        timestamp = selectedTimestamp,
                                        submissionId = draftSubmissionId
                                    )
                                }
                                onDismiss()
                            } catch (error: Exception) {
                                inputError = error.localizedMessage ?: "The transaction could not be saved."
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving…" else "Save transaction")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Discard this transaction?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    onDismiss()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    scope.launch { sheetState.show() }
                }) { Text("Keep editing") }
            }
        )
    }

    if (showTemplateManager) {
        TransactionTemplatesDialog(
            templates = transactionTemplates,
            accounts = accounts,
            categories = categories,
            message = templateMessage,
            onDismiss = {
                showTemplateManager = false
                templateMessage = null
            },
            onCreate = { template ->
                runTemplateOperation("Template saved.") {
                    appContainer.repository.createTransactionTemplate(template)
                    Unit
                }
            },
            onUpdate = { template ->
                runTemplateOperation("Template updated.") {
                    appContainer.repository.updateTransactionTemplate(template)
                }
            },
            onArchive = { id, isArchived ->
                runTemplateOperation(if (isArchived) "Template archived." else "Template restored.") {
                    appContainer.repository.setTransactionTemplateArchived(id, isArchived)
                }
            },
            onDelete = { id ->
                runTemplateOperation("Template deleted.") {
                    appContainer.repository.deleteTransactionTemplate(id)
                }
            },
            onUse = { template ->
                txType = template.transactionType.name
                amountStr = quickAddAmountInput(template.amount)
                accountId = template.accountId
                hasManuallySelectedAccount = true
                accountDefaultSource = QuickAddAccountDefaultSource.NONE
                destAccountId = template.destinationAccountId.orEmpty()
                categoryId = template.categoryId.orEmpty()
                hasManuallySelectedCategory = true
                categoryWasSuggested = false
                counterparty = template.counterparty
                description = template.description
                notes = template.notes
                feeAmountStr = template.fee.takeIf { it.isPositive }
                    ?.let { quickAddAmountInput(it) }.orEmpty()
                feeCategoryId = template.feeCategoryId.orEmpty()
                if (template.transactionType.name == "TRANSFER") selectedTagIds = emptySet()
                showAdvancedDetails = true
                inputError = null
                hasUserEdited = true
            },
            onClearMessage = { templateMessage = null }
        )
    }
}

private fun quickAddAmountInput(money: Money): String =
    java.math.BigDecimal.valueOf(money.centavos, 2).toPlainString()
