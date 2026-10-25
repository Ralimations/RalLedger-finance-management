package com.ralledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ralledger.data.importing.CsvColumnMapping
import com.ralledger.data.importing.CsvDateOrder
import com.ralledger.data.importing.CsvImportPreviewRow
import com.ralledger.domain.model.CategoryType
import java.util.Locale

private const val IMPORT_PREVIEW_PAGE_SIZE = 5

@Composable
fun CsvImportMappingDialog(
    state: CsvImportUiState,
    onMappingChange: (CsvColumnMapping) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    var previewPage by remember(state.preview?.rows?.size) { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = { if (!state.isImporting) onCancel() },
        title = { Text("Review import · ${state.sourceFormat.displayName}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Check the detected columns and preview. Only income, expenses and transfers are imported. Nothing is added until you confirm.")
                CsvColumnPicker("Date · required", state.headers, state.mapping.date) { onMappingChange(state.mapping.copy(date = it)) }
                CsvDateOrderPicker(state.mapping.dateOrder) { onMappingChange(state.mapping.copy(dateOrder = it)) }
                CsvColumnPicker("Type", state.headers, state.mapping.type) { onMappingChange(state.mapping.copy(type = it)) }
                CsvColumnPicker("Amount", state.headers, state.mapping.amount) {
                    onMappingChange(state.mapping.copy(amount = it, debitAmount = null, creditAmount = null))
                }
                CsvColumnPicker("Debit amount", state.headers, state.mapping.debitAmount) {
                    onMappingChange(state.mapping.copy(amount = null, debitAmount = it))
                }
                CsvColumnPicker("Credit amount", state.headers, state.mapping.creditAmount) {
                    onMappingChange(state.mapping.copy(amount = null, creditAmount = it))
                }
                CsvColumnPicker("Account from file", state.headers, state.mapping.account) { onMappingChange(state.mapping.copy(account = it)) }
                CsvRecordPicker(
                    label = "Use this account for every row",
                    options = state.accounts.filter { it.accountType.isAsset && !it.isArchived && !it.accountType.isReceivable }
                        .map { it.id to it.name },
                    selected = state.mapping.defaultAccountId,
                    onSelect = { onMappingChange(state.mapping.copy(defaultAccountId = it)) }
                )
                CsvColumnPicker("Destination account", state.headers, state.mapping.destinationAccount) { onMappingChange(state.mapping.copy(destinationAccount = it)) }
                CsvRecordPicker(
                    label = "Transfer destination for every row",
                    options = state.accounts.filter { it.accountType.isAsset && !it.isArchived && !it.accountType.isReceivable }
                        .map { it.id to it.name },
                    selected = state.mapping.defaultDestinationAccountId,
                    onSelect = { onMappingChange(state.mapping.copy(defaultDestinationAccountId = it)) }
                )
                CsvColumnPicker("Category", state.headers, state.mapping.category) { onMappingChange(state.mapping.copy(category = it)) }
                CsvRecordPicker(
                    label = "Default income category",
                    options = state.categories.filter { it.type == CategoryType.INCOME && !it.isArchived }.map { it.id to it.name },
                    selected = state.mapping.defaultIncomeCategoryId,
                    onSelect = { onMappingChange(state.mapping.copy(defaultIncomeCategoryId = it)) }
                )
                CsvRecordPicker(
                    label = "Default expense category",
                    options = state.categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived }.map { it.id to it.name },
                    selected = state.mapping.defaultExpenseCategoryId,
                    onSelect = { onMappingChange(state.mapping.copy(defaultExpenseCategoryId = it)) }
                )
                CsvColumnPicker("Transfer fee", state.headers, state.mapping.fee) { onMappingChange(state.mapping.copy(fee = it)) }
                CsvColumnPicker("Fee category", state.headers, state.mapping.feeCategory) { onMappingChange(state.mapping.copy(feeCategory = it)) }
                CsvColumnPicker("Description", state.headers, state.mapping.description) { onMappingChange(state.mapping.copy(description = it)) }
                CsvColumnPicker("Counterparty", state.headers, state.mapping.counterparty) { onMappingChange(state.mapping.copy(counterparty = it)) }
                CsvColumnPicker("Notes", state.headers, state.mapping.notes) { onMappingChange(state.mapping.copy(notes = it)) }
                CsvColumnPicker("Currency", state.headers, state.mapping.currency) { onMappingChange(state.mapping.copy(currency = it)) }
                CsvColumnPicker("Labels", state.headers, state.mapping.labels) { onMappingChange(state.mapping.copy(labels = it)) }

                state.preview?.let { preview ->
                    Text(
                        "Ready ${preview.importableCount} · Saved duplicates ${preview.duplicateCount} · Repeated rows ${preview.sameFileDuplicateCount} · Needs attention ${preview.errorCount}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Rows matching saved transactions are skipped. Identical rows in this file are kept; check for overlapping statement exports.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    preview.warnings.take(4).forEach { warning ->
                        Text(warning, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    preview.rows.filter { it.sameFileDuplicate }.take(6).forEach { row ->
                        Text("Row ${row.lineNumber}: ${row.warning}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.sameFileDuplicateCount > 6) {
                        Text("${preview.sameFileDuplicateCount - 6} more repeated rows are kept.", style = MaterialTheme.typography.bodySmall)
                    }
                    preview.rows.filter { it.error != null }.take(6).forEach { row ->
                        Text("Row ${row.lineNumber}: ${row.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.errorCount > 6) {
                        Text("${preview.errorCount - 6} more rows need attention.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Transaction preview", style = MaterialTheme.typography.titleMedium)
                    if (preview.rows.isEmpty()) {
                        Text("No transaction rows were found in this file.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val pageCount = (preview.rows.size + IMPORT_PREVIEW_PAGE_SIZE - 1) / IMPORT_PREVIEW_PAGE_SIZE
                        val currentPage = previewPage.coerceIn(0, pageCount - 1)
                        val firstRow = currentPage * IMPORT_PREVIEW_PAGE_SIZE
                        val visibleRows = preview.rows.drop(firstRow).take(IMPORT_PREVIEW_PAGE_SIZE)
                        Text(
                            "Rows ${firstRow + 1}–${firstRow + visibleRows.size} of ${preview.rows.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        visibleRows.forEach { row -> CsvImportPreviewCard(row, state) }
                        if (pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { previewPage = currentPage - 1 },
                                    enabled = currentPage > 0
                                ) { Text("Previous") }
                                OutlinedButton(
                                    onClick = { previewPage = currentPage + 1 },
                                    enabled = currentPage < pageCount - 1
                                ) { Text("Next") }
                            }
                        }
                    }
                } ?: Text("Checking the selected columns…", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = !state.isImporting && (state.preview?.importableCount ?: 0) > 0
            ) {
                val count = state.preview?.importableCount ?: 0
                Text(
                    when {
                        state.isImporting -> "Importing…"
                        count == 1 -> "Import 1 row"
                        else -> "Import $count rows"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !state.isImporting) { Text("Cancel") }
        }
    )
}

@Composable
private fun CsvImportPreviewCard(row: CsvImportPreviewRow, state: CsvImportUiState) {
    val transaction = row.transaction
    val status = when {
        row.error != null -> "Needs attention"
        row.duplicate -> "Already saved · skipped"
        row.sameFileDuplicate -> "Repeated in file · kept"
        else -> "Ready to import"
    }
    val statusColor = when {
        row.error != null -> MaterialTheme.colorScheme.error
        row.duplicate -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Row ${row.lineNumber} · $status", style = MaterialTheme.typography.labelLarge, color = statusColor)
            if (transaction == null) {
                Text(row.error ?: "This row could not be previewed.", style = MaterialTheme.typography.bodySmall)
            } else {
                val account = state.accounts.firstOrNull { it.id == transaction.accountId }
                val destination = transaction.destinationAccountId?.let { id ->
                    state.accounts.firstOrNull { it.id == id }?.name
                }
                val category = transaction.categoryId?.let { id ->
                    state.categories.firstOrNull { it.id == id }?.name
                }
                val typeLabel = when (transaction.transactionType.name) {
                    "INCOME" -> "Income"
                    "EXPENSE" -> "Expense"
                    "TRANSFER" -> "Transfer"
                    else -> transaction.transactionType.name.lowercase(Locale.getDefault())
                        .replaceFirstChar { it.uppercase(Locale.getDefault()) }
                }
                Text(
                    "$typeLabel · ${displayMoney(transaction.amount, account?.currency ?: "PHP")}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(displayDate(transaction.timestamp), style = MaterialTheme.typography.bodySmall)
                Text(transaction.description.ifBlank { "Imported transaction" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (destination == null) "Account: ${account?.name ?: "Unavailable"}"
                    else "Transfer: ${account?.name ?: "Unavailable"} → $destination",
                    style = MaterialTheme.typography.bodySmall
                )
                category?.let { Text("Category: $it", style = MaterialTheme.typography.bodySmall) }
                if (transaction.fee.isPositive) {
                    Text("Fee: ${displayMoney(transaction.fee, account?.currency ?: "PHP")}", style = MaterialTheme.typography.bodySmall)
                }
                if (transaction.counterparty.isNotBlank()) {
                    Text("Counterparty: ${transaction.counterparty}", style = MaterialTheme.typography.bodySmall)
                }
                if (transaction.notes.isNotBlank()) {
                    Text("Notes: ${transaction.notes}", style = MaterialTheme.typography.bodySmall)
                }
                val labels = state.tags.filter { it.id in transaction.tagIds }.joinToString { it.name }
                if (labels.isNotBlank()) Text("Labels: $labels", style = MaterialTheme.typography.bodySmall)
            }
            row.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun CsvRecordPicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            val selectedName = options.firstOrNull { it.first == selected }?.second ?: "Use file value / choose later"
            Text("$label: $selectedName", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Use file value / choose later") },
                onClick = { expanded = false; onSelect(null) }
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun CsvColumnPicker(
    label: String,
    headers: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            val value = selected?.let { "${it + 1}. ${headers.getOrNull(it).orEmpty()}" } ?: "Not mapped"
            Text("$label: $value", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Not mapped") },
                onClick = { expanded = false; onSelect(null) }
            )
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text("${index + 1}. $header") },
                    onClick = { expanded = false; onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun CsvDateOrderPicker(selected: CsvDateOrder, onSelect: (CsvDateOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date order: ${selected.displayName}", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CsvDateOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.displayName) },
                    onClick = { expanded = false; onSelect(order) }
                )
            }
        }
    }
}
