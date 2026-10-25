package com.ralledger.ui.accounts
import com.ralledger.ui.settings.displayMoney
import com.ralledger.ui.components.MoneyAmountField
import com.ralledger.ui.components.DiscardChangesDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import java.text.SimpleDateFormat
import com.ralledger.ui.settings.displayDate
import java.util.Date
import java.util.Locale

private val accountColors = listOf(
    "#00695C", "#1565C0", "#6A1B9A", "#EF6C00", "#C62828", "#455A64", "#2E7D32"
)
private val accountIcons = listOf(
    "account_balance", "account_balance_wallet", "savings", "credit_card", "wallet", "payments"
)

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onQuickAddForAccount: (transactionType: String, accountId: String) -> Unit = { _, _ -> }
) {
    val accounts by viewModel.accounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var detailAccount by remember { mutableStateOf<Account?>(null) }
    var historyAccount by remember { mutableStateOf<Account?>(null) }
    var reconcilingAccount by remember { mutableStateOf<Account?>(null) }
    var deletingAccount by remember { mutableStateOf<Account?>(null) }
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val visibleAccounts = accounts.filter { account ->
        (showArchived || !account.isArchived) &&
            (search.isBlank() || account.name.contains(search, true) ||
                account.institution.contains(search, true) || account.description.contains(search, true))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Accounts", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search accounts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { }
                    .toggleable(
                        value = showArchived,
                        role = Role.Checkbox,
                        onValueChange = { showArchived = it }
                    )
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = showArchived, onCheckedChange = null)
                Text("Show archived accounts", style = MaterialTheme.typography.bodyMedium)
            }

            if (visibleAccounts.isEmpty()) {
                Text(
                    if (accounts.isEmpty()) "Add an account to start tracking your money."
                    else "No accounts match this view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleAccounts, key = { it.id }) { account ->
                        AccountItemCard(
                            account = account,
                            onOpenDetails = { detailAccount = account },
                            onEdit = { editingAccount = account },
                            onHistory = { historyAccount = account },
                            onReconcile = { reconcilingAccount = account },
                            onArchiveToggle = { viewModel.setArchived(account.id, !account.isArchived) },
                            onDelete = { deletingAccount = account }
                        )
                    }
                }
            }
        }
    }

    detailAccount?.let { account ->
        AccountDetailSheet(
            account = account,
            transactions = transactionsForAccount(account.id, transactions),
            onDismiss = { detailAccount = null },
            onReceive = { detailAccount = null; onQuickAddForAccount("INCOME", account.id) },
            onTransfer = { detailAccount = null; onQuickAddForAccount("TRANSFER", account.id) },
            onViewHistory = { detailAccount = null; historyAccount = account },
            onReconcile = { detailAccount = null; reconcilingAccount = account },
            onEdit = { detailAccount = null; editingAccount = account },
            onArchiveToggle = { detailAccount = null; viewModel.setArchived(account.id, !account.isArchived) },
            onDelete = { detailAccount = null; deletingAccount = account }
        )
    }
    if (showAddDialog) {
        AccountFormDialog(
            account = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, institution, description, type, balance, icon, color, include ->
                viewModel.createAccount(name, institution, type, balance, icon, color, description, include)
                showAddDialog = false
            }
        )
    }
    editingAccount?.let { account ->
        AccountFormDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onSave = { name, institution, description, _, _, icon, color, include ->
                viewModel.updateAccountMetadata(account.id, name, institution, description, icon, color, include)
                editingAccount = null
            }
        )
    }
    historyAccount?.let { account ->
        AccountHistoryDialog(
            account = account,
            transactions = transactions.filter {
                it.accountId == account.id || it.destinationAccountId == account.id
            }.sortedByDescending { it.timestamp },
            onDismiss = { historyAccount = null }
        )
    }
    reconcilingAccount?.let { account ->
        AccountReconciliationDialog(
            account = account,
            onDismiss = { reconcilingAccount = null },
            onSave = { actualBalance, reason ->
                viewModel.reconcileAccount(account.id, actualBalance, reason)
                reconcilingAccount = null
            }
        )
    }
    deletingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("Delete ${account.name}?") },
            text = {
                Text("Permanent deletion is available only for an unused account. If it has ledger, reconciliation, payment, or subscription history, the app will keep it and explain why. Archive the account to preserve its history.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteUnusedAccount(account.id)
                    deletingAccount = null
                }) { Text("Delete if unused") }
            },
            dismissButton = { TextButton(onClick = { deletingAccount = null }) { Text("Cancel") } }
        )
    }
    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Account") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun AccountItemCard(
    account: Account,
    onOpenDetails: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onReconcile: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = accountColor(account.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (account.isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(accountIcon(account.iconName), contentDescription = null, tint = accent)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.isArchived) {
                    Text("Archived", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val subtitle = listOf(account.institution, account.accountType.displayName)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (account.description.isNotBlank()) {
                    Text(account.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    displayMoney(account.currentLedgerBalance),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
                Text("Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "${account.name} actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Account details") }, onClick = { menuExpanded = false; onOpenDetails() })
                    DropdownMenuItem(text = { Text("View history") }, onClick = { menuExpanded = false; onHistory() })
                    if (!account.isArchived && (account.accountType.isAsset || account.accountType.isLiability)) {
                        DropdownMenuItem(text = { Text("Reconcile balance") }, onClick = { menuExpanded = false; onReconcile() })
                    }
                    DropdownMenuItem(text = { Text("Edit account") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (account.isArchived) "Restore" else "Archive") },
                        onClick = { menuExpanded = false; onArchiveToggle() }
                    )
                    DropdownMenuItem(text = { Text("Delete if unused") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun AccountReconciliationDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Money, String) -> Unit
) {
    var actualBalanceText by remember(account.id) { mutableStateOf("") }
    var reason by remember(account.id) { mutableStateOf("") }
    var confirmDiscard by remember(account.id) { mutableStateOf(false) }
    val isDirty = actualBalanceText.isNotBlank() || reason.isNotBlank()
    val requestDismiss = { if (isDirty) confirmDiscard = true else onDismiss() }
    var error by remember(account.id) { mutableStateOf<String?>(null) }
    val actualBalance = Money.parse(actualBalanceText)
    val difference = actualBalance?.minus(account.currentLedgerBalance)

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text("Reconcile ${account.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recorded balance: ${displayMoney(account.currentLedgerBalance)}")
                MoneyAmountField(
                    value = actualBalanceText,
                    onValueChange = { actualBalanceText = it; error = null },
                    label = "Verified balance (PHP)"
                )
                difference?.let { Text("Adjustment: ${displayMoney(it)}", style = MaterialTheme.typography.labelLarge) }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; error = null },
                    label = { Text("Reason for correction") },
                    supportingText = { Text("This posts an audited balance adjustment. It preserves the opening balance and past entries.") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    actualBalance == null -> error = "Enter a valid balance with up to two decimal places."
                    !difference!!.isZero && reason.isBlank() -> error = "Add a reason for the balance adjustment."
                    else -> onSave(actualBalance, reason.trim())
                }
            }) { Text("Save adjustment") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, AccountType, Money, String, String, Boolean) -> Unit
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var institution by remember(account?.id) { mutableStateOf(account?.institution.orEmpty()) }
    var description by remember(account?.id) { mutableStateOf(account?.description.orEmpty()) }
    var balanceText by remember(account?.id) { mutableStateOf(account?.openingBalance?.let { "%.2f".format(Locale.US, it.centavos / 100.0) }.orEmpty()) }
    var inputError by remember(account?.id) { mutableStateOf<String?>(null) }
    var selectedType by remember(account?.id) { mutableStateOf(account?.accountType ?: AccountType.TRADITIONAL_BANK) }
    var selectedIcon by remember(account?.id) { mutableStateOf(account?.iconName ?: accountIcons.first()) }
    var selectedColor by remember(account?.id) { mutableStateOf(account?.colorHex ?: accountColors[1]) }
    var includeInDashboard by remember(account?.id) { mutableStateOf(account?.includeInDashboardTotals ?: true) }
    var expanded by remember { mutableStateOf(false) }
    val originalName = remember(account?.id) { name }
    val originalInstitution = remember(account?.id) { institution }
    val originalDescription = remember(account?.id) { description }
    val originalBalanceText = remember(account?.id) { balanceText }
    val originalType = remember(account?.id) { selectedType }
    val originalIcon = remember(account?.id) { selectedIcon }
    val originalColor = remember(account?.id) { selectedColor }
    val originalDashboardInclusion = remember(account?.id) { includeInDashboard }
    var confirmDiscard by remember(account?.id) { mutableStateOf(false) }
    val requestDismiss = {
        if (name != originalName || institution != originalInstitution || description != originalDescription ||
            (account == null && balanceText != originalBalanceText) || selectedType != originalType ||
            selectedIcon != originalIcon || selectedColor != originalColor || includeInDashboard != originalDashboardInclusion
        ) confirmDiscard = true else onDismiss()
    }
    val selectableTypes = AccountType.entries.filter {
        it != AccountType.EQUITY && it != AccountType.INCOME && it != AccountType.EXPENSE
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (account == null) "Add account" else "Edit account") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; inputError = null },
                    label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = institution, onValueChange = { institution = it },
                    label = { Text("Bank or institution") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )

                if (account == null) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedType.displayName, onValueChange = {}, readOnly = true,
                            label = { Text("Account type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            selectableTypes.forEach { type ->
                                DropdownMenuItem(text = { Text(type.displayName) }, onClick = {
                                    selectedType = type
                                    expanded = false
                                })
                            }
                        }
                    }
                    Column {
                        MoneyAmountField(
                            value = balanceText,
                            onValueChange = { balanceText = it; inputError = null },
                            label = "Opening balance"
                        )
                        Text(
                            "Use the verified balance when this account is first added.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "${account.accountType.displayName} · Opening balance ${displayMoney(account.openingBalance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .semantics(mergeDescendants = true) { }
                        .toggleable(
                            value = includeInDashboard,
                            role = Role.Checkbox,
                            onValueChange = { includeInDashboard = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includeInDashboard, onCheckedChange = null)
                    Text("Include in dashboard totals", style = MaterialTheme.typography.bodyMedium)
                }

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accountIcons.forEach { icon ->
                        IconButton(
                            onClick = { selectedIcon = icon },
                            modifier = Modifier.size(42.dp).border(
                                width = if (selectedIcon == icon) 2.dp else 1.dp,
                                color = if (selectedIcon == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                        ) {
                            Icon(accountIcon(icon), contentDescription = icon, tint = accountColor(selectedColor))
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accountColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(accountColor(color), CircleShape)
                                .border(
                                    width = if (selectedColor.equals(color, true)) 3.dp else 1.dp,
                                    color = if (selectedColor.equals(color, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = { selectedColor = color },
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (selectedColor.equals(color, true)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected color", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val openingBalance = if (account == null) {
                    if (balanceText.isBlank()) Money.ZERO else Money.parse(balanceText)
                } else Money.ZERO
                when {
                    name.isBlank() -> inputError = "Enter an account name."
                    account == null && openingBalance == null -> inputError = "Enter an amount with up to two decimal places."
                    account == null && openingBalance!!.isNegative -> inputError = "Opening balance cannot be negative."
                    else -> onSave(
                        name.trim(), institution.trim(), description.trim(), selectedType,
                        openingBalance ?: Money.ZERO, selectedIcon, selectedColor, includeInDashboard
                    )
                }
            }) { Text(if (account == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
}

@Composable
private fun AccountHistoryDialog(account: Account, transactions: List<Transaction>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${account.name} history") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${account.accountType.displayName} · ${displayMoney(account.currentLedgerBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (account.description.isNotBlank()) Text(account.description, style = MaterialTheme.typography.bodySmall)
                if (transactions.isEmpty()) {
                    Text("No transactions recorded for this account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(transactions, key = { it.journalEntryId }) { transaction ->
                            TransactionHistoryRow(transaction)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun TransactionHistoryRow(transaction: Transaction) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(transaction.description.ifBlank { transaction.transactionType.displayName }, modifier = Modifier.weight(1f))
            Text(displayMoney(transaction.amount), fontWeight = FontWeight.Medium)
        }
        Text(
            "${displayDate(transaction.timestamp, includeTime = true)} · ${transaction.transactionType.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (transaction.categoryName != null) {
            Text(transaction.categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun accountColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF1565C0))

internal fun accountIcon(iconName: String): ImageVector = when (iconName) {
    "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
    "savings" -> Icons.Default.Savings
    "credit_card" -> Icons.Default.CreditCard
    "wallet" -> Icons.Default.Wallet
    "payments" -> Icons.Default.Payments
    else -> Icons.Default.AccountBalance
}
