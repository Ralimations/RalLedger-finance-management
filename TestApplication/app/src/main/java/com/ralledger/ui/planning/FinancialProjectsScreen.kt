package com.ralledger.ui.planning

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.model.Money
import com.ralledger.domain.usecase.InsightsResult
import com.ralledger.ui.settings.displayMoney

@Composable
fun FinancialProjectsScreen(viewModel: FinancialProjectsViewModel) {
    val projects by viewModel.projects.collectAsState()
    val results by viewModel.results.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var search by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<FinancialProjectKind?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<FinancialProject?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FinancialProject?>(null) }
    var mergeSource by remember { mutableStateOf<FinancialProject?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val visibleProjects = projects.filter { project ->
        project.isArchived == showArchived &&
            (selectedKind == null || project.kind == selectedKind) &&
            (search.isBlank() || project.name.contains(search, ignoreCase = true) || project.description.contains(search, ignoreCase = true))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorTarget = null; editorOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add financial project")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Projects", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Group transactions into personal or business projects. Totals follow posted journal entries and reversals.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search projects") },
                    singleLine = true
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedKind == null, { selectedKind = null }, label = { Text("All types") })
                    FinancialProjectKind.entries.forEach { kind ->
                        FilterChip(selectedKind == kind, { selectedKind = kind }, label = { Text(kind.displayName) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showArchived, { showArchived = false }, label = { Text("Active") })
                    FilterChip(showArchived, { showArchived = true }, label = { Text("Archived") })
                    Spacer(Modifier.weight(1f))
                    Text("${visibleProjects.size} projects", style = MaterialTheme.typography.labelMedium)
                }
            }
            item {
                val personal = projectTypeTotals(FinancialProjectKind.PERSONAL, projects, results)
                val business = projectTypeTotals(FinancialProjectKind.BUSINESS, projects, results)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectKindSummary(FinancialProjectKind.PERSONAL, personal, Modifier.weight(1f))
                    ProjectKindSummary(FinancialProjectKind.BUSINESS, business, Modifier.weight(1f))
                }
            }
            if (visibleProjects.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (search.isNotBlank()) "No matching projects"
                                else if (showArchived) "No archived projects" else "No projects yet",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Add a project, then assign it to transactions from the transaction actions menu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(visibleProjects, key = { it.id }) { project ->
                    FinancialProjectCard(
                        project = project,
                        result = results[project.id],
                        onEdit = { editorTarget = project; editorOpen = true },
                        onArchive = { viewModel.archive(project, !project.isArchived) },
                        onMerge = { mergeSource = project },
                        onDelete = { deleteTarget = project }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        FinancialProjectEditorDialog(
            project = editorTarget,
            onDismiss = { editorOpen = false },
            onSave = { name, kind, description, color ->
                val current = editorTarget
                if (current == null) viewModel.create(name, kind, description, color)
                else viewModel.update(current, name, kind, description, color)
                editorOpen = false
            }
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Only a project with no assigned transactions or saved reports can be deleted. Used projects must be archived or merged to preserve their history.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(project); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    mergeSource?.let { source ->
        FinancialProjectMergeDialog(
            source = source,
            targets = projects.filter { it.id != source.id && !it.isArchived },
            onDismiss = { mergeSource = null },
            onMerge = { targetId -> viewModel.merge(source, targetId); mergeSource = null }
        )
    }
}

@Composable
private fun FinancialProjectCard(
    project: FinancialProject,
    result: InsightsResult?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(project.id) { mutableStateOf(false) }
    val fallbackColor = MaterialTheme.colorScheme.primary
    val color = remember(project.colorHex, fallbackColor) {
        runCatching { Color(AndroidColor.parseColor(project.colorHex)) }.getOrDefault(fallbackColor)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.padding(end = 10.dp).size(18.dp).background(color, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(project.kind.displayName + if (project.isArchived) " · Archived" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxMenu(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    archived = project.isArchived,
                    onEdit = onEdit,
                    onArchive = onArchive,
                    onMerge = onMerge,
                    onDelete = onDelete
                )
            }
            if (project.description.isNotBlank()) Text(project.description, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ProjectMetric("Income", displayMoney(result?.income ?: com.ralledger.domain.model.Money.ZERO), MaterialTheme.colorScheme.primary)
                ProjectMetric("Expenses", displayMoney(result?.expenses ?: com.ralledger.domain.model.Money.ZERO), MaterialTheme.colorScheme.error)
                ProjectMetric("Net", displayMoney(result?.net ?: com.ralledger.domain.model.Money.ZERO), MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ProjectMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun projectTypeTotals(
    kind: FinancialProjectKind,
    projects: List<FinancialProject>,
    results: Map<String, InsightsResult>
): InsightsResult? {
    val matching = projects.filter { it.kind == kind }
    if (matching.isEmpty()) return null
    val totals = matching.mapNotNull { results[it.id] }
    return totals.firstOrNull()?.copy(
        income = totals.fold(Money.ZERO) { sum, result -> sum + result.income },
        expenses = totals.fold(Money.ZERO) { sum, result -> sum + result.expenses },
        net = totals.fold(Money.ZERO) { sum, result -> sum + result.net }
    )
}

@Composable
private fun ProjectKindSummary(kind: FinancialProjectKind, result: InsightsResult?, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${kind.displayName} · all time", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("Income ${displayMoney(result?.income ?: Money.ZERO)}", style = MaterialTheme.typography.bodySmall)
            Text("Expenses ${displayMoney(result?.expenses ?: Money.ZERO)}", style = MaterialTheme.typography.bodySmall)
            Text("Net ${displayMoney(result?.net ?: Money.ZERO)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BoxMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    archived: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    IconButton(onClick = { onExpandedChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Project actions") }
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { onExpandedChange(false); onEdit() })
        DropdownMenuItem(text = { Text(if (archived) "Restore" else "Archive") }, onClick = { onExpandedChange(false); onArchive() })
        DropdownMenuItem(text = { Text("Merge into…") }, onClick = { onExpandedChange(false); onMerge() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onExpandedChange(false); onDelete() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinancialProjectEditorDialog(
    project: FinancialProject?,
    onDismiss: () -> Unit,
    onSave: (String, FinancialProjectKind, String, String) -> Unit
) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var kind by remember(project?.id) { mutableStateOf(project?.kind ?: FinancialProjectKind.PERSONAL) }
    var description by remember(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var color by remember(project?.id) { mutableStateOf(project?.colorHex ?: "#008577") }
    var kindExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "Add project" else "Edit project") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column {
                    Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { kindExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(kind.displayName, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) {
                        FinancialProjectKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.displayName) }, onClick = { kind = option; kindExpanded = false })
                        }
                    }
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                OutlinedTextField(color, { color = it }, label = { Text("Color (hex)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, kind, description, color) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FinancialProjectMergeDialog(
    source: FinancialProject,
    targets: List<FinancialProject>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit
) {
    var targetId by remember(source.id) { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${source.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction assignments and saved report filters move to the destination. The original journal entries remain unchanged.")
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(targets.firstOrNull { it.id == targetId }?.name ?: "Choose a destination", modifier = Modifier.weight(1f))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    targets.forEach { target -> DropdownMenuItem(text = { Text(target.name) }, onClick = { targetId = target.id; expanded = false }) }
                }
                if (targets.isEmpty()) Text("Create or restore another active project before merging.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { targetId?.let(onMerge) }, enabled = targetId != null) { Text("Merge") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
