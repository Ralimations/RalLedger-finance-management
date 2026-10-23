package com.ralledger.ui.receivables
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.ralledger.ui.settings.displayDate

@Composable
fun ReceivablesScreen(viewModel: ReceivablesViewModel) {
    val receivables by viewModel.receivables.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var includeArchived by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Receivable?>(null) }
    var collecting by remember { mutableStateOf<Receivable?>(null) }
    var writingOff by remember { mutableStateOf<Receivable?>(null) }
    var cancelling by remember { mutableStateOf<Receivable?>(null) }
    var pendingReversal by remember { mutableStateOf<Pair<String, String>?>(null) }

    val visibleReceivables = receivables.filter { receivable ->
        (includeArchived || !receivable.isArchived) &&
            (search.isBlank() || receivable.clientName.contains(search, true) || receivable.description.contains(search, true))
    }
    val activeAccounts = accounts.filter { it.accountType.isLiquid && !it.isArchived }
    val incomeCategories = categories.filter { it.type == CategoryType.INCOME && !it.isArchived }
    val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add receivable")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Receivables", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search clients and notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
                selected = includeArchived,
                onClick = { includeArchived = !includeArchived },
                label = { Text(if (includeArchived) "Showing archived" else "Show archived") }
            )
            if (visibleReceivables.isEmpty()) {
                Text(
                    if (search.isNotBlank()) "No matching receivables."
                    else if (includeArchived) "No receivables found."
                    else "No receivables yet."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleReceivables, key = { it.id }) { receivable ->
                        ReceivableCard(
                            receivable = receivable,
                            accounts = accounts,
                            categories = categories,
                            onEdit = { editing = receivable; showEditor = true },
                            onCollect = { collecting = receivable },
                            onWriteOff = { writingOff = receivable },
                            onCancel = { cancelling = receivable },
                            onArchive = { viewModel.setArchived(receivable, true) },
                            onRestore = { viewModel.setArchived(receivable, false) },
                            onReverse = { journalId, label -> pendingReversal = journalId to label }
                        )
                    }
                    item { Spacer(Modifier.height(76.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        ReceivableEditorDialog(
            receivable = editing,
            categories = incomeCategories,
            counterparties = counterparties,
            onDismiss = { showEditor = false },
            onConfirm = { client, amount, dueDate, description, categoryId, counterpartyId ->
                val current = editing
                if (current == null) {
                    viewModel.createReceivable(client, amount, dueDate, categoryId, description, counterpartyId)
                } else {
                    viewModel.updateReceivable(current.id, client, dueDate, description, counterpartyId)
                }
                showEditor = false
            }
        )
    }

    collecting?.let { receivable ->
        CollectPaymentDialog(
            receivable = receivable,
            accounts = activeAccounts,
            onDismiss = { collecting = null },
            onConfirm = { accountId, amount, notes ->
                viewModel.collectReceivable(receivable.id, accountId, amount, notes)
                collecting = null
            }
        )
    }

    writingOff?.let { receivable ->
        WriteOffDialog(
            receivable = receivable,
            categories = expenseCategories,
            onDismiss = { writingOff = null },
            onConfirm = { amount, categoryId, notes ->
                viewModel.writeOffReceivable(receivable.id, amount, categoryId, notes)
                writingOff = null
            }
        )
    }

    cancelling?.let { receivable ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel ${receivable.clientName}?") },
            text = {
                Text("This posts a reversing journal entry and preserves the receivable and its history. Reverse any active collections or write-offs first.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelReceivable(receivable); cancelling = null }) { Text("Cancel receivable") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep open") } }
        )
    }

    pendingReversal?.let { (journalId, label) ->
        AlertDialog(
            onDismissRequest = { pendingReversal = null },
            title = { Text("Reverse $label?") },
            text = { Text("A reversing journal entry will be posted. The original history will remain visible.") },
            confirmButton = {
                TextButton(onClick = { viewModel.reverseEntry(journalId); pendingReversal = null }) { Text("Reverse entry") }
            },
            dismissButton = { TextButton(onClick = { pendingReversal = null }) { Text("Keep entry") } }
        )
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Receivable") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun ReceivableCard(
    receivable: Receivable,
    accounts: List<Account>,
    categories: List<Category>,
    onEdit: () -> Unit,
    onCollect: () -> Unit,
    onWriteOff: () -> Unit,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onReverse: (journalId: String, label: String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    val hasHistory = receivable.payments.isNotEmpty() || receivable.writeOffs.isNotEmpty()
    val canCollect = !receivable.isArchived && receivable.outstandingAmount.isPositive &&
        receivable.status in setOf(ReceivableStatus.CONFIRMED, ReceivableStatus.PARTIALLY_PAID, ReceivableStatus.OVERDUE)
    val isClosed = receivable.outstandingAmount.isZero && receivable.status in setOf(
        ReceivableStatus.PAID, ReceivableStatus.CANCELLED, ReceivableStatus.WRITTEN_OFF
    )
    val canCancel = !receivable.isArchived && canCollect &&
        receivable.payments.none { it.reversedAt == null } && receivable.writeOffs.none { it.reversedAt == null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (receivable.isArchived) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(receivable.clientName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (receivable.isArchived) "Archived" else receivable.status.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (receivable.status in setOf(ReceivableStatus.OVERDUE, ReceivableStatus.WRITTEN_OFF)) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canCollect) {
                    IconButton(onClick = onCollect) {
                        Icon(Icons.Default.Payments, contentDescription = "Collect from ${receivable.clientName}")
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "${receivable.clientName} actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (receivable.isArchived) {
                            DropdownMenuItem(text = { Text("Restore") }, onClick = { menuExpanded = false; onRestore() })
                        } else {
                            DropdownMenuItem(text = { Text("Edit details") }, onClick = { menuExpanded = false; onEdit() })
                            if (canCollect) {
                                DropdownMenuItem(text = { Text("Collect payment") }, onClick = { menuExpanded = false; onCollect() })
                                DropdownMenuItem(text = { Text("Write off balance") }, onClick = { menuExpanded = false; onWriteOff() })
                                if (canCancel) DropdownMenuItem(text = { Text("Cancel receivable") }, onClick = { menuExpanded = false; onCancel() })
                            }
                            if (isClosed) DropdownMenuItem(text = { Text("Archive") }, onClick = { menuExpanded = false; onArchive() })
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Outstanding", style = MaterialTheme.typography.labelSmall)
                    Text(displayMoney(receivable.outstandingAmount), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Original", style = MaterialTheme.typography.labelSmall)
                    Text(displayMoney(receivable.originalAmount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Due ${formatDate(receivable.dueDate)}", style = MaterialTheme.typography.bodySmall)
            if (receivable.description.isNotBlank()) Text(receivable.description, style = MaterialTheme.typography.bodySmall)
            if (hasHistory) {
                TextButton(onClick = { historyExpanded = !historyExpanded }) {
                    Text(if (historyExpanded) "Hide payment history" else "View payment history (${receivable.payments.size + receivable.writeOffs.size})")
                }
                if (historyExpanded) {
                    receivable.payments.sortedByDescending { it.paymentDate }.forEach { payment ->
                        val accountName = accounts.firstOrNull { it.id == payment.destinationAccountId }?.name ?: "Account"
                        HistoryRow(
                            title = "Collection · ${formatDate(payment.paymentDate)}",
                            detail = listOf(accountName, payment.notes).filter(String::isNotBlank).joinToString(" · "),
                            amount = payment.amount,
                            reversed = payment.reversedAt != null,
                            canReverse = !receivable.isArchived,
                            onReverse = { onReverse(payment.journalEntryId, "collection") }
                        )
                    }
                    receivable.writeOffs.sortedByDescending { it.date }.forEach { writeOff ->
                        val categoryName = categories.firstOrNull { it.id == writeOff.expenseCategoryId }?.name ?: "Expense"
                        HistoryRow(
                            title = "Write-off · ${formatDate(writeOff.date)}",
                            detail = listOf(categoryName, writeOff.notes).filter(String::isNotBlank).joinToString(" · "),
                            amount = writeOff.amount,
                            reversed = writeOff.reversedAt != null,
                            canReverse = !receivable.isArchived,
                            onReverse = { onReverse(writeOff.journalEntryId, "write-off") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    title: String,
    detail: String,
    amount: Money,
    reversed: Boolean,
    canReverse: Boolean,
    onReverse: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(displayMoney(amount), style = MaterialTheme.typography.labelLarge)
            if (reversed) Text("Reversed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (canReverse) TextButton(onClick = onReverse) { Text("Reverse") }
            else Text("Restore to reverse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivableEditorDialog(
    receivable: Receivable?,
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onConfirm: (client: String, amount: Money, dueDate: Long, description: String, categoryId: String?, counterpartyId: String?) -> Unit
) {
    var client by remember(receivable?.id) { mutableStateOf(receivable?.clientName.orEmpty()) }
    var amountText by remember(receivable?.id) { mutableStateOf(receivable?.originalAmount?.let { moneyInput(it.centavos) }.orEmpty()) }
    var description by remember(receivable?.id) { mutableStateOf(receivable?.description.orEmpty()) }
    var dueDate by remember(receivable?.id) { mutableStateOf(receivable?.dueDate ?: System.currentTimeMillis() + 30L * 86_400_000L) }
    var selectedCategoryId by remember(receivable?.id) { mutableStateOf(receivable?.linkedCategoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var counterpartyExpanded by remember { mutableStateOf(false) }
    var selectedCounterpartyId by remember(receivable?.id) { mutableStateOf(receivable?.counterpartyId) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val datePickerState = if (datePickerOpen) rememberDatePickerState(initialSelectedDateMillis = dueDate) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (receivable == null) "Add receivable" else "Edit receivable details") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    client,
                    { client = it; selectedCounterpartyId = null; error = null },
                    label = { Text("Client / counterparty") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = counterpartyExpanded,
                    onExpandedChange = { counterpartyExpanded = !counterpartyExpanded }
                ) {
                    OutlinedTextField(
                        value = counterparties.firstOrNull { it.id == selectedCounterpartyId }?.name ?: "No saved client linked",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Link saved client") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = counterpartyExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = counterpartyExpanded, onDismissRequest = { counterpartyExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No saved client") },
                            onClick = { selectedCounterpartyId = null; counterpartyExpanded = false }
                        )
                        counterparties.filter { !it.isArchived || it.id == receivable?.counterpartyId }.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name + if (item.isArchived) " (archived)" else "") },
                                onClick = { client = item.name; selectedCounterpartyId = item.id; counterpartyExpanded = false }
                            )
                        }
                    }
                }
                if (receivable == null) {
                    OutlinedTextField(
                        amountText, { amountText = it; error = null },
                        label = { Text("Amount owed (PHP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                        OutlinedTextField(
                            value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select income source",
                            onValueChange = {}, readOnly = true, label = { Text("Income source") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            categories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = { selectedCategoryId = category.id; categoryExpanded = false })
                            }
                        }
                    }
                } else {
                    Text("Original amount and income source stay attached to the posted journal entry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { datePickerOpen = true }) { Text("Due date: ${formatDate(dueDate)}") }
                OutlinedTextField(description, { description = it }, label = { Text("Description / invoice reference") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = if (receivable == null) Money.parse(amountText) else receivable.originalAmount
                when {
                    client.isBlank() -> error = "Enter a client or counterparty name."
                    amount == null || !amount.isPositive -> error = "Enter an amount greater than zero."
                    receivable == null && selectedCategoryId.isBlank() -> error = "Choose an income source."
                    else -> onConfirm(client.trim(), amount, dueDate, description.trim(), selectedCategoryId.ifBlank { null }, selectedCounterpartyId)
                }
            }) { Text(if (receivable == null) "Record receivable" else "Save details") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (datePickerOpen && datePickerState != null) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = { datePickerState.selectedDateMillis?.let { dueDate = it }; datePickerOpen = false }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectPaymentDialog(
    receivable: Receivable,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (destinationAccountId: String, amount: Money, notes: String) -> Unit
) {
    var selectedAccountId by remember(receivable.id) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var amountText by remember(receivable.id) { mutableStateOf(moneyInput(receivable.outstandingAmount.centavos)) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedName = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Select destination account"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${receivable.clientName} · up to ${displayMoney(receivable.outstandingAmount)}")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedName, onValueChange = {}, readOnly = true,
                        label = { Text("Deposit account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.name) }, onClick = { selectedAccountId = account.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    amountText, { amountText = it; error = null }, label = { Text("Collection amount (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes / receipt reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                when {
                    selectedAccountId.isBlank() -> error = "Choose a destination account."
                    amount == null || !amount.isPositive -> error = "Enter a collection greater than zero."
                    amount > receivable.outstandingAmount -> error = "Collection exceeds the remaining balance."
                    else -> onConfirm(selectedAccountId, amount, notes.trim())
                }
            }) { Text("Record payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteOffDialog(
    receivable: Receivable,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Money, categoryId: String, notes: String) -> Unit
) {
    var amountText by remember(receivable.id) { mutableStateOf(moneyInput(receivable.outstandingAmount.centavos)) }
    var notes by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val categoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select expense category"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write off uncollectible balance?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This records an expense and reduces this receivable. The original income recognition stays unchanged.")
                Text("Outstanding: ${displayMoney(receivable.outstandingAmount)}")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = categoryName, onValueChange = {}, readOnly = true,
                        label = { Text("Bad-debt expense category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { selectedCategoryId = category.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    amountText, { amountText = it; error = null }, label = { Text("Amount to write off (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Reason / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                when {
                    selectedCategoryId.isBlank() -> error = "Choose an expense category."
                    amount == null || !amount.isPositive -> error = "Enter an amount greater than zero."
                    amount > receivable.outstandingAmount -> error = "Write-off exceeds the outstanding balance."
                    notes.isBlank() -> error = "Add a brief reason for the write-off."
                    else -> onConfirm(amount, selectedCategoryId, notes.trim())
                }
            }) { Text("Confirm write-off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep balance") } }
    )
}

private fun moneyInput(centavos: Long): String = "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"

@Composable
private fun formatDate(timestamp: Long): String = displayDate(timestamp)
