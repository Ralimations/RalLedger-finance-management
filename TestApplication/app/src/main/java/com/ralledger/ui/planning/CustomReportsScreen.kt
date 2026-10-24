package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CustomReport
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.TransactionTag
import com.ralledger.domain.usecase.InsightsResult
import com.ralledger.ui.settings.displayDate
import com.ralledger.ui.settings.displayMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private data class ReportFilterOption(val id: String?, val label: String)
private enum class ReportDateField { START, END }

@Composable
fun CustomReportsScreen(viewModel: CustomReportsViewModel) {
    val reports by viewModel.reports.collectAsState()
    val results by viewModel.results.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<CustomReport?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CustomReport?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val visibleReports = reports.filter { report ->
        report.isArchived == showArchived && (query.isBlank() || report.name.contains(query, ignoreCase = true))
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorTarget = null; editorOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Save custom report")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Custom Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Save a date range with optional account, category, label, and project filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search reports") },
                    singleLine = true
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                    FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                    Spacer(Modifier.weight(1f))
                    Text("${visibleReports.size} reports", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (visibleReports.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (query.isBlank()) if (showArchived) "No archived reports" else "No saved reports" else "No matching reports", fontWeight = FontWeight.SemiBold)
                            Text("Use + to save a report for a date range and optional filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(visibleReports, key = { it.id }) { report ->
                    CustomReportCard(
                        report = report,
                        result = results[report.id],
                        accountName = accounts.firstOrNull { it.id == report.accountId }?.name,
                        categoryName = categories.firstOrNull { it.id == report.categoryId }?.name,
                        tagName = tags.firstOrNull { it.id == report.tagId }?.name,
                        projectName = projects.firstOrNull { it.id == report.projectId }?.name,
                        onEdit = { editorTarget = report; editorOpen = true },
                        onArchive = { viewModel.archive(report, !report.isArchived) },
                        onDelete = { deleteTarget = report }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        CustomReportEditorDialog(
            report = editorTarget,
            accounts = accounts,
            categories = categories,
            tags = tags,
            projects = projects,
            onDismiss = { editorOpen = false },
            onSave = { name, startDate, endDate, accountId, categoryId, tagId, projectId ->
                val target = editorTarget
                if (target == null) viewModel.create(name, startDate, endDate, accountId, categoryId, tagId, projectId)
                else viewModel.update(target, name, startDate, endDate, accountId, categoryId, tagId, projectId)
                editorOpen = false
            }
        )
    }

    deleteTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete saved report?") },
            text = { Text("${report.name} will be deleted. This does not change transaction history.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(report); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CustomReportCard(
    report: CustomReport,
    result: InsightsResult?,
    accountName: String?,
    categoryName: String?,
    tagName: String?,
    projectName: String?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(report.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(report.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${displayDate(report.startDate)} – ${displayDate(report.endDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxMenu(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    archived = report.isArchived,
                    onEdit = onEdit,
                    onArchive = onArchive,
                    onDelete = onDelete
                )
            }
            val filters = listOfNotNull(
                accountName?.let { "Account: $it" },
                categoryName?.let { "Category: $it" },
                tagName?.let { "Label: $it" },
                projectName?.let { "Project: $it" }
            )
            if (filters.isNotEmpty()) Text(filters.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (result != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReportMetric("Income", displayMoney(result.income), MaterialTheme.colorScheme.primary)
                    ReportMetric("Expenses", displayMoney(result.expenses), MaterialTheme.colorScheme.error)
                    ReportMetric("Net", displayMoney(result.net), MaterialTheme.colorScheme.onSurface)
                }
                if (result.categoryTotals.isNotEmpty()) TopCategories(result.categoryTotals)
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopCategories(totals: List<com.ralledger.domain.usecase.InsightsNamedAmount>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Top categories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (item in totals.take(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, style = MaterialTheme.typography.bodySmall)
                Text(displayMoney(item.amount), style = MaterialTheme.typography.bodySmall)
            }
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
    onDelete: () -> Unit
) {
    IconButton(onClick = { onExpandedChange(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Report actions") }
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { onExpandedChange(false); onEdit() })
        DropdownMenuItem(text = { Text(if (archived) "Restore" else "Archive") }, onClick = { onExpandedChange(false); onArchive() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { onExpandedChange(false); onDelete() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomReportEditorDialog(
    report: CustomReport?,
    accounts: List<Account>,
    categories: List<Category>,
    tags: List<TransactionTag>,
    projects: List<FinancialProject>,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String?, String?, String?, String?) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var name by remember(report?.id) { mutableStateOf(report?.name.orEmpty()) }
    var startDate by remember(report?.id) { mutableStateOf(report?.startDate ?: today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()) }
    var endDate by remember(report?.id) { mutableStateOf(report?.endDate ?: today.atStartOfDay(zone).toInstant().toEpochMilli()) }
    var accountId by remember(report?.id) { mutableStateOf(report?.accountId) }
    var categoryId by remember(report?.id) { mutableStateOf(report?.categoryId) }
    var tagId by remember(report?.id) { mutableStateOf(report?.tagId) }
    var projectId by remember(report?.id) { mutableStateOf(report?.projectId) }
    var dateField by remember { mutableStateOf<ReportDateField?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report == null) "Save custom report" else "Edit custom report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Report name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { dateField = ReportDateField.START }, modifier = Modifier.weight(1f)) { Text("From\n${displayDate(startDate)}") }
                    OutlinedButton(onClick = { dateField = ReportDateField.END }, modifier = Modifier.weight(1f)) { Text("To\n${displayDate(endDate)}") }
                }
                ReportFilterDropdown("Account", accountId, listOf(ReportFilterOption(null, "All accounts")) + accounts.filter { !it.isArchived || it.id == accountId }.map { ReportFilterOption(it.id, it.name) }) { accountId = it }
                ReportFilterDropdown("Category", categoryId, listOf(ReportFilterOption(null, "All categories")) + categories.filter { !it.isArchived || it.id == categoryId }.map { ReportFilterOption(it.id, it.name) }) { categoryId = it }
                ReportFilterDropdown("Label", tagId, listOf(ReportFilterOption(null, "All labels")) + tags.filter { !it.isArchived || it.id == tagId }.map { ReportFilterOption(it.id, it.name) }) { tagId = it }
                ReportFilterDropdown("Project", projectId, listOf(ReportFilterOption(null, "All projects")) + projects.filter { !it.isArchived || it.id == projectId }.map { ReportFilterOption(it.id, it.name) }) { projectId = it }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, startDate, endDate, accountId, categoryId, tagId, projectId) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    dateField?.let { field ->
        val initial = if (field == ReportDateField.START) startDate else endDate
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = storedDateToPicker(initial, zone))
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selected ->
                        val selectedEpoch = pickerDateToStored(selected, zone)
                        if (field == ReportDateField.START) startDate = selectedEpoch else endDate = selectedEpoch
                    }
                    dateField = null
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { dateField = null }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ReportFilterDropdown(
    label: String,
    selectedId: String?,
    options: List<ReportFilterOption>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: "Choose $label"
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option.id); expanded = false })
            }
        }
    }
}

private fun storedDateToPicker(timestamp: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerDateToStored(timestamp: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
