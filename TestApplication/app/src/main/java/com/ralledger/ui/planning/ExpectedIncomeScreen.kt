package com.ralledger.ui.planning
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.ExpectedIncome
import com.ralledger.domain.model.ExpectedIncomeStatus
import com.ralledger.domain.model.Money
import com.ralledger.ui.components.MoneyCalculatorAction
import java.util.Calendar
import java.util.Date
import com.ralledger.ui.settings.displayDate

private enum class ExpectedIncomeAction { CONVERT, CANCEL, DELETE }
private data class PendingExpectedIncomeAction(val item: ExpectedIncome, val action: ExpectedIncomeAction)

@Composable
fun ExpectedIncomeScreen(
    viewModel: ExpectedIncomeViewModel,
    onViewReceivables: () -> Unit
) {
    val items by viewModel.expectedIncome.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpectedIncome?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingExpectedIncomeAction?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    val filtered = items.filter { item ->
        item.isArchived == showArchived &&
            (query.isBlank() || listOf(item.description, item.counterparty, item.incomeSourceName, item.status.displayName)
                .any { it.contains(query, ignoreCase = true) })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; editorOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add expected income")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search expected income") },
                    singleLine = true
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { showArchived = false },
                        label = { Text("Active") }
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = true },
                        label = { Text("Archived") }
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} records", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (query.isBlank()) "No expected income" else "No matching income", fontWeight = FontWeight.SemiBold)
                            Text("Add expected payments here, then convert confirmed amounts to receivables.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    ExpectedIncomeCard(
                        item = item,
                        onEdit = { editing = item; editorOpen = true },
                        onConvert = { pendingAction = PendingExpectedIncomeAction(item, ExpectedIncomeAction.CONVERT) },
                        onCancel = { pendingAction = PendingExpectedIncomeAction(item, ExpectedIncomeAction.CANCEL) },
                        onDelete = { pendingAction = PendingExpectedIncomeAction(item, ExpectedIncomeAction.DELETE) },
                        onArchive = { viewModel.archive(item, !item.isArchived) },
                        onReactivate = { viewModel.reactivate(item) },
                        onViewReceivable = onViewReceivables
                    )
                }
            }
        }
    }

    if (editorOpen) {
        ExpectedIncomeEditorDialog(
            item = editing,
            categories = categories,
            counterparties = counterparties,
            onDismiss = { editorOpen = false },
            onSave = { description, counterparty, amount, date, categoryId, counterpartyId ->
                val existing = editing
                if (existing == null) viewModel.create(description, counterparty, amount, date, categoryId, counterpartyId)
                else viewModel.update(existing, description, counterparty, amount, date, categoryId, counterpartyId)
                editorOpen = false
            }
        )
    }

    pendingAction?.let { pending ->
        val title: String
        val details: String
        val actionLabel: String
        when (pending.action) {
            ExpectedIncomeAction.CONVERT -> {
                title = "Convert to receivable?"
                details = "This creates a receivable and recognizes ${displayMoney(pending.item.amount)} as income now. The forecast and receivable will stay linked."
                actionLabel = "Convert"
            }
            ExpectedIncomeAction.CANCEL -> {
                title = "Cancel expected income?"
                details = "${pending.item.description} will be marked cancelled. Its record can be reactivated later."
                actionLabel = "Cancel forecast"
            }
            ExpectedIncomeAction.DELETE -> {
                title = "Delete expected income?"
                details = "${pending.item.description} will be permanently removed. It has not created a receivable or journal entry."
                actionLabel = "Delete"
            }
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text(details) },
            confirmButton = {
                Button(onClick = {
                    when (pending.action) {
                        ExpectedIncomeAction.CONVERT -> viewModel.convert(pending.item)
                        ExpectedIncomeAction.CANCEL -> viewModel.cancel(pending.item)
                        ExpectedIncomeAction.DELETE -> viewModel.delete(pending.item)
                    }
                    pendingAction = null
                }) { Text(actionLabel) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun ExpectedIncomeCard(
    item: ExpectedIncome,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit,
    onViewReceivable: () -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    val isOverdue = item.status == ExpectedIncomeStatus.PLANNED && isBeforeToday(item.expectedDate)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(item.incomeSourceName, item.counterparty).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(displayMoney(item.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Manage expected income")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!item.isArchived && item.status == ExpectedIncomeStatus.PLANNED) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                            DropdownMenuItem(text = { Text("Convert to receivable") }, onClick = { menuExpanded = false; onConvert() })
                            DropdownMenuItem(text = { Text("Cancel") }, onClick = { menuExpanded = false; onCancel() })
                        }
                        if (!item.isArchived && item.status == ExpectedIncomeStatus.CANCELLED) {
                            DropdownMenuItem(text = { Text("Reactivate") }, onClick = { menuExpanded = false; onReactivate() })
                        }
                        if (item.status == ExpectedIncomeStatus.CONVERTED) {
                            DropdownMenuItem(text = { Text("View receivables") }, onClick = { menuExpanded = false; onViewReceivable() })
                        }
                        DropdownMenuItem(
                            text = { Text(if (item.isArchived) "Restore" else "Archive") },
                            onClick = { menuExpanded = false; onArchive() }
                        )
                        if (item.status != ExpectedIncomeStatus.CONVERTED) {
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Expected ${formatExpectedDate(item.expectedDate)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                val statusText = when {
                    item.isArchived -> "Archived"
                    isOverdue -> "Overdue"
                    else -> item.status.displayName
                }
                Text(statusText, color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpectedIncomeEditorDialog(
    item: ExpectedIncome?,
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onSave: (String, String, Money, Long, String, String?) -> Unit
) {
    var description by remember(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var counterparty by remember(item?.id) { mutableStateOf(item?.counterparty.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.centavos?.let { Money(it).format() }.orEmpty()) }
    var expectedDate by remember(item?.id) { mutableStateOf(item?.expectedDate ?: System.currentTimeMillis() + 30L * 86_400_000L) }
    val incomeCategories = categories.filter { it.type == CategoryType.INCOME && (!it.isArchived || it.id == item?.incomeCategoryId) }
    var categoryId by remember(item?.id, incomeCategories) { mutableStateOf(item?.incomeCategoryId ?: incomeCategories.firstOrNull()?.id.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var counterpartyExpanded by remember { mutableStateOf(false) }
    var counterpartyId by remember(item?.id) { mutableStateOf(item?.counterpartyId) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val datePickerState = if (datePickerOpen) rememberDatePickerState(initialSelectedDateMillis = expectedDate) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add expected income" else "Edit expected income") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(description, { description = it; error = null }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    counterparty,
                    { counterparty = it; counterpartyId = null },
                    label = { Text("Payer or client (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = counterpartyExpanded,
                    onExpandedChange = { counterpartyExpanded = !counterpartyExpanded }
                ) {
                    OutlinedTextField(
                        value = counterparties.firstOrNull { it.id == counterpartyId }?.name ?: "No saved client linked",
                        onValueChange = {}, readOnly = true, label = { Text("Link saved client") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = counterpartyExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = counterpartyExpanded, onDismissRequest = { counterpartyExpanded = false }) {
                        DropdownMenuItem(text = { Text("No saved client") }, onClick = { counterpartyId = null; counterpartyExpanded = false })
                        counterparties.filter { !it.isArchived || it.id == item?.counterpartyId }.forEach { saved ->
                            DropdownMenuItem(
                                text = { Text(saved.name + if (saved.isArchived) " (archived)" else "") },
                                onClick = { counterparty = saved.name; counterpartyId = saved.id; counterpartyExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    amountText, { amountText = it; error = null },
                    label = { Text("Expected amount (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { MoneyCalculatorAction(amountText) { amountText = it; error = null } },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                    val selectedName = incomeCategories.firstOrNull { it.id == categoryId }?.name ?: "Choose income source"
                    OutlinedTextField(
                        value = selectedName, onValueChange = {}, readOnly = true,
                        label = { Text("Income source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        incomeCategories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; categoryExpanded = false })
                        }
                    }
                }
                TextButton(onClick = { datePickerOpen = true }) { Text("Expected date: ${formatExpectedDate(expectedDate)}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                when {
                    description.isBlank() -> error = "Enter a description."
                    amount == null || !amount.isPositive -> error = "Enter an amount greater than zero."
                    categoryId.isBlank() -> error = "Choose an income source."
                    else -> onSave(description.trim(), counterparty.trim(), amount, expectedDate, categoryId, counterpartyId)
                }
            }) { Text(if (item == null) "Save forecast" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (datePickerOpen && datePickerState != null) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = { datePickerState.selectedDateMillis?.let { expectedDate = it }; datePickerOpen = false }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }
}

@Composable
private fun formatExpectedDate(timestamp: Long): String = displayDate(timestamp)

private fun isBeforeToday(timestamp: Long): Boolean {
    val expectedDate = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return expectedDate.before(today)
}
