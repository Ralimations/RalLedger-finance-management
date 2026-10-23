package com.ralledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ralledger.data.importing.CsvColumnMapping
import com.ralledger.data.importing.CsvDateOrder

@Composable
fun CsvImportMappingDialog(
    state: CsvImportUiState,
    onMappingChange: (CsvColumnMapping) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isImporting) onCancel() },
        title = { Text("Map CSV columns") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose how the file columns match your records. Only income, expenses and transfers are imported. Rows must match existing active accounts and categories.")
                CsvColumnPicker("Date · required", state.headers, state.mapping.date) { onMappingChange(state.mapping.copy(date = it)) }
                CsvDateOrderPicker(state.mapping.dateOrder) { onMappingChange(state.mapping.copy(dateOrder = it)) }
                CsvColumnPicker("Type", state.headers, state.mapping.type) { onMappingChange(state.mapping.copy(type = it)) }
                CsvColumnPicker("Amount · required", state.headers, state.mapping.amount) { onMappingChange(state.mapping.copy(amount = it)) }
                CsvColumnPicker("Account · required", state.headers, state.mapping.account) { onMappingChange(state.mapping.copy(account = it)) }
                CsvColumnPicker("Destination account", state.headers, state.mapping.destinationAccount) { onMappingChange(state.mapping.copy(destinationAccount = it)) }
                CsvColumnPicker("Category", state.headers, state.mapping.category) { onMappingChange(state.mapping.copy(category = it)) }
                CsvColumnPicker("Transfer fee", state.headers, state.mapping.fee) { onMappingChange(state.mapping.copy(fee = it)) }
                CsvColumnPicker("Fee category", state.headers, state.mapping.feeCategory) { onMappingChange(state.mapping.copy(feeCategory = it)) }
                CsvColumnPicker("Description", state.headers, state.mapping.description) { onMappingChange(state.mapping.copy(description = it)) }
                CsvColumnPicker("Counterparty", state.headers, state.mapping.counterparty) { onMappingChange(state.mapping.copy(counterparty = it)) }
                CsvColumnPicker("Notes", state.headers, state.mapping.notes) { onMappingChange(state.mapping.copy(notes = it)) }
                CsvColumnPicker("Currency", state.headers, state.mapping.currency) { onMappingChange(state.mapping.copy(currency = it)) }
                CsvColumnPicker("Labels", state.headers, state.mapping.labels) { onMappingChange(state.mapping.copy(labels = it)) }

                state.preview?.let { preview ->
                    Text(
                        "Ready ${preview.importableCount} · Duplicates ${preview.duplicateCount} · Needs attention ${preview.errorCount}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Duplicate rows are skipped. Unsupported types and unmatched accounts/categories are not imported.", style = MaterialTheme.typography.bodySmall)
                    preview.warnings.take(4).forEach { warning ->
                        Text(warning, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    preview.rows.filter { it.error != null }.take(6).forEach { row ->
                        Text("Row ${row.lineNumber}: ${row.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.errorCount > 6) {
                        Text("${preview.errorCount - 6} more rows need attention.", style = MaterialTheme.typography.bodySmall)
                    }
                } ?: Text("Checking the selected columns…", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = !state.isImporting && (state.preview?.importableCount ?: 0) > 0
            ) {
                Text(if (state.isImporting) "Importing…" else "Import ${state.preview?.importableCount ?: 0} rows")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !state.isImporting) { Text("Cancel") }
        }
    )
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
