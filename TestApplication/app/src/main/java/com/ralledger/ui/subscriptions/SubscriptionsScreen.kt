package com.ralledger.ui.subscriptions

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
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var includeArchived by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    var pendingCancel by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    val visibleSubscriptions = subscriptions.filter { includeArchived || !it.isArchived }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingSubscription = null
                showEditor = true
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add subscription")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recurring Subscriptions",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text("Manage recurring services and billing dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(
                selected = includeArchived,
                onClick = { includeArchived = !includeArchived },
                label = { Text(if (includeArchived) "Showing archived" else "Show archived") }
            )
            if (visibleSubscriptions.isEmpty()) {
                Text(if (includeArchived) "No subscriptions." else "No subscriptions added. Tap + to add one.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleSubscriptions, key = { it.id }) { subscription ->
                        SubscriptionCard(
                            subscription = subscription,
                            onEdit = { editingSubscription = subscription; showEditor = true },
                            onPauseResume = {
                                viewModel.setStatus(
                                    subscription,
                                    if (subscription.status == SubscriptionStatus.ACTIVE) SubscriptionStatus.PAUSED else SubscriptionStatus.ACTIVE
                                )
                            },
                            onCancel = { pendingCancel = subscription },
                            onReactivate = { viewModel.setStatus(subscription, SubscriptionStatus.ACTIVE) },
                            onArchive = { viewModel.setArchived(subscription, true) },
                            onRestore = { viewModel.setArchived(subscription, false) },
                            onDelete = { pendingDelete = subscription }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        SubscriptionEditorDialog(
            accounts = accounts.filter { it.accountType.isLiquid && !it.isArchived },
            subscription = editingSubscription,
            onDismiss = { showEditor = false },
            onConfirm = { name, merchant, amount, frequency, accountId, nextDate, notes ->
                val existing = editingSubscription
                if (existing == null) {
                    viewModel.createSubscription(name, merchant, amount, frequency, accountId, nextDate, notes)
                } else {
                    viewModel.updateSubscription(
                        existing.copy(
                            name = name,
                            merchant = merchant,
                            billingAmount = amount,
                            billingFrequency = frequency,
                            assignedAccountId = accountId,
                            nextBillingDate = nextDate,
                            notes = notes
                        )
                    )
                }
                showEditor = false
            }
        )
    }

    pendingCancel?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel ${subscription.name}?") },
            text = { Text("This stops including it in active monthly commitments. Past records remain unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(subscription, SubscriptionStatus.CANCELLED)
                    pendingCancel = null
                }) { Text("Cancel subscription") }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("Keep active") } }
        )
    }

    pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${subscription.name}?") },
            text = { Text("This removes the subscription schedule. Past transactions are not changed. If a payment is recorded, deletion is blocked; cancel or archive the subscription instead.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubscription(subscription)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Subscription update") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onEdit: () -> Unit,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onReactivate: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (subscription.isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(subscription.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (subscription.merchant.isNotBlank()) Text("Merchant: ${subscription.merchant}", style = MaterialTheme.typography.bodySmall)
                Text("Assigned: ${subscription.assignedAccountName}", style = MaterialTheme.typography.labelSmall)
                Text("Next bill: ${formatDate(subscription.nextBillingDate)}", style = MaterialTheme.typography.labelSmall)
                subscription.lastPaymentDate?.let { Text("Last payment: ${formatDate(it)}", style = MaterialTheme.typography.labelSmall) }
                Text(
                    if (subscription.isArchived) "Archived" else subscription.status.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (subscription.status == SubscriptionStatus.CANCELLED || subscription.isArchived) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(subscription.billingAmount.format(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(subscription.billingFrequency.displayName, style = MaterialTheme.typography.labelSmall)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "${subscription.name} actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (subscription.isArchived) {
                            DropdownMenuItem(text = { Text("Restore") }, onClick = { menuExpanded = false; onRestore() })
                        } else {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                            when (subscription.status) {
                                SubscriptionStatus.ACTIVE -> {
                                    DropdownMenuItem(text = { Text("Pause") }, onClick = { menuExpanded = false; onPauseResume() })
                                    DropdownMenuItem(text = { Text("Cancel subscription") }, onClick = { menuExpanded = false; onCancel() })
                                }
                                SubscriptionStatus.PAUSED -> {
                                    DropdownMenuItem(text = { Text("Resume") }, onClick = { menuExpanded = false; onPauseResume() })
                                    DropdownMenuItem(text = { Text("Cancel subscription") }, onClick = { menuExpanded = false; onCancel() })
                                }
                                SubscriptionStatus.CANCELLED -> DropdownMenuItem(
                                    text = { Text("Reactivate") }, onClick = { menuExpanded = false; onReactivate() }
                                )
                            }
                            DropdownMenuItem(text = { Text("Archive") }, onClick = { menuExpanded = false; onArchive() })
                        }
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubscriptionEditorDialog(
    accounts: List<Account>,
    subscription: Subscription?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, merchant: String, amount: Money, frequency: BillingFrequency, accountId: String, nextBillingDate: Long, notes: String) -> Unit
) {
    var name by remember(subscription?.id) { mutableStateOf(subscription?.name.orEmpty()) }
    var merchant by remember(subscription?.id) { mutableStateOf(subscription?.merchant.orEmpty()) }
    var amountText by remember(subscription?.id) { mutableStateOf(subscription?.billingAmount?.centavos?.let(::moneyInput).orEmpty()) }
    var notes by remember(subscription?.id) { mutableStateOf(subscription?.notes.orEmpty()) }
    var selectedAccountId by remember(subscription?.id) { mutableStateOf(subscription?.assignedAccountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var selectedFrequency by remember(subscription?.id) { mutableStateOf(subscription?.billingFrequency ?: BillingFrequency.MONTHLY) }
    var nextBillingDate by remember(subscription?.id) {
        mutableStateOf(subscription?.nextBillingDate ?: System.currentTimeMillis() + 30L * 86_400_000L)
    }
    var datePickerOpen by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val datePickerState = if (datePickerOpen) rememberDatePickerState(initialSelectedDateMillis = nextBillingDate) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (subscription == null) "Add subscription" else "Edit subscription") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { name = it; inputError = null }, label = { Text("Subscription name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / service") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !accountExpanded }) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "Select payment account",
                        onValueChange = {}, readOnly = true, label = { Text("Payment account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { selectedAccountId = account.id; accountExpanded = false }) }
                    }
                }

                ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = !frequencyExpanded }) {
                    OutlinedTextField(
                        value = selectedFrequency.displayName, onValueChange = {}, readOnly = true,
                        label = { Text("Billing frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                        BillingFrequency.entries.forEach { frequency ->
                            DropdownMenuItem(text = { Text(frequency.displayName) }, onClick = { selectedFrequency = frequency; frequencyExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it; inputError = null },
                    label = { Text("Billing amount (PHP)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { datePickerOpen = true }) { Text("Next billing date: ${formatDate(nextBillingDate)}") }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                when {
                    name.isBlank() -> inputError = "Enter a subscription name."
                    selectedAccountId.isBlank() -> inputError = "Choose a payment account."
                    amount == null || !amount.isPositive -> inputError = "Enter a valid billing amount greater than zero."
                    else -> onConfirm(name.trim(), merchant.trim(), amount, selectedFrequency, selectedAccountId, nextBillingDate, notes.trim())
                }
            }) { Text(if (subscription == null) "Save subscription" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (datePickerOpen && datePickerState != null) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { nextBillingDate = it }
                    datePickerOpen = false
                }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }
}

private fun moneyInput(centavos: Long): String = "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"

private fun formatDate(timestamp: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
