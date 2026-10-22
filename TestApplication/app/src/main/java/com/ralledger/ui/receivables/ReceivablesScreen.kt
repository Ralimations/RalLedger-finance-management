package com.ralledger.ui.receivables

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
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus

@Composable
fun ReceivablesScreen(
    viewModel: ReceivablesViewModel
) {
    val receivables by viewModel.receivables.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCollectReceivable by remember { mutableStateOf<Receivable?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Receivable")
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
                text = "Accounts Receivable",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Track unpaid client work, commissions & expected collections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (receivables.isEmpty()) {
                Text("No receivables recorded. Tap '+' to record uncollected money owed to you.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(receivables) { rec ->
                        ReceivableCard(
                            receivable = rec,
                            onCollect = { selectedCollectReceivable = rec }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReceivableDialog(
            categories = categories.filter { it.type == CategoryType.INCOME },
            onDismiss = { showAddDialog = false },
            onConfirm = { client, amount, desc, catId ->
                viewModel.createReceivable(client, amount, System.currentTimeMillis() + 30 * 86400000L, catId, desc)
                showAddDialog = false
            }
        )
    }

    selectedCollectReceivable?.let { rec ->
        CollectPaymentDialog(
            receivable = rec,
            accounts = accounts.filter { it.accountType.isLiquid && !it.isArchived },
            onDismiss = { selectedCollectReceivable = null },
            onConfirm = { accountId, amount, notes ->
                viewModel.collectReceivable(rec.id, accountId, amount, notes)
                selectedCollectReceivable = null
            }
        )
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Receivable update") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun ReceivableCard(
    receivable: Receivable,
    onCollect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = receivable.clientName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFE65100))
                    Text(text = "Status: ${receivable.status.displayName}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF6C00))
                }
                IconButton(
                    onClick = onCollect,
                    enabled = receivable.outstandingAmount.isPositive && receivable.status in setOf(
                        ReceivableStatus.CONFIRMED,
                        ReceivableStatus.PARTIALLY_PAID,
                        ReceivableStatus.OVERDUE
                    )
                ) {
                    Icon(imageVector = Icons.Default.Payments, contentDescription = "Collect", tint = Color(0xFFE65100))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Outstanding Amount", style = MaterialTheme.typography.labelSmall)
                    Text(text = receivable.outstandingAmount.format(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Original Amount", style = MaterialTheme.typography.labelSmall)
                    Text(text = receivable.originalAmount.format(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReceivableDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (client: String, amount: Money, desc: String, categoryId: String?) -> Unit
) {
    var client by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    LaunchedEffect(categories) {
        if (selectedCategoryId.isBlank() && categories.isNotEmpty()) selectedCategoryId = categories.first().id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Accounts Receivable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it; inputError = null },
                    label = { Text("Client / Counterparty Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; inputError = null },
                    label = { Text("Amount Owed (e.g. 15000.00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Select income source",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Income Source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Invoice Ref") },
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
                        client.isBlank() -> inputError = "Enter a client or counterparty name."
                        amount == null || !amount.isPositive -> inputError = "Enter a valid amount greater than zero."
                        selectedCategoryId.isBlank() -> inputError = "Choose an income source."
                        else -> onConfirm(client.trim(), amount, description, selectedCategoryId.ifBlank { null })
                    }
                }
            ) {
                Text("Confirm Receivable")
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
private fun CollectPaymentDialog(
    receivable: Receivable,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (destinationAccountId: String, amount: Money, notes: String) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf(receivable.outstandingAmount.format().replace("₱", "").replace("$", "").trim()) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val selectedName = accounts.find { it.id == selectedAccountId }?.name ?: "Select Destination Account"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect Receivable Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Client: ${receivable.clientName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Max Collectible: ${receivable.outstandingAmount.format()}", style = MaterialTheme.typography.bodySmall)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Destination Bank / Wallet") },
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
                                    selectedAccountId = acc.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; inputError = null },
                    label = { Text("Collection Amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Receipt Reference") },
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
                        selectedAccountId.isBlank() -> inputError = "Choose a destination account."
                        amount == null || !amount.isPositive -> inputError = "Enter a valid collection greater than zero."
                        amount > receivable.outstandingAmount -> inputError = "Collection exceeds the remaining balance."
                        else -> onConfirm(selectedAccountId, amount, notes)
                    }
                }
            ) {
                Text("Collect & Update Cash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
