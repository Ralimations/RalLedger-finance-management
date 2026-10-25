package com.ralledger.ui.planning
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.Money
import com.ralledger.ui.components.MoneyAmountField
import com.ralledger.ui.components.DiscardChangesDialog
import com.ralledger.domain.model.Counterparty
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.ralledger.ui.settings.displayDate

private enum class DebtAction { CANCEL, ARCHIVE, RESTORE, REVERSE_PAYMENT }
private data class PendingDebtAction(val debt: Debt, val action: DebtAction, val paymentJournalId: String? = null)

@Composable
fun DebtsScreen(
    viewModel: DebtsViewModel,
    quickActionRequest: PlanningQuickAction? = null,
    onQuickActionHandled: () -> Unit = {}
) {
    val debts by viewModel.debts.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorKind by remember { mutableStateOf(DebtKind.BILL) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var paymentFor by remember { mutableStateOf<Debt?>(null) }
    var pending by remember { mutableStateOf<PendingDebtAction?>(null) }

    LaunchedEffect(quickActionRequest) {
        when (quickActionRequest) {
            PlanningQuickAction.ADD_BILL -> {
                editorKind = DebtKind.BILL
                editing = null
                editorOpen = true
                onQuickActionHandled()
            }
            PlanningQuickAction.ADD_LOAN -> {
                editorKind = DebtKind.LOAN
                editing = null
                editorOpen = true
                onQuickActionHandled()
            }
            else -> Unit
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    val shown = debts.filter { debt ->
        debt.isArchived == showArchived &&
            (query.isBlank() || listOf(debt.title, debt.counterparty, debt.kind.displayName, debt.status.displayName)
                .any { it.contains(query, ignoreCase = true) })
    }
    val openBills = debts.filter { !it.isArchived && it.kind == DebtKind.BILL && it.status !in setOf(DebtStatus.PAID, DebtStatus.CANCELLED) }
        .fold(Money.ZERO) { sum, debt -> sum + debt.outstandingAmount }
    val openLoans = debts.filter { !it.isArchived && it.kind == DebtKind.LOAN && it.status !in setOf(DebtStatus.PAID, DebtStatus.CANCELLED) }
        .fold(Money.ZERO) { sum, debt -> sum + debt.outstandingAmount }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Bills due", style = MaterialTheme.typography.labelMedium); Text(displayMoney(openBills), fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Loans outstanding", style = MaterialTheme.typography.labelMedium); Text(displayMoney(openLoans), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search bills and loans") }, singleLine = true
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                    FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                    Spacer(Modifier.weight(1f))
                    Text("${shown.size} records", style = MaterialTheme.typography.labelMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editorKind = DebtKind.BILL; editing = null; editorOpen = true }) { Text("Add bill") }
                    Button(onClick = { editorKind = DebtKind.LOAN; editing = null; editorOpen = true }) { Text("Add loan") }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (query.isBlank()) "No ${if (showArchived) "archived " else ""}bills or loans" else "No matches", fontWeight = FontWeight.SemiBold)
                            Text("Record bills and borrowed funds, then track principal and interest payments.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(shown, key = { it.id }) { debt ->
                    DebtCard(
                        debt = debt,
                        onEdit = { editing = debt; editorKind = debt.kind; editorOpen = true },
                        onPay = { paymentFor = debt },
                        onCancel = { pending = PendingDebtAction(debt, DebtAction.CANCEL) },
                        onArchive = { pending = PendingDebtAction(debt, DebtAction.ARCHIVE) },
                        onRestore = { pending = PendingDebtAction(debt, DebtAction.RESTORE) },
                        onReverse = { payment -> pending = PendingDebtAction(debt, DebtAction.REVERSE_PAYMENT, payment.journalEntryId) }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        DebtEditorDialog(
            debt = editing, kind = editorKind, accounts = accounts, categories = categories, counterparties = counterparties,
            onDismiss = { editorOpen = false },
            onSave = { title, counterparty, amount, dueDate, categoryId, accountId, counterpartyId ->
                val existing = editing
                if (existing == null) viewModel.create(editorKind, title, counterparty, amount, dueDate, categoryId, accountId, counterpartyId)
                else viewModel.update(existing, title, counterparty, dueDate, counterpartyId)
                editorOpen = false
            }
        )
    }

    paymentFor?.let { debt ->
        DebtPaymentDialog(
            debt = debt, accounts = accounts, categories = categories,
            onDismiss = { paymentFor = null },
            onSave = { accountId, principal, interest, categoryId, date, notes ->
                viewModel.pay(debt, accountId, principal, interest, categoryId, date, notes)
                paymentFor = null
            }
        )
    }

    pending?.let { action ->
        val (title, description, confirm) = when (action.action) {
            DebtAction.CANCEL -> Triple("Cancel this ${action.debt.kind.displayName.lowercase()}?", "The journal entry will be reversed. Reverse active payments first.", "Cancel record")
            DebtAction.ARCHIVE -> Triple("Archive this record?", "${action.debt.title} will move to Archived. Its financial history stays available.", "Archive")
            DebtAction.RESTORE -> Triple("Restore this record?", "${action.debt.title} will return to the active list.", "Restore")
            DebtAction.REVERSE_PAYMENT -> Triple("Reverse this payment?", "The cash and principal balances will be restored, and a reversal will be recorded.", "Reverse payment")
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) }, text = { Text(description) },
            confirmButton = {
                Button(onClick = {
                    when (action.action) {
                        DebtAction.CANCEL -> viewModel.cancel(action.debt)
                        DebtAction.ARCHIVE -> viewModel.archive(action.debt, true)
                        DebtAction.RESTORE -> viewModel.archive(action.debt, false)
                        DebtAction.REVERSE_PAYMENT -> action.paymentJournalId?.let(viewModel::reversePayment)
                    }
                    pending = null
                }) { Text(confirm) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun DebtCard(
    debt: Debt,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onReverse: (com.ralledger.domain.model.DebtPayment) -> Unit
) {
    var menuExpanded by remember(debt.id) { mutableStateOf(false) }
    val overdue = !debt.isArchived && debt.outstandingAmount.isPositive && isBeforeToday(debt.dueDate)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(debt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(listOf(debt.kind.displayName, debt.counterparty).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Manage ${debt.title}") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!debt.isArchived && debt.status !in setOf(DebtStatus.PAID, DebtStatus.CANCELLED)) {
                        DropdownMenuItem(text = { Text("Edit details") }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(text = { Text("Record payment") }, onClick = { menuExpanded = false; onPay() })
                        DropdownMenuItem(text = { Text("Cancel record") }, onClick = { menuExpanded = false; onCancel() })
                    }
                    if (debt.status in setOf(DebtStatus.PAID, DebtStatus.CANCELLED)) {
                        DropdownMenuItem(text = { Text(if (debt.isArchived) "Restore" else "Archive") }, onClick = { menuExpanded = false; if (debt.isArchived) onRestore() else onArchive() })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayMoney(debt.outstandingAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Due ${formatDebtDate(debt.dueDate)}", style = MaterialTheme.typography.bodySmall)
                    Text(if (debt.isArchived) "Archived" else if (overdue) "Overdue" else debt.status.displayName,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            if (debt.kind == DebtKind.BILL && debt.categoryName != null) Text("Expense · ${debt.categoryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (debt.payments.isNotEmpty()) {
                Text("Payment history", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                debt.payments.take(3).forEach { payment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatDebtDate(payment.paymentDate)} · Principal ${displayMoney(payment.principal)} · Interest ${displayMoney(payment.interest)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        if (payment.reversedAt != null) Text("Reversed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        else if (!debt.isArchived) TextButton(onClick = { onReverse(payment) }) { Text("Undo") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtEditorDialog(
    debt: Debt?,
    kind: DebtKind,
    accounts: List<Account>,
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onSave: (String, String, Money, Long, String?, String?, String?) -> Unit
) {
    var title by remember(debt?.id) { mutableStateOf(debt?.title.orEmpty()) }
    var counterparty by remember(debt?.id) { mutableStateOf(debt?.counterparty.orEmpty()) }
    var selectedCounterpartyId by remember(debt?.id) { mutableStateOf(debt?.counterpartyId) }
    var amount by remember(debt?.id) { mutableStateOf(debt?.originalAmount?.format()?.removePrefix("₱")?.replace(",", "").orEmpty()) }
    var dueDate by remember(debt?.id) { mutableStateOf(debt?.dueDate ?: System.currentTimeMillis() + 14L * 86_400_000L) }
    val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE && (!it.isArchived || it.id == debt?.categoryId) }
    val assetAccounts = accounts.filter { it.accountType.isAsset && !it.accountType.isReceivable && (!it.isArchived || it.id == debt?.fundingAccountId) }
    var categoryId by remember(debt?.id, expenseCategories) { mutableStateOf(debt?.categoryId ?: expenseCategories.firstOrNull()?.id.orEmpty()) }
    var accountId by remember(debt?.id, assetAccounts) { mutableStateOf(debt?.fundingAccountId ?: assetAccounts.firstOrNull()?.id.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var counterpartyExpanded by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val originalTitle = remember(debt?.id) { title }
    val originalCounterparty = remember(debt?.id) { counterparty }
    val originalCounterpartyId = remember(debt?.id) { selectedCounterpartyId }
    val originalAmount = remember(debt?.id) { amount }
    val originalDueDate = remember(debt?.id) { dueDate }
    val originalCategoryId = remember(debt?.id, expenseCategories) { categoryId }
    val originalAccountId = remember(debt?.id, assetAccounts) { accountId }
    var confirmDiscard by remember(debt?.id) { mutableStateOf(false) }
    val isDirty = title != originalTitle || counterparty != originalCounterparty ||
        selectedCounterpartyId != originalCounterpartyId || amount != originalAmount || dueDate != originalDueDate ||
        categoryId != originalCategoryId || accountId != originalAccountId
    val requestDismiss = { if (isDirty) confirmDiscard = true else onDismiss() }
    val dateState = if (dateOpen) rememberDatePickerState(initialSelectedDateMillis = dueDate) else null

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (debt == null) "Add ${kind.displayName.lowercase()}" else "Edit ${kind.displayName.lowercase()}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("Name or description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    counterparty,
                    { counterparty = it; selectedCounterpartyId = null },
                    label = { Text(if (kind == DebtKind.BILL) "Payee (optional)" else "Lender (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SelectDebtValue(
                    "Saved contact",
                    counterparties.firstOrNull { it.id == selectedCounterpartyId }?.name ?: "No saved contact",
                    counterpartyExpanded,
                    { counterpartyExpanded = it }
                ) {
                    DropdownMenuItem(text = { Text("No saved contact") }, onClick = {
                        selectedCounterpartyId = null
                        counterpartyExpanded = false
                    })
                    counterparties.filter { !it.isArchived || it.id == debt?.counterpartyId }.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            selectedCounterpartyId = item.id
                            counterparty = item.name
                            counterpartyExpanded = false
                        })
                    }
                }
                if (debt == null) {
                    MoneyAmountField(amount, { amount = it; error = null }, "${if (kind == DebtKind.BILL) "Bill" else "Loan"} amount (PHP)")
                    if (kind == DebtKind.BILL) {
                        SelectDebtValue("Expense category", expenseCategories.firstOrNull { it.id == categoryId }?.name ?: "Choose category", categoryExpanded, { categoryExpanded = it }) {
                            expenseCategories.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { categoryId = item.id; categoryExpanded = false }) }
                        }
                    } else {
                        SelectDebtValue("Deposit to account", assetAccounts.firstOrNull { it.id == accountId }?.name ?: "Choose account", accountExpanded, { accountExpanded = it }) {
                            assetAccounts.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { accountId = item.id; accountExpanded = false }) }
                        }
                    }
                } else {
                    Text("Amount and category are locked after recording so the ledger history stays accurate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { dateOpen = true }) { Text("Due date: ${formatDebtDate(dueDate)}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = Money.parse(amount)
                when {
                    title.isBlank() -> error = "Enter a name or description."
                    debt == null && (parsed == null || !parsed.isPositive) -> error = "Enter an amount greater than zero."
                    debt == null && kind == DebtKind.BILL && categoryId.isBlank() -> error = "Choose an expense category."
                    debt == null && kind == DebtKind.LOAN && accountId.isBlank() -> error = "Choose the account that received the funds."
                    else -> onSave(title.trim(), counterparty.trim(), parsed ?: debt!!.originalAmount, dueDate,
                        if (kind == DebtKind.BILL) categoryId else null,
                        if (kind == DebtKind.LOAN) accountId else null,
                        selectedCounterpartyId)
                }
            }) { Text(if (debt == null) "Save record" else "Save details") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
    if (dateOpen && dateState != null) {
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = { TextButton(onClick = { dateState.selectedDateMillis?.let { dueDate = it }; dateOpen = false }) { Text("Choose") } },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState, showModeToggle = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectDebtValue(label: String, value: String, expanded: Boolean, onExpanded: (Boolean) -> Unit, choices: @Composable () -> Unit) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpanded) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) { choices() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtPaymentDialog(
    debt: Debt,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, Money, Money, String?, Long, String) -> Unit
) {
    val assetAccounts = accounts.filter { it.accountType.isAsset && !it.accountType.isReceivable && !it.isArchived }
    val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived }
    var accountId by remember(debt.id, assetAccounts) { mutableStateOf(assetAccounts.firstOrNull()?.id.orEmpty()) }
    var principal by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("") }
    var categoryId by remember(debt.id, expenseCategories) { mutableStateOf(expenseCategories.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dateState = if (dateOpen) rememberDatePickerState(initialSelectedDateMillis = date) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment · ${debt.title}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding principal: ${displayMoney(debt.outstandingAmount)}", fontWeight = FontWeight.SemiBold)
                SelectDebtValue("Pay from account", assetAccounts.firstOrNull { it.id == accountId }?.name ?: "Choose account", accountExpanded, { accountExpanded = it }) {
                    assetAccounts.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { accountId = item.id; accountExpanded = false }) }
                }
                MoneyAmountField(principal, { principal = it; error = null }, "Principal paid (PHP)")
                MoneyAmountField(interest, { interest = it; error = null }, "Interest or fee (PHP)")
                SelectDebtValue("Interest expense category", expenseCategories.firstOrNull { it.id == categoryId }?.name ?: "Choose category", categoryExpanded, { categoryExpanded = it }) {
                    expenseCategories.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { categoryId = item.id; categoryExpanded = false }) }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { dateOpen = true }) { Text("Payment date: ${formatDebtDate(date)}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val principalMoney = Money.parse(principal.ifBlank { "0" })
                val interestMoney = Money.parse(interest.ifBlank { "0" })
                when {
                    accountId.isBlank() -> error = "Choose the account used for payment."
                    principalMoney == null || principalMoney.isNegative -> error = "Enter a valid principal amount."
                    principalMoney.centavos > debt.outstandingAmount.centavos -> error = "Principal cannot exceed the outstanding balance."
                    interestMoney == null || interestMoney.isNegative -> error = "Enter a valid interest amount."
                    interestMoney.isPositive && categoryId.isBlank() -> error = "Choose an interest expense category."
                    !principalMoney.isPositive && !interestMoney.isPositive -> error = "Enter principal or interest greater than zero."
                    else -> onSave(accountId, principalMoney, interestMoney, if (interestMoney.isPositive) categoryId else null, date, notes.trim())
                }
            }) { Text("Record payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (dateOpen && dateState != null) {
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = { TextButton(onClick = { dateState.selectedDateMillis?.let { date = it }; dateOpen = false }) { Text("Choose") } },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState, showModeToggle = false) }
    }
}

@Composable
private fun formatDebtDate(timestamp: Long): String = displayDate(timestamp)

private fun isBeforeToday(timestamp: Long): Boolean {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return target.before(today)
}
