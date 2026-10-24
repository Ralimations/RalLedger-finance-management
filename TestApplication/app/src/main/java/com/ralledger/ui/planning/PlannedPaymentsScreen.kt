package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.Money
import com.ralledger.ui.components.MoneyCalculatorAction
import com.ralledger.domain.model.PlannedPayment
import com.ralledger.domain.model.PlannedPaymentRecord
import com.ralledger.domain.model.PlannedPaymentStatus
import com.ralledger.ui.settings.displayDate
import com.ralledger.ui.settings.displayMoney
import java.util.Calendar

private enum class PlanAction { CANCEL, DELETE, REVERSE }
private data class PendingPlanAction(val item: PlannedPayment, val action: PlanAction, val record: PlannedPaymentRecord? = null)

@Composable
fun PlannedPaymentsScreen(viewModel: PlannedPaymentsViewModel) {
    val plans by viewModel.payments.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<PlannedPayment?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<PlannedPayment?>(null) }
    var pendingAction by remember { mutableStateOf<PendingPlanAction?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val filtered = plans.filter { plan ->
        plan.isArchived == showArchived &&
            (query.isBlank() || listOf(plan.description, plan.counterparty, plan.categoryName, plan.status.displayName)
                .any { it.contains(query, ignoreCase = true) })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorTarget = null; editorOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add planned payment")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Planned Payments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Forecasts do not change account balances until you record a payment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search plans") },
                    singleLine = true
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                    FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                    Spacer(Modifier.weight(1f))
                    Text("${filtered.size} plans", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (query.isBlank()) "No planned payments" else "No matching plans", fontWeight = FontWeight.SemiBold)
                            Text("Add bills or one-time expenses you expect to pay.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { plan ->
                    PlannedPaymentCard(
                        plan = plan,
                        onEdit = { editorTarget = plan; editorOpen = true },
                        onRecord = { paymentTarget = plan },
                        onCancel = { pendingAction = PendingPlanAction(plan, PlanAction.CANCEL) },
                        onDelete = { pendingAction = PendingPlanAction(plan, PlanAction.DELETE) },
                        onReverse = { record -> pendingAction = PendingPlanAction(plan, PlanAction.REVERSE, record) },
                        onArchive = { viewModel.archive(plan, !plan.isArchived) },
                        onReactivate = { viewModel.reactivate(plan) }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        PlannedPaymentEditorDialog(
            item = editorTarget,
            categories = categories,
            counterparties = counterparties,
            onDismiss = { editorOpen = false },
            onSave = { description, counterparty, amount, date, categoryId, counterpartyId, notes ->
                val existing = editorTarget
                if (existing == null) viewModel.create(description, counterparty, amount, date, categoryId, counterpartyId, notes)
                else viewModel.update(existing, description, counterparty, amount, date, categoryId, counterpartyId, notes)
                editorOpen = false
            }
        )
    }

    paymentTarget?.let { plan ->
        RecordPaymentDialog(
            plan = plan,
            accounts = accounts.filter { !it.isArchived && it.accountType.isAsset && !it.accountType.isReceivable },
            onDismiss = { paymentTarget = null },
            onRecord = { accountId, amount, date, notes ->
                viewModel.record(plan, accountId, amount, date, notes)
                paymentTarget = null
            }
        )
    }

    pendingAction?.let { pending ->
        val title: String
        val detail: String
        val label: String
        when (pending.action) {
            PlanAction.CANCEL -> {
                title = "Cancel this plan?"; detail = "${pending.item.description} will be marked cancelled. You can reactivate it later."; label = "Cancel plan"
            }
            PlanAction.DELETE -> {
                title = "Delete this plan?"; detail = "The plan will be permanently removed if it has no payment history."; label = "Delete"
            }
            PlanAction.REVERSE -> {
                title = "Reverse this payment?"; detail = "A matching reversal will be posted to the ledger. The payment history remains, and this plan becomes available to pay again."; label = "Reverse payment"
            }
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = {
                Button(onClick = {
                    when (pending.action) {
                        PlanAction.CANCEL -> viewModel.cancel(pending.item)
                        PlanAction.DELETE -> viewModel.delete(pending.item)
                        PlanAction.REVERSE -> pending.record?.let { viewModel.reverse(it.id) }
                    }
                    pendingAction = null
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun PlannedPaymentCard(
    plan: PlannedPayment,
    onEdit: () -> Unit,
    onRecord: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onReverse: (PlannedPaymentRecord) -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit
) {
    var menuExpanded by remember(plan.id) { mutableStateOf(false) }
    val activePayment = plan.activePayment
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(listOf(plan.categoryName, plan.counterparty).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(displayMoney(plan.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!plan.isArchived && plan.status == PlannedPaymentStatus.PLANNED && !plan.isPaid && plan.payments.isEmpty()) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    }
                    if (!plan.isArchived && plan.status == PlannedPaymentStatus.PLANNED && activePayment == null) {
                        DropdownMenuItem(text = { Text("Record payment") }, onClick = { menuExpanded = false; onRecord() })
                        DropdownMenuItem(text = { Text("Cancel") }, onClick = { menuExpanded = false; onCancel() })
                    }
                    if (!plan.isArchived && plan.status == PlannedPaymentStatus.CANCELLED && activePayment == null) {
                        DropdownMenuItem(text = { Text("Reactivate") }, onClick = { menuExpanded = false; onReactivate() })
                    }
                    DropdownMenuItem(text = { Text(if (plan.isArchived) "Restore" else "Archive") }, onClick = { menuExpanded = false; onArchive() })
                    if (plan.payments.isEmpty()) DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Due ${displayDate(plan.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        plan.isArchived -> "Archived"
                        activePayment != null -> "Paid ${displayMoney(activePayment.amount)}"
                        else -> plan.status.displayName
                    },
                    color = if (activePayment != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
            if (plan.notes.isNotBlank()) Text(plan.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plan.payments.forEach { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Payment history", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("${displayMoney(payment.amount)} · ${displayDate(payment.paymentDate)}${if (payment.reversedAt != null) " · Reversed" else ""}")
                        if (payment.notes.isNotBlank()) Text(payment.notes, style = MaterialTheme.typography.bodySmall)
                        if (payment.reversedAt == null) TextButton(onClick = { onReverse(payment) }) { Text("Reverse payment") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannedPaymentEditorDialog(
    item: PlannedPayment?,
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onSave: (String, String, Money, Long, String, String?, String) -> Unit
) {
    var description by remember(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var payee by remember(item?.id) { mutableStateOf(item?.counterparty.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.let { "%.2f".format(it.centavos / 100.0) }.orEmpty()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var categoryId by remember(item?.id) { mutableStateOf(item?.expenseCategoryId ?: categories.firstOrNull { it.type == CategoryType.EXPENSE && !it.isArchived }?.id.orEmpty()) }
    var counterpartyId by remember(item?.id) { mutableStateOf(item?.counterpartyId) }
    var date by remember(item?.id) { mutableStateOf(item?.dueDate ?: Calendar.getInstance().timeInMillis) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activeCategories = categories.filter { it.type == CategoryType.EXPENSE && (!it.isArchived || it.id == item?.expenseCategoryId) }
    val category = activeCategories.firstOrNull { it.id == categoryId }
    val counterparty = counterparties.firstOrNull { it.id == counterpartyId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add planned payment" else "Edit planned payment") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(payee, { payee = it }, label = { Text("Payee") }, singleLine = true)
                SelectionField("Expense category", category?.name ?: "Choose category", activeCategories.map { it.name to it.id }, { categoryId = it })
                SelectionField("Saved counterparty (optional)", counterparty?.name ?: "None", listOf("None" to "") + counterparties.filter { !it.isArchived || it.id == item?.counterpartyId }.map { it.name to it.id }, { counterpartyId = it.ifBlank { null } })
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Planned amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), trailingIcon = { MoneyCalculatorAction(amountText) { amountText = it } }, singleLine = true)
                OutlinedButton(onClick = { datePickerOpen = true }) { Text("Due ${displayDate(date)}") }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = Money.parse(amountText)
                when {
                    description.isBlank() -> error = "Description is required"
                    parsed == null || !parsed.isPositive -> error = "Enter a valid amount greater than zero"
                    categoryId.isBlank() -> error = "Choose an expense category"
                    else -> { error = null; onSave(description, payee, parsed, date, categoryId, counterpartyId, notes) }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (datePickerOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(onDismissRequest = { datePickerOpen = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { date = it }; datePickerOpen = false }) { Text("Choose") }
        }, dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }) { DatePicker(state = picker) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialog(
    plan: PlannedPayment,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onRecord: (String, Money, Long, String) -> Unit
) {
    var accountId by remember(plan.id) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var amountText by remember(plan.id) { mutableStateOf("%.2f".format(plan.amount.centavos / 100.0)) }
    var notes by remember(plan.id) { mutableStateOf("") }
    var date by remember(plan.id) { mutableStateOf(Calendar.getInstance().timeInMillis) }
    var accountMenu by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedAccount = accounts.firstOrNull { it.id == accountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This posts the actual amount as an expense and updates the account balance.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedAccount?.name ?: "Choose account") }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { accountId = account.id; accountMenu = false }) }
                }
                if (accounts.isEmpty()) Text("Add or restore an asset account first.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Actual amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), trailingIcon = { MoneyCalculatorAction(amountText) { amountText = it } }, singleLine = true)
                OutlinedButton(onClick = { datePickerOpen = true }) { Text("Paid ${displayDate(date)}") }
                OutlinedTextField(notes, { notes = it }, label = { Text("Payment notes (optional)") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = selectedAccount != null, onClick = {
                val amount = Money.parse(amountText)
                if (selectedAccount == null) error = "Choose an account"
                else if (amount == null || !amount.isPositive) error = "Enter a valid actual amount"
                else { error = null; onRecord(accountId, amount, date, notes) }
            }) { Text("Post payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (datePickerOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(onDismissRequest = { datePickerOpen = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { date = it }; datePickerOpen = false }) { Text("Choose") }
        }, dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }) { DatePicker(state = picker) }
    }
}

@Composable
private fun SelectionField(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember(label) { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (title, id) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}
