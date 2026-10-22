package com.ralledger.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var reverseCandidate by remember { mutableStateOf<Transaction?>(null) }

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
            Text(
                text = "Transaction Journal",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (transactions.isEmpty()) {
                Text("No transactions found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { tx ->
                        TransactionRow(
                            transaction = tx,
                            onReverse = { reverseCandidate = tx }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecordTransactionDialog(
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddDialog = false },
            onIncome = { accountId, catId, amount, desc ->
                viewModel.recordIncome(accountId, catId, amount, desc)
                showAddDialog = false
            },
            onExpense = { accountId, catId, amount, desc ->
                viewModel.recordExpense(accountId, catId, amount, desc)
                showAddDialog = false
            },
            onTransfer = { srcId, destId, amount, fee, feeCatId, desc ->
                viewModel.recordTransfer(srcId, destId, amount, fee, feeCatId, desc)
                showAddDialog = false
            }
        )
    }

    reverseCandidate?.let { transaction ->
        AlertDialog(
            onDismissRequest = { reverseCandidate = null },
            title = { Text("Reverse transaction?") },
            text = { Text("A reversal entry will preserve the audit history and offset the original posting. This cannot be undone.") },
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
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onReverse: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val isPositive = transaction.transactionType == TransactionType.INCOME ||
        transaction.transactionType == TransactionType.RECEIVABLE_COLLECTION ||
        transaction.transactionType == TransactionType.OPENING_BALANCE
    val amountColor = when {
        isPositive -> Color(0xFF2E7D32)
        transaction.transactionType == TransactionType.TRANSFER || transaction.transactionType == TransactionType.REVERSAL -> Color(0xFF1565C0)
        transaction.transactionType == TransactionType.BALANCE_ADJUSTMENT -> Color(0xFF6A1B9A)
        else -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = dateFormat.format(Date(transaction.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${when (transaction.transactionType) {
                        TransactionType.INCOME, TransactionType.RECEIVABLE_COLLECTION -> "+"
                        TransactionType.EXPENSE, TransactionType.LOAN_REPAYMENT -> "−"
                        TransactionType.OPENING_BALANCE, TransactionType.REVERSAL, TransactionType.TRANSFER,
                        TransactionType.BALANCE_ADJUSTMENT, TransactionType.RECEIVABLE_RECOGNITION,
                        TransactionType.LOAN_DISBURSEMENT -> ""
                    }}${transaction.amount.format()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                if (transaction.transactionType != TransactionType.REVERSAL) {
                    TextButton(onClick = onReverse) { Text("Reverse", color = MaterialTheme.colorScheme.error) }
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
    onDismiss: () -> Unit,
    onIncome: (accountId: String, categoryId: String, amount: Money, desc: String) -> Unit,
    onExpense: (accountId: String, categoryId: String, amount: Money, desc: String) -> Unit,
    onTransfer: (srcId: String, destId: String, amount: Money, fee: Money, feeCatId: String?, desc: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Expense, 1: Income, 2: Transfer

    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedDestAccountId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: "") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull { it.type.name == "EXPENSE" }?.id ?: "") }
    var selectedFeeCategoryId by remember { mutableStateOf(categories.firstOrNull { it.type.name == "EXPENSE" }?.id ?: "") }

    var amountText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val visibleCategories = categories.filter { it.type.name == if (selectedTab == 1) "INCOME" else "EXPENSE" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = {
                        selectedTab = 0
                        selectedCategoryId = categories.firstOrNull { it.type.name == "EXPENSE" }?.id ?: ""
                    }, text = { Text("Expense") })
                    Tab(selected = selectedTab == 1, onClick = {
                        selectedTab = 1
                        selectedCategoryId = categories.firstOrNull { it.type.name == "INCOME" }?.id ?: ""
                    }, text = { Text("Income") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Transfer") })
                }

                // Account Selector
                AccountDropdown(
                    label = if (selectedTab == 2) "From Account" else "Account",
                    accounts = accounts,
                    selectedId = selectedAccountId,
                    onSelect = { selectedAccountId = it }
                )

                if (selectedTab == 2) {
                    AccountDropdown(
                        label = "To Account",
                        accounts = accounts,
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
                        categories = categories.filter { it.type.name == "EXPENSE" },
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
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedTab == 2) {
                    OutlinedTextField(
                        value = feeText,
                        onValueChange = { feeText = it; inputError = null },
                        label = { Text("Transfer Fee (optional, e.g. 15.00)") },
                        singleLine = true,
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
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = Money.parse(amountText)
                    when {
                        amount == null || !amount.isPositive -> inputError = "Enter a valid amount greater than zero."
                        selectedAccountId.isBlank() -> inputError = "Choose an account."
                        selectedTab == 0 && selectedCategoryId.isBlank() -> inputError = "Choose an expense category."
                        selectedTab == 1 && selectedCategoryId.isBlank() -> inputError = "Choose an income source."
                        selectedTab == 0 -> onExpense(selectedAccountId, selectedCategoryId, amount, description)
                        selectedTab == 1 -> onIncome(selectedAccountId, selectedCategoryId, amount, description)
                        else -> {
                            val fee = if (feeText.isBlank()) Money.ZERO else Money.parse(feeText)
                            when {
                                fee == null || fee.isNegative -> inputError = "Enter a valid non-negative transfer fee."
                                selectedDestAccountId.isBlank() || selectedDestAccountId == selectedAccountId -> inputError = "Choose a different destination account."
                                fee.isPositive && selectedFeeCategoryId.isBlank() -> inputError = "Choose an expense category for the fee."
                                else -> onTransfer(selectedAccountId, selectedDestAccountId, amount, fee, selectedFeeCategoryId.ifBlank { null }, description)
                            }
                        }
                    }
                }
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

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
