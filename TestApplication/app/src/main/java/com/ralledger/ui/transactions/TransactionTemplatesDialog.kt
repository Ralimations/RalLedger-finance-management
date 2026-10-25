package com.ralledger.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.TransactionType
import com.ralledger.ui.components.DiscardChangesDialog
import com.ralledger.ui.components.MoneyAmountField
import com.ralledger.ui.settings.displayMoney
import java.util.UUID

@Composable
internal fun TransactionTemplatesDialog(
    templates: List<TransactionTemplate>,
    accounts: List<Account>,
    categories: List<Category>,
    message: String?,
    onDismiss: () -> Unit,
    onCreate: (TransactionTemplate) -> Unit,
    onUpdate: (TransactionTemplate) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onUse: (TransactionTemplate) -> Unit,
    onClearMessage: () -> Unit
) {
    var showArchived by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TransactionTemplate?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var createNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TransactionTemplate?>(null) }
    val visible = templates.filter { template ->
        template.isArchived == showArchived &&
            (query.isBlank() || template.name.contains(query, ignoreCase = true) ||
                template.description.contains(query, ignoreCase = true) || template.counterparty.contains(query, ignoreCase = true))
    }

    if (!editorOpen && deleting == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Transaction templates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save common transaction details, then review them before posting.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search templates") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = !showArchived },
                        label = { Text(if (showArchived) "Archived" else "Show archived") }
                    )
                    message?.let { text ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text, color = if (text.contains("could not", true) || text.contains("already exists", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            TextButton(onClick = onClearMessage) { Text("Dismiss") }
                        }
                    }
                    if (visible.isEmpty()) {
                        Text(if (showArchived) "No archived templates." else "No templates yet.")
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visible.forEach { item ->
                                val usable = item.isArchived.not() && isTemplateUsable(item, accounts, categories)
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                                        Text(templateSummary(item, accounts, categories), style = MaterialTheme.typography.bodySmall)
                                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                                        if (!item.isArchived && !usable) {
                                            Text("Restore its account or category before using this template.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (!item.isArchived) {
                                                TextButton(onClick = { onUse(item); onDismiss() }, enabled = usable) { Text("Use") }
                                            }
                                            TextButton(onClick = { editing = item; createNew = false; editorOpen = true }) { Text("Edit") }
                                            TextButton(onClick = { onArchive(item.id, !item.isArchived) }) {
                                                Text(if (item.isArchived) "Restore" else "Archive")
                                            }
                                            TextButton(onClick = { deleting = item }) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { editing = null; createNew = true; editorOpen = true }) { Text("New template") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )
    }

    if (editorOpen) {
        TransactionTemplateEditorDialog(
            template = if (createNew) null else editing,
            accounts = accounts,
            categories = categories,
            onDismiss = { editorOpen = false; createNew = false },
            onSave = { value ->
                if (createNew) onCreate(value) else onUpdate(value)
                editorOpen = false
                createNew = false
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This removes the saved template. Transactions already posted from it remain unchanged.") },
            confirmButton = { Button(onClick = { onDelete(item.id); deleting = null }) { Text("Delete template") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TransactionTemplateEditorDialog(
    template: TransactionTemplate?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (TransactionTemplate) -> Unit
) {
    var name by remember(template?.id) { mutableStateOf(template?.name.orEmpty()) }
    var type by remember(template?.id) { mutableStateOf(template?.transactionType ?: TransactionType.EXPENSE) }
    var amountText by remember(template?.id) { mutableStateOf(template?.amount?.let(::moneyText).orEmpty()) }
    var feeText by remember(template?.id) { mutableStateOf(template?.fee?.takeIf { it.isPositive }?.let(::moneyText).orEmpty()) }
    var accountId by remember(template?.id, accounts) { mutableStateOf(template?.accountId ?: accounts.firstOrNull { !it.isArchived }?.id.orEmpty()) }
    var destinationId by remember(template?.id, accounts) {
        mutableStateOf(template?.destinationAccountId ?: accounts.firstOrNull { !it.isArchived && it.id != accountId }?.id.orEmpty())
    }
    var categoryId by remember(template?.id) {
        mutableStateOf(template?.categoryId ?: categories.firstOrNull { !it.isArchived && it.type == templateCategoryType(type) }?.id.orEmpty())
    }
    var feeCategoryId by remember(template?.id) {
        mutableStateOf(template?.feeCategoryId ?: categories.firstOrNull { !it.isArchived && it.type == CategoryType.EXPENSE }?.id.orEmpty())
    }
    var counterparty by remember(template?.id) { mutableStateOf(template?.counterparty.orEmpty()) }
    var description by remember(template?.id) { mutableStateOf(template?.description.orEmpty()) }
    var notes by remember(template?.id) { mutableStateOf(template?.notes.orEmpty()) }
    var error by remember(template?.id) { mutableStateOf<String?>(null) }
    val originalName = remember(template?.id) { name }
    val originalType = remember(template?.id) { type }
    val originalAmountText = remember(template?.id) { amountText }
    val originalFeeText = remember(template?.id) { feeText }
    val originalAccountId = remember(template?.id, accounts) { accountId }
    val originalDestinationId = remember(template?.id, accounts) { destinationId }
    val originalCategoryId = remember(template?.id) { categoryId }
    val originalFeeCategoryId = remember(template?.id) { feeCategoryId }
    val originalCounterparty = remember(template?.id) { counterparty }
    val originalDescription = remember(template?.id) { description }
    val originalNotes = remember(template?.id) { notes }
    var confirmDiscard by remember(template?.id) { mutableStateOf(false) }
    val requestDismiss = {
        if (name != originalName || type != originalType || amountText != originalAmountText || feeText != originalFeeText ||
            accountId != originalAccountId || destinationId != originalDestinationId || categoryId != originalCategoryId ||
            feeCategoryId != originalFeeCategoryId || counterparty != originalCounterparty || description != originalDescription || notes != originalNotes
        ) confirmDiscard = true else onDismiss()
    }
    val accountsForSelection = accounts.filter { !it.isArchived || it.id == template?.accountId || it.id == template?.destinationAccountId }
    val categoriesForSelection = categories.filter { !it.isArchived || it.id == template?.categoryId || it.id == template?.feeCategoryId }
    val categoryOptions = categoriesForSelection.filter { it.type == templateCategoryType(type) }
    val feeCategoryOptions = categoriesForSelection.filter { it.type == CategoryType.EXPENSE }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (template == null) "New transaction template" else "Edit transaction template") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TabRow(selectedTabIndex = when (type) {
                    TransactionType.EXPENSE -> 0
                    TransactionType.INCOME -> 1
                    else -> 2
                }) {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER).forEach { option ->
                        Tab(
                            selected = type == option,
                            onClick = {
                                type = option
                                if (option != TransactionType.TRANSFER) {
                                    categoryId = categories.firstOrNull { !it.isArchived && it.type == templateCategoryType(option) }?.id.orEmpty()
                                }
                            },
                            text = { Text(option.displayName) }
                        )
                    }
                }
                TemplateSelectionField("Account", accountsForSelection.map { it.id to (it.name + if (it.isArchived) " · Archived" else "") }, accountId) {
                    accountId = it
                    if (destinationId == it) destinationId = accountsForSelection.firstOrNull { option -> option.id != it && !option.isArchived }?.id.orEmpty()
                }
                if (type == TransactionType.TRANSFER) {
                    TemplateSelectionField("To account", accountsForSelection.filter { it.id != accountId }.map { it.id to (it.name + if (it.isArchived) " · Archived" else "") }, destinationId) { destinationId = it }
                } else {
                    TemplateSelectionField("Category", categoryOptions.map { it.id to (it.name + if (it.isArchived) " · Archived" else "") }, categoryId) { categoryId = it }
                }
                if (type == TransactionType.TRANSFER) {
                    TemplateSelectionField("Transfer fee category", feeCategoryOptions.map { it.id to (it.name + if (it.isArchived) " · Archived" else "") }, feeCategoryId) { feeCategoryId = it }
                }
                MoneyAmountField(amountText, { amountText = it; error = null }, "Amount (PHP)")
                if (type == TransactionType.TRANSFER) {
                    MoneyAmountField(feeText, { feeText = it; error = null }, "Transfer fee (optional)")
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (type != TransactionType.TRANSFER) {
                    OutlinedTextField(counterparty, { counterparty = it }, label = { Text("Source or counterparty") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                val fee = if (type == TransactionType.TRANSFER && feeText.isNotBlank()) Money.parse(feeText) else Money.ZERO
                when {
                    name.isBlank() -> error = "Enter a template name."
                    amount == null || !amount.isPositive -> error = "Enter an amount greater than zero."
                    accountId.isBlank() -> error = "Choose an account."
                    type != TransactionType.TRANSFER && categoryId.isBlank() -> error = "Choose a category."
                    type == TransactionType.TRANSFER && (destinationId.isBlank() || destinationId == accountId) -> error = "Choose a different destination account."
                    type == TransactionType.TRANSFER && fee == null -> error = "Enter a valid non-negative transfer fee."
                    type == TransactionType.TRANSFER && fee!!.isPositive && feeCategoryId.isBlank() -> error = "Choose a fee category."
                    else -> {
                        val now = System.currentTimeMillis()
                        onSave(
                            TransactionTemplate(
                                id = template?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                transactionType = type,
                                amount = amount,
                                fee = fee ?: Money.ZERO,
                                accountId = accountId,
                                destinationAccountId = if (type == TransactionType.TRANSFER) destinationId else null,
                                categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                                feeCategoryId = if (type == TransactionType.TRANSFER && feeCategoryId.isNotBlank()) feeCategoryId else null,
                                counterparty = counterparty,
                                description = description,
                                notes = notes,
                                isArchived = template?.isArchived ?: false,
                                createdAt = template?.createdAt ?: now,
                                updatedAt = now
                            )
                        )
                    }
                }
            }) { Text("Save template") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
}

@Composable
private fun TemplateSelectionField(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "Choose $label"
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selectedLabel")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

private fun templateCategoryType(type: TransactionType) = when (type) {
    TransactionType.INCOME -> CategoryType.INCOME
    else -> CategoryType.EXPENSE
}

private fun isTemplateUsable(template: TransactionTemplate, accounts: List<Account>, categories: List<Category>): Boolean {
    val account = accounts.firstOrNull { it.id == template.accountId }
    if (account == null || account.isArchived || !account.accountType.isAsset || account.accountType.isReceivable) return false
    if (template.transactionType == TransactionType.TRANSFER) {
        val destination = accounts.firstOrNull { it.id == template.destinationAccountId }
        if (destination == null || destination.isArchived || !destination.accountType.isAsset || destination.accountType.isReceivable || destination.id == account.id) return false
        val feeCategoryId = template.feeCategoryId
        if (template.fee.isPositive && (feeCategoryId == null || categories.none { it.id == feeCategoryId && it.type == CategoryType.EXPENSE && !it.isArchived })) return false
        return true
    }
    val expected = if (template.transactionType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    return categories.any { it.id == template.categoryId && it.type == expected && !it.isArchived }
}

@Composable
private fun templateSummary(template: TransactionTemplate, accounts: List<Account>, categories: List<Category>): String {
    val amount = displayMoney(template.amount)
    val account = accounts.firstOrNull { it.id == template.accountId }?.name ?: "Missing account"
    val category = when (template.transactionType) {
        TransactionType.TRANSFER -> "to " + (accounts.firstOrNull { it.id == template.destinationAccountId }?.name ?: "Missing account")
        else -> categories.firstOrNull { it.id == template.categoryId }?.name ?: "Missing category"
    }
    val fee = if (template.transactionType == TransactionType.TRANSFER && template.fee.isPositive) " · fee ${displayMoney(template.fee)}" else ""
    return "${template.transactionType.displayName} · $amount · $account · $category$fee" + if (template.isArchived) " · Archived" else ""
}

private fun moneyText(money: Money): String = java.math.BigDecimal.valueOf(money.centavos, 2).toPlainString()
