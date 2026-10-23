package com.ralledger.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionTypeFilter
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.model.TransactionTag
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private enum class FilterDateTarget { FROM, TO }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onManageCategories: () -> Unit
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val accountFilterId by viewModel.accountFilterId.collectAsState()
    val categoryFilterId by viewModel.categoryFilterId.collectAsState()
    val tagFilterId by viewModel.tagFilterId.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val tagMessage by viewModel.tagMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var filterDateTarget by remember { mutableStateOf<FilterDateTarget?>(null) }
    var reverseCandidate by remember { mutableStateOf<Transaction?>(null) }
    var correctCandidate by remember { mutableStateOf<Transaction?>(null) }
    var duplicateCandidate by remember { mutableStateOf<Transaction?>(null) }
    var showTagManager by remember { mutableStateOf(false) }
    var tagAssignmentCandidate by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Transaction Journal",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row {
                    TextButton(onClick = onManageCategories) { Text("Categories") }
                    TextButton(onClick = { showTagManager = true }) { Text("Labels") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Search transactions") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(Icons.Default.FilterList, contentDescription = if (showFilters) "Hide filters" else "Show filters")
                }
            }

            if (showFilters) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            TransactionTypeFilter.ALL to "All",
                            TransactionTypeFilter.INCOME to "Income",
                            TransactionTypeFilter.EXPENSE to "Expenses",
                            TransactionTypeFilter.TRANSFER to "Transfers",
                            TransactionTypeFilter.OTHER to "Other"
                        ).forEach { (filter, label) ->
                            FilterChip(
                                selected = typeFilter == filter,
                                onClick = { viewModel.setTypeFilter(filter) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterDropdownChip(
                            title = "Account",
                            selectedId = accountFilterId,
                            options = listOf<Pair<String?, String>>(null to "All accounts") + accounts.map { it.id to it.name },
                            onSelect = viewModel::setAccountFilter
                        )
                        FilterDropdownChip(
                            title = "Category",
                            selectedId = categoryFilterId,
                            options = listOf<Pair<String?, String>>(null to "All categories") + categories.map { it.id to it.name },
                            onSelect = viewModel::setCategoryFilter
                        )
                        FilterDropdownChip(
                            title = "Label",
                            selectedId = tagFilterId,
                            options = listOf<Pair<String?, String>>(null to "All labels") + tags.map { it.id to (it.name + if (it.isArchived) " · Archived" else "") },
                            onSelect = viewModel::setTagFilter
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { filterDateTarget = FilterDateTarget.FROM }) {
                            Text("From: ${dateRange.first?.let(::formatLocalDate) ?: "Any date"}")
                        }
                        TextButton(onClick = { filterDateTarget = FilterDateTarget.TO }) {
                            Text("To: ${dateRange.second?.let { formatLocalDate(it - 1L) } ?: "Any date"}")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearFilters) { Text("Clear") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transactions.isEmpty()) {
                Text("No transactions found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { tx ->
                        TransactionRow(
                            transaction = tx,
                            onCorrect = { correctCandidate = tx },
                            onDuplicate = { duplicateCandidate = tx },
                            onReverse = { reverseCandidate = tx },
                            onManageTags = { tagAssignmentCandidate = tx }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || correctCandidate != null || duplicateCandidate != null) {
        val correctionSource = correctCandidate
        val initialTransaction = correctionSource ?: duplicateCandidate
        RecordTransactionDialog(
            accounts = accounts,
            categories = categories,
            initialTransaction = initialTransaction,
            isCorrection = correctionSource != null,
            onDismiss = {
                showAddDialog = false
                correctCandidate = null
                duplicateCandidate = null
            },
            onIncome = { accountId, catId, amount, desc, counterparty, notes, timestamp ->
                viewModel.recordIncome(accountId, catId, amount, desc, counterparty, notes, timestamp)
                showAddDialog = false
                duplicateCandidate = null
            },
            onExpense = { accountId, catId, amount, desc, counterparty, notes, timestamp ->
                viewModel.recordExpense(accountId, catId, amount, desc, counterparty, notes, timestamp)
                showAddDialog = false
                duplicateCandidate = null
            },
            onTransfer = { srcId, destId, amount, fee, feeCatId, desc, notes, timestamp ->
                viewModel.recordTransfer(srcId, destId, amount, fee, feeCatId, desc, notes, timestamp)
                showAddDialog = false
                duplicateCandidate = null
            },
            onCorrect = { correction ->
                correctionSource?.let { viewModel.correctTransaction(it.journalEntryId, correction) }
                correctCandidate = null
            }
        )
    }

    if (showTagManager) {
        TransactionTagsManagerDialog(
            tags = tags,
            onDismiss = { showTagManager = false },
            onCreate = viewModel::createTag,
            onUpdate = viewModel::updateTag,
            onArchive = viewModel::setTagArchived,
            onDelete = viewModel::deleteUnusedTag,
            onMerge = viewModel::mergeTags
        )
    }

    tagAssignmentCandidate?.let { transaction ->
        TransactionTagAssignmentDialog(
            transaction = transaction,
            tags = tags,
            onDismiss = { tagAssignmentCandidate = null },
            onSave = { tagIds ->
                viewModel.setTransactionTags(transaction.journalEntryId, tagIds)
                tagAssignmentCandidate = null
            }
        )
    }

    reverseCandidate?.let { transaction ->
        AlertDialog(
            onDismissRequest = { reverseCandidate = null },
            title = { Text("Void transaction?") },
            text = { Text("The original stays in your journal. RalLedger will add an offsetting reversal so the account balance remains correct. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.reverseTransaction(transaction.journalEntryId)
                    reverseCandidate = null
                }) { Text("Reverse") }
            },
            dismissButton = { TextButton(onClick = { reverseCandidate = null }) { Text("Cancel") } }
        )
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Transaction update") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }

    tagMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearTagMessage,
            title = { Text("Transaction labels") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearTagMessage) { Text("OK") } }
        )
    }

    filterDateTarget?.let { target ->
        key(target) {
            val existingDate = when (target) {
                FilterDateTarget.FROM -> dateRange.first
                FilterDateTarget.TO -> dateRange.second?.let { it - 1L }
            } ?: System.currentTimeMillis()
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = localTimestampToPickerDateMillis(existingDate))
            DatePickerDialog(
                onDismissRequest = { filterDateTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            val dayStart = pickerDateToLocalDayStart(selected)
                            when (target) {
                                FilterDateTarget.FROM -> viewModel.setDateRange(dayStart, dateRange.second)
                                FilterDateTarget.TO -> viewModel.setDateRange(dateRange.first, nextLocalDay(dayStart))
                            }
                        }
                        filterDateTarget = null
                    }) { Text("Set date") }
                },
                dismissButton = { TextButton(onClick = { filterDateTarget = null }) { Text("Cancel") } }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

@Composable
private fun FilterDropdownChip(
    title: String,
    selectedId: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "All"
    Box {
        FilterChip(
            selected = selectedId != null,
            onClick = { expanded = true },
            label = { Text("$title: $selectedLabel") }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatLocalDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

private fun localTimestampToPickerDateMillis(timestamp: Long): Long {
    val localDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(localDate.get(Calendar.YEAR), localDate.get(Calendar.MONTH), localDate.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

private fun pickerDateToLocalDayStart(pickerMillis: Long): Long {
    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun pickerDateToLocalTimestamp(pickerMillis: Long, preserveTimeFrom: Long): Long {
    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    val local = Calendar.getInstance().apply { timeInMillis = preserveTimeFrom }
    val hour = local.get(Calendar.HOUR_OF_DAY)
    val minute = local.get(Calendar.MINUTE)
    val second = local.get(Calendar.SECOND)
    val millisecond = local.get(Calendar.MILLISECOND)
    local.set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH), hour, minute, second)
    local.set(Calendar.MILLISECOND, millisecond)
    return local.timeInMillis
}

private fun nextLocalDay(dayStart: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onCorrect: () -> Unit,
    onDuplicate: () -> Unit,
    onReverse: () -> Unit,
    onManageTags: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val isPositive = transaction.transactionType == TransactionType.INCOME ||
        transaction.transactionType == TransactionType.INTEREST_CREDIT ||
        transaction.transactionType == TransactionType.RECEIVABLE_COLLECTION ||
        transaction.transactionType == TransactionType.LOAN_DISBURSEMENT ||
        transaction.transactionType == TransactionType.OPENING_BALANCE
    val amountColor = when {
        isPositive -> MaterialTheme.colorScheme.secondary
        transaction.transactionType == TransactionType.TRANSFER || transaction.transactionType == TransactionType.REVERSAL -> MaterialTheme.colorScheme.primary
        transaction.transactionType == TransactionType.BALANCE_ADJUSTMENT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    var actionsExpanded by remember(transaction.journalEntryId) { mutableStateOf(false) }
    val canCorrect = transaction.transactionType in setOf(
        TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank { transaction.transactionType.displayName },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${transaction.accountName}${transaction.destinationAccountName?.let { " ➔ $it" } ?: ""} | ${transaction.categoryName ?: transaction.transactionType.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.transactionType == TransactionType.INTEREST_CREDIT) {
                    Text(
                        text = "Gross ${transaction.amount.format()} · tax withheld ${transaction.fee.format()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = dateFormat.format(Date(transaction.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                if (transaction.tagNames.isNotEmpty()) {
                    Text(
                        text = transaction.tagNames.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${when (transaction.transactionType) {
                        TransactionType.INCOME, TransactionType.INTEREST_CREDIT, TransactionType.RECEIVABLE_COLLECTION, TransactionType.LOAN_DISBURSEMENT -> "+"
                        TransactionType.EXPENSE, TransactionType.SUBSCRIPTION_PAYMENT, TransactionType.RECEIVABLE_WRITEOFF, TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT -> "−"
                        TransactionType.OPENING_BALANCE, TransactionType.REVERSAL, TransactionType.TRANSFER,
                        TransactionType.BALANCE_ADJUSTMENT, TransactionType.RECEIVABLE_RECOGNITION,
                        TransactionType.PAYABLE_RECOGNITION -> ""
                    }}${if (transaction.transactionType == TransactionType.INTEREST_CREDIT) (transaction.amount - transaction.fee).format() else transaction.amount.format()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                run {
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Transaction actions")
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            DropdownMenuItem(text = { Text("Manage labels") }, onClick = { actionsExpanded = false; onManageTags() })
                            if (canCorrect && !transaction.isReversed) {
                                DropdownMenuItem(text = { Text("Correct") }, onClick = { actionsExpanded = false; onCorrect() })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { actionsExpanded = false; onDuplicate() })
                            }
                            if (transaction.transactionType != TransactionType.REVERSAL && !transaction.isReversed) {
                                DropdownMenuItem(text = { Text("Void with reversal") }, onClick = { actionsExpanded = false; onReverse() })
                            }
                        }
                    }
                    if (transaction.isReversed) {
                        Text("Reversed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTransactionDialog(
    accounts: List<Account>,
    categories: List<Category>,
    initialTransaction: Transaction?,
    isCorrection: Boolean,
    onDismiss: () -> Unit,
    onIncome: (accountId: String, categoryId: String, amount: Money, desc: String, counterparty: String, notes: String, timestamp: Long) -> Unit,
    onExpense: (accountId: String, categoryId: String, amount: Money, desc: String, counterparty: String, notes: String, timestamp: Long) -> Unit,
    onTransfer: (srcId: String, destId: String, amount: Money, fee: Money, feeCatId: String?, desc: String, notes: String, timestamp: Long) -> Unit,
    onCorrect: (TransactionCorrection) -> Unit
) {
    val transactionKey = initialTransaction?.journalEntryId
    var selectedTab by remember(transactionKey, isCorrection) {
        mutableStateOf(
            when (initialTransaction?.transactionType) {
                TransactionType.INCOME -> 1
                TransactionType.TRANSFER -> 2
                else -> 0
            }
        )
    }
    val eligibleAccounts = accounts.filter { account ->
        account.accountType.isAsset && !account.accountType.isReceivable &&
            (!account.isArchived || (isCorrection && account.id in setOf(initialTransaction?.accountId, initialTransaction?.destinationAccountId)))
    }
    var selectedAccountId by remember(transactionKey, isCorrection) {
        mutableStateOf(initialTransaction?.accountId ?: eligibleAccounts.firstOrNull()?.id.orEmpty())
    }
    var selectedDestAccountId by remember(transactionKey, isCorrection) {
        mutableStateOf(initialTransaction?.destinationAccountId ?: eligibleAccounts.firstOrNull { it.id != selectedAccountId }?.id.orEmpty())
    }
    var selectedCategoryId by remember(transactionKey, isCorrection) {
        mutableStateOf(initialTransaction?.categoryId ?: categories.firstOrNull {
            it.type.name == (if (selectedTab == 1) "INCOME" else "EXPENSE") && !it.isArchived
        }?.id.orEmpty())
    }
    var selectedFeeCategoryId by remember(transactionKey, isCorrection) {
        mutableStateOf(initialTransaction?.categoryId ?: categories.firstOrNull { it.type.name == "EXPENSE" && !it.isArchived }?.id.orEmpty())
    }
    var amountText by remember(transactionKey, isCorrection) { mutableStateOf(initialTransaction?.amount?.let(::moneyForInput).orEmpty()) }
    var feeText by remember(transactionKey, isCorrection) { mutableStateOf(initialTransaction?.fee?.takeIf { it.isPositive }?.let(::moneyForInput).orEmpty()) }
    var description by remember(transactionKey, isCorrection) { mutableStateOf(initialTransaction?.description.orEmpty()) }
    var counterparty by remember(transactionKey, isCorrection) { mutableStateOf(initialTransaction?.counterparty.orEmpty()) }
    var notes by remember(transactionKey, isCorrection) { mutableStateOf(initialTransaction?.notes.orEmpty()) }
    var selectedTimestamp by remember(transactionKey, isCorrection) {
        mutableStateOf(if (isCorrection) initialTransaction?.timestamp ?: System.currentTimeMillis() else System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val categoryType = if (selectedTab == 1) "INCOME" else "EXPENSE"
    val visibleCategories = categories.filter { category ->
        category.type.name == categoryType &&
            (!category.isArchived || (isCorrection && category.id == initialTransaction?.categoryId))
    }
    val visibleFeeCategories = categories.filter { category ->
        category.type.name == "EXPENSE" &&
            (!category.isArchived || (isCorrection && category.id == initialTransaction?.categoryId))
    }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(eligibleAccounts) {
        if (eligibleAccounts.none { it.id == selectedAccountId }) selectedAccountId = eligibleAccounts.firstOrNull()?.id.orEmpty()
        if (eligibleAccounts.none { it.id == selectedDestAccountId }) {
            selectedDestAccountId = eligibleAccounts.firstOrNull { it.id != selectedAccountId }?.id.orEmpty()
        }
    }
    LaunchedEffect(visibleCategories) {
        if (selectedTab != 2 && visibleCategories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = visibleCategories.firstOrNull()?.id.orEmpty()
        }
        if (visibleFeeCategories.none { it.id == selectedFeeCategoryId }) {
            selectedFeeCategoryId = visibleFeeCategories.firstOrNull()?.id.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when {
                isCorrection -> "Correct transaction"
                initialTransaction != null -> "Duplicate transaction"
                else -> "Record transaction"
            })
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isCorrection) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = {
                            selectedTab = 0
                            selectedCategoryId = categories.firstOrNull { it.type.name == "EXPENSE" && !it.isArchived }?.id.orEmpty()
                        }, text = { Text("Expense") })
                        Tab(selected = selectedTab == 1, onClick = {
                            selectedTab = 1
                            selectedCategoryId = categories.firstOrNull { it.type.name == "INCOME" && !it.isArchived }?.id.orEmpty()
                        }, text = { Text("Income") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Transfer") })
                    }
                }

                AccountDropdown(
                    label = if (selectedTab == 2) "From Account" else "Account",
                    accounts = eligibleAccounts,
                    selectedId = selectedAccountId,
                    onSelect = { selectedAccountId = it }
                )

                if (selectedTab == 2) {
                    AccountDropdown(
                        label = "To Account",
                        accounts = eligibleAccounts,
                        selectedId = selectedDestAccountId,
                        onSelect = { selectedDestAccountId = it }
                    )
                } else {
                    CategoryDropdown(
                        categories = visibleCategories,
                        selectedId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it }
                    )
                }

                if (selectedTab == 2) {
                    CategoryDropdown(
                        categories = visibleFeeCategories,
                        selectedId = selectedFeeCategoryId,
                        onSelect = { selectedFeeCategoryId = it },
                        label = "Fee Category"
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; inputError = null },
                    label = { Text("Amount (e.g. 250.00)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedTab == 2) {
                    OutlinedTextField(
                        value = feeText,
                        onValueChange = { feeText = it; inputError = null },
                        label = { Text("Transfer Fee (optional, e.g. 15.00)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedTab != 2) {
                    OutlinedTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it },
                        label = { Text("Source or counterparty") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Date: ${dateFormatter.format(Date(selectedTimestamp))}")
                }
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = Money.parse(amountText)
                    val fee = if (selectedTab == 2) {
                        if (feeText.isBlank()) Money.ZERO else Money.parse(feeText)
                    } else Money.ZERO
                    when {
                        amount == null || !amount.isPositive -> inputError = "Enter a valid amount greater than zero."
                        selectedAccountId.isBlank() -> inputError = "Choose an account."
                        selectedTab != 2 && selectedCategoryId.isBlank() -> inputError = if (selectedTab == 1) "Choose an income source." else "Choose an expense category."
                        selectedTab == 2 && (fee == null || fee.isNegative) -> inputError = "Enter a valid non-negative transfer fee."
                        selectedTab == 2 && (selectedDestAccountId.isBlank() || selectedDestAccountId == selectedAccountId) -> inputError = "Choose a different destination account."
                        selectedTab == 2 && fee!!.isPositive && selectedFeeCategoryId.isBlank() -> inputError = "Choose an expense category for the fee."
                        isCorrection -> {
                            val correctionType = when (selectedTab) {
                                1 -> TransactionType.INCOME
                                2 -> TransactionType.TRANSFER
                                else -> TransactionType.EXPENSE
                            }
                            onCorrect(
                                TransactionCorrection(
                                    transactionType = correctionType,
                                    timestamp = selectedTimestamp,
                                    amount = amount,
                                    fee = fee ?: Money.ZERO,
                                    accountId = selectedAccountId,
                                    destinationAccountId = if (selectedTab == 2) selectedDestAccountId else null,
                                    categoryId = if (selectedTab == 2) selectedFeeCategoryId.ifBlank { null } else selectedCategoryId,
                                    counterparty = counterparty,
                                    description = description,
                                    notes = notes
                                )
                            )
                        }
                        selectedTab == 0 -> onExpense(selectedAccountId, selectedCategoryId, amount, description, counterparty, notes, selectedTimestamp)
                        selectedTab == 1 -> onIncome(selectedAccountId, selectedCategoryId, amount, description, counterparty, notes, selectedTimestamp)
                        else -> {
                            onTransfer(selectedAccountId, selectedDestAccountId, amount, fee ?: Money.ZERO, selectedFeeCategoryId.ifBlank { null }, description, notes, selectedTimestamp)
                        }
                    }
                }
            ) {
                Text(if (isCorrection) "Save correction" else if (initialTransaction != null) "Create duplicate" else "Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = localTimestampToPickerDateMillis(selectedTimestamp))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedTimestamp = pickerDateToLocalTimestamp(it, selectedTimestamp) }
                    showDatePicker = false
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun moneyForInput(money: Money): String = BigDecimal.valueOf(money.centavos, 2).toPlainString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.find { it.id == selectedId }?.name ?: "Select Account"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name) },
                    onClick = {
                        onSelect(acc.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    label: String = "Category"
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.name ?: "Select Category"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionTagAssignmentDialog(
    transaction: Transaction,
    tags: List<TransactionTag>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedIds by remember(transaction.journalEntryId) { mutableStateOf(transaction.tagIds) }
    val availableTags = tags.filter { !it.isArchived || it.id in transaction.tagIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels for ${transaction.description.ifBlank { "transaction" }}") },
        text = {
            if (availableTags.isEmpty()) {
                Text("Create a label first to organize and filter transactions.")
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableTags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(16.dp)
                                    .background(parseTagColor(tag.colorHex), CircleShape)
                            )
                            Checkbox(
                                checked = tag.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + tag.id else selectedIds - tag.id
                                }
                            )
                            Text(tag.name + if (tag.isArchived) " · Archived" else "")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedIds) }, enabled = availableTags.isNotEmpty() || selectedIds.isEmpty()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TransactionTagsManagerDialog(
    tags: List<TransactionTag>,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onMerge: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TransactionTag?>(null) }
    var mergeSource by remember { mutableStateOf<TransactionTag?>(null) }
    var deleteCandidate by remember { mutableStateOf<TransactionTag?>(null) }
    val visibleTags = tags.filter { it.name.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction labels") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search labels") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { editingTag = null; showEditor = true }) { Text("Add label") }
                if (visibleTags.isEmpty()) {
                    Text(if (tags.isEmpty()) "No labels yet." else "No matching labels.")
                }
                visibleTags.forEach { tag ->
                    TransactionTagManagerRow(
                        tag = tag,
                        onEdit = { editingTag = tag; showEditor = true },
                        onArchive = { onArchive(tag.id, !tag.isArchived) },
                        onDelete = { deleteCandidate = tag },
                        onMerge = { mergeSource = tag }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (showEditor) {
        TransactionTagEditorDialog(
            tag = editingTag,
            onDismiss = { showEditor = false },
            onSave = { name, color ->
                val current = editingTag
                if (current == null) onCreate(name, color) else onUpdate(current.id, name, color)
                showEditor = false
            }
        )
    }

    mergeSource?.let { source ->
        val targets = tags.filter { it.id != source.id && !it.isArchived }
        AlertDialog(
            onDismissRequest = { mergeSource = null },
            title = { Text("Merge ${source.name}") },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Its transaction labels will move to the selected label. Duplicate assignments will be combined, and the source label will be removed.")
                    targets.forEach { target ->
                        TextButton(
                            onClick = {
                                onMerge(source.id, target.id)
                                mergeSource = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Merge into ${target.name}") }
                    }
                    if (targets.isEmpty()) Text("Create or restore another label first.")
                }
            },
            confirmButton = { TextButton(onClick = { mergeSource = null }) { Text("Cancel") } }
        )
    }

    deleteCandidate?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${tag.name}?") },
            text = { Text("Only unused labels can be deleted. Labels attached to transactions must be removed from those transactions first, or archived to preserve history.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tag.id)
                    deleteCandidate = null
                }) { Text("Delete if unused") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TransactionTagManagerRow(
    tag: TransactionTag,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit
) {
    var menuExpanded by remember(tag.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp)
                    .background(parseTagColor(tag.colorHex), CircleShape)
            )
            Spacer(Modifier.size(10.dp))
            Text(tag.name + if (tag.isArchived) " · Archived" else "", modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${tag.name}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Merge into…") }, onClick = { menuExpanded = false; onMerge() })
                    DropdownMenuItem(text = { Text(if (tag.isArchived) "Restore" else "Archive") }, onClick = { menuExpanded = false; onArchive() })
                    DropdownMenuItem(text = { Text("Delete if unused") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

private fun parseTagColor(colorHex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrElse { Color.Gray }

@Composable
private fun TransactionTagEditorDialog(
    tag: TransactionTag?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var colorHex by remember(tag?.id) { mutableStateOf(tag?.colorHex ?: "#6750A4") }
    var validationMessage by remember(tag?.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "New label" else "Edit label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationMessage = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it; validationMessage = null },
                    label = { Text("Color (hex)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validationMessage = when {
                    name.isBlank() -> "Enter a label name."
                    name.trim().length > 40 -> "Use 40 characters or fewer."
                    !colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$")) -> "Enter a color like #6750A4."
                    else -> null
                }
                if (validationMessage == null) onSave(name.trim(), colorHex)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
