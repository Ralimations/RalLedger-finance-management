package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.CounterpartyKind

@Composable
fun CounterpartiesScreen(viewModel: CounterpartiesViewModel) {
    val counterparties by viewModel.counterparties.collectAsState()
    val receivables by viewModel.receivables.collectAsState()
    val expectedIncome by viewModel.expectedIncome.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Counterparty?>(null) }
    var actionItem by remember { mutableStateOf<Counterparty?>(null) }
    var deleting by remember { mutableStateOf<Counterparty?>(null) }
    var merging by remember { mutableStateOf<Counterparty?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message!!)
            viewModel.clearMessage()
        }
    }

    val filtered = counterparties.filter { item ->
        item.isArchived == showArchived &&
            (query.isBlank() || item.name.contains(query, true) || item.notes.contains(query, true) || item.kind.displayName.contains(query, true))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; editorOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add counterparty")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search clients and counterparties") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FilterChip(
                    selected = showArchived,
                    onClick = { showArchived = !showArchived },
                    label = { Text(if (showArchived) "Showing archived" else "Show archived") }
                )
            }
            if (filtered.isEmpty()) {
                item { Text(if (query.isNotBlank()) "No matching counterparties." else if (showArchived) "No archived counterparties." else "No counterparties yet.") }
            }
            items(filtered, key = { it.id }) { item ->
                val linkedCount = receivables.count { it.counterpartyId == item.id } +
                    expectedIncome.count { it.counterpartyId == item.id }
                CounterpartyCard(
                    item = item,
                    linkedCount = linkedCount,
                    onEdit = { editing = item; editorOpen = true },
                    onMerge = { merging = item },
                    onArchive = { viewModel.archive(item, true) },
                    onRestore = { viewModel.archive(item, false) },
                    onDelete = { deleting = item }
                )
            }
        }
    }

    if (editorOpen) {
        CounterpartyEditorDialog(
            item = editing,
            onDismiss = { editorOpen = false },
            onSave = { name, kind, notes ->
                if (editing == null) viewModel.create(name, kind, notes)
                else viewModel.update(editing!!, name, kind, notes)
                editorOpen = false
            }
        )
    }

    deleting?.let { item ->
        val linkedCount = receivables.count { it.counterpartyId == item.id } +
            expectedIncome.count { it.counterpartyId == item.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${item.name}?") },
            text = {
                Text(
                    if (linkedCount == 0) "This removes the saved counterparty record. No linked receivables or expected-income records will be changed."
                    else "$linkedCount receivable or expected-income records reference ${item.name}. Linked contacts cannot be deleted; archive or merge this record to keep those relationships safe."
                )
            },
            confirmButton = {
                if (linkedCount == 0) {
                    Button(onClick = { viewModel.delete(item); deleting = null }) { Text("Delete") }
                } else {
                    Button(onClick = { viewModel.archive(item, true); deleting = null }) { Text("Archive instead") }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    merging?.let { source ->
        val targets = counterparties.filter { it.id != source.id && !it.isArchived }
        var selectedId by remember(source.id, targets) { mutableStateOf(targets.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text("Merge ${source.name}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Linked receivables and expected-income records will move to the selected counterparty. Journal entries keep their original names.")
                    targets.forEach { target ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == target.id, onClick = { selectedId = target.id })
                            Text(target.name)
                        }
                    }
                    if (targets.isEmpty()) Text("Create or restore another counterparty before merging.")
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedId != null,
                    onClick = {
                        targets.firstOrNull { it.id == selectedId }?.let { viewModel.merge(source, it) }
                        merging = null
                    }
                ) { Text("Merge records") }
            },
            dismissButton = { TextButton(onClick = { merging = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CounterpartyCard(
    item: Counterparty,
    linkedCount: Int,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(item.kind.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall)
                Text("$linkedCount linked financial records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "${item.name} actions")
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    if (!item.isArchived) {
                        DropdownMenuItem(text = { Text("Merge") }, onClick = { menuExpanded = false; onMerge() })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = { menuExpanded = false; onArchive() })
                    } else {
                        DropdownMenuItem(text = { Text("Restore") }, onClick = { menuExpanded = false; onRestore() })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun CounterpartyEditorDialog(
    item: Counterparty?,
    onDismiss: () -> Unit,
    onSave: (String, CounterpartyKind, String) -> Unit
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var kind by remember(item?.id) { mutableStateOf(item?.kind ?: CounterpartyKind.CLIENT) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add counterparty" else "Edit counterparty") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Contact type", style = MaterialTheme.typography.labelLarge)
                CounterpartyKind.values().forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(option.displayName) }
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = "Enter a name."
                else onSave(name.trim(), kind, notes.trim())
            }) { Text(if (item == null) "Save" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
