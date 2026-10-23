package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.SavingsGoal
import com.ralledger.domain.model.SavingsGoalEventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SavingsGoalsScreen(viewModel: SavingsGoalsViewModel) {
    val goals by viewModel.goals.collectAsState()
    val message by viewModel.message.collectAsState()
    var showArchived by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var eventTarget by remember { mutableStateOf<SavingsGoal?>(null) }
    var eventType by remember { mutableStateOf(SavingsGoalEventType.ALLOCATE) }
    var deleteTarget by remember { mutableStateOf<SavingsGoal?>(null) }

    val visibleGoals = goals.filter { (showArchived || !it.isArchived) &&
        (search.isBlank() || it.name.contains(search, ignoreCase = true) || it.notes.contains(search, ignoreCase = true))
    }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add savings goal")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Savings goals", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                "Reserve part of your available cash for a goal. It stays in the same account and is not counted as new money.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search savings goals") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            FilterChip(
                selected = showArchived,
                onClick = { showArchived = !showArchived },
                label = { Text(if (showArchived) "Showing archived" else "Show archived") }
            )
            if (visibleGoals.isEmpty()) {
                Text(
                    if (showArchived) "No archived savings goals." else "Set a target, then reserve some of your available cash.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleGoals, key = { it.id }) { goal ->
                        SavingsGoalCard(
                            goal = goal,
                            onEdit = { editing = goal; showEditor = true },
                            onArchive = { viewModel.archive(goal.id, !goal.isArchived) },
                            onDelete = { deleteTarget = goal },
                            onAllocate = { eventTarget = goal; eventType = SavingsGoalEventType.ALLOCATE },
                            onRelease = { eventTarget = goal; eventType = SavingsGoalEventType.RELEASE }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        SavingsGoalEditorDialog(
            goal = editing,
            onDismiss = { showEditor = false },
            onSave = { name, target, date, notes ->
                if (editing == null) viewModel.create(name, target, date, notes)
                else viewModel.update(editing!!.id, name, target, date, notes)
                showEditor = false
            }
        )
    }
    eventTarget?.let { goal ->
        GoalAllocationDialog(
            goal = goal,
            type = eventType,
            onDismiss = { eventTarget = null },
            onSave = { amount, note ->
                if (eventType == SavingsGoalEventType.ALLOCATE) viewModel.allocate(goal.id, amount, note)
                else viewModel.release(goal.id, amount, note)
                eventTarget = null
            }
        )
    }
    deleteTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${goal.name}?") },
            text = { Text("Only an unused goal with no allocation history can be deleted. Existing goal history is kept by archiving it.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteUnused(goal.id); deleteTarget = null }) { Text("Delete if unused") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("Savings goal") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAllocate: () -> Unit,
    onRelease: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    goal.targetDate?.let { Text("Target ${formatGoalDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "${goal.name} actions") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Edit goal") }, onClick = { expanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(if (goal.isArchived) "Restore" else "Archive") }, onClick = { expanded = false; onArchive() })
                    DropdownMenuItem(text = { Text("Delete if unused") }, onClick = { expanded = false; onDelete() })
                }
            }
            if (goal.notes.isNotBlank()) Text(goal.notes, style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${goal.allocatedAmount.format()} reserved", fontWeight = FontWeight.SemiBold)
                Text("of ${goal.targetAmount.format()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllocate, enabled = !goal.isArchived, modifier = Modifier.weight(1f)) { Text("Reserve") }
                TextButton(onClick = onRelease, enabled = goal.allocatedAmount.isPositive, modifier = Modifier.weight(1f)) { Text("Release") }
            }
            if (goal.events.isNotEmpty()) {
                TextButton(onClick = { historyExpanded = !historyExpanded }) {
                    Text(if (historyExpanded) "Hide history" else "Allocation history (${goal.events.size})")
                }
                if (historyExpanded) {
                    goal.events.forEach { event ->
                        val label = if (event.type == SavingsGoalEventType.ALLOCATE) "Reserved" else "Released"
                        Text(
                            "$label ${event.amount.format()} · ${formatGoalDate(event.date)}${if (event.note.isBlank()) "" else " · ${event.note}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalEditorDialog(goal: SavingsGoal?, onDismiss: () -> Unit, onSave: (String, Money, Long?, String) -> Unit) {
    var name by remember(goal?.id) { mutableStateOf(goal?.name.orEmpty()) }
    var targetText by remember(goal?.id) { mutableStateOf(goal?.targetAmount?.let { "%.2f".format(Locale.US, it.centavos / 100.0) }.orEmpty()) }
    var dateText by remember(goal?.id) { mutableStateOf(goal?.targetDate?.let(::formatGoalDateInput).orEmpty()) }
    var notes by remember(goal?.id) { mutableStateOf(goal?.notes.orEmpty()) }
    var error by remember(goal?.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "Add savings goal" else "Edit savings goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(targetText, { targetText = it; error = null }, label = { Text("Target amount (PHP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dateText, { dateText = it; error = null }, label = { Text("Target date (YYYY-MM-DD, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = Money.parse(targetText)
                val date = if (dateText.isBlank()) null else runCatching {
                    LocalDate.parse(dateText.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
                when {
                    name.isBlank() -> error = "Enter a name for this goal."
                    target == null || !target.isPositive -> error = "Enter a target amount greater than zero."
                    dateText.isNotBlank() && date == null -> error = "Enter the target date as YYYY-MM-DD."
                    else -> onSave(name.trim(), target, date, notes.trim())
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoalAllocationDialog(goal: SavingsGoal, type: SavingsGoalEventType, onDismiss: () -> Unit, onSave: (Money, String) -> Unit) {
    var amountText by remember(goal.id, type) { mutableStateOf("") }
    var note by remember(goal.id, type) { mutableStateOf("") }
    var error by remember(goal.id, type) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == SavingsGoalEventType.ALLOCATE) "Reserve for ${goal.name}" else "Release from ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (type == SavingsGoalEventType.ALLOCATE) "This earmarks existing available cash; it does not move or create money." else "Released money becomes available to spend again.")
                if (type == SavingsGoalEventType.RELEASE) Text("Reserved balance: ${goal.allocatedAmount.format()}")
                OutlinedTextField(amountText, { amountText = it; error = null }, label = { Text("Amount (PHP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = Money.parse(amountText)
                if (amount == null || !amount.isPositive) error = "Enter an amount greater than zero."
                else onSave(amount, note.trim())
            }) { Text(if (type == SavingsGoalEventType.ALLOCATE) "Reserve" else "Release") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatGoalDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    )

private fun formatGoalDateInput(epochMillis: Long): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
