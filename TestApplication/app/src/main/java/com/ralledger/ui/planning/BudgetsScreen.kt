package com.ralledger.ui.planning
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Budget
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.ui.components.MoneyCalculatorAction
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var includeArchived by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var editorMonth by remember { mutableStateOf(YearMonth.now()) }
    var pendingDelete by remember { mutableStateOf<Budget?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val monthBudgets = budgets.filter { it.year == month.year && it.month == month.monthValue }
    val shownBudgets = monthBudgets.filter { includeArchived || !it.isArchived }
    val activeBudgets = monthBudgets.filterNot { it.isArchived }
    val totalLimit = activeBudgets.fold(0L) { total, budget -> Math.addExact(total, budget.limit.centavos) }
    val totalSpent = activeBudgets.fold(0L) { total, budget -> Math.addExact(total, budget.spent.centavos) }
    val activeCategories = categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingBudget = null
                editorMonth = month
                formError = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add budget")
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Previous month")
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next month")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BudgetSummary("Budgeted", Money(totalLimit))
                    BudgetSummary("Spent", Money(totalSpent), emphasize = totalSpent > totalLimit && totalLimit > 0L)
                    BudgetSummary("Remaining", Money(totalLimit - totalSpent), emphasize = totalSpent > totalLimit && totalLimit > 0L)
                }
            }

            FilterChip(
                selected = includeArchived,
                onClick = { includeArchived = !includeArchived },
                label = { Text(if (includeArchived) "Showing archived" else "Show archived") }
            )

            if (shownBudgets.isEmpty()) {
                Text(
                    text = "No budgets for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shownBudgets, key = { it.id }) { budget ->
                        BudgetCard(
                            budget = budget,
                            onEdit = {
                                editingBudget = budget
                                editorMonth = budget.period
                                formError = null
                                showEditor = true
                            },
                            onArchive = {
                                scope.launch {
                                    runCatching { viewModel.setArchived(budget.id, !budget.isArchived) }
                                        .onSuccess { message = if (budget.isArchived) "Budget restored." else "Budget archived." }
                                        .onFailure { message = it.localizedMessage ?: "The budget could not be updated." }
                                }
                            },
                            onDelete = { pendingDelete = budget }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        BudgetEditorDialog(
            month = editorMonth,
            onMonthChange = { editorMonth = it },
            budget = editingBudget,
            categories = activeCategories,
            allCategories = categories,
            error = formError,
            onDismiss = { showEditor = false },
            onSave = { categoryId, limit ->
                scope.launch {
                    val selected = editorMonth
                    runCatching {
                        val existing = editingBudget
                        if (existing == null) {
                            viewModel.createBudget(categoryId, selected.year, selected.monthValue, limit)
                        } else {
                            viewModel.updateBudget(existing.id, categoryId, selected.year, selected.monthValue, limit)
                        }
                    }.onSuccess {
                        formError = null
                        month = selected
                        showEditor = false
                        message = if (editingBudget == null) "Budget saved." else "Budget updated."
                    }.onFailure {
                        formError = it.localizedMessage ?: "The budget could not be saved."
                    }
                }
            }
        )
    }

    pendingDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${budget.categoryName} budget?") },
            text = { Text("This removes the ${budget.period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))} budget. Transactions and their history stay unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { viewModel.deleteBudget(budget.id) }
                            .onSuccess { message = "Budget deleted." }
                            .onFailure { message = it.localizedMessage ?: "The budget could not be deleted." }
                        pendingDelete = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Budget update") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun BudgetSummary(label: String, amount: Money, emphasize: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            displayMoney(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun BudgetCard(budget: Budget, onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val overBudget = budget.isOverBudget
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (budget.isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(budget.categoryName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (budget.isArchived) "Archived budget" else "${budget.period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))} · ${"%.0f".format(Locale.getDefault(), budget.progressFraction * 100)}% used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "${budget.categoryName} budget actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(if (budget.isArchived) "Restore" else "Archive") },
                            onClick = { menuExpanded = false; onArchive() }
                        )
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Spent ${displayMoney(budget.spent)}")
                Text("Limit ${displayMoney(budget.limit)}")
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(budget.progressFraction).height(8.dp)
                        .clip(RoundedCornerShape(50)).background(if (overBudget) MaterialTheme.colorScheme.error else primary)
                )
            }
            Text(
                text = if (overBudget) "Over budget by ${displayMoney(budget.overspent)}" else "${displayMoney(budget.remaining)} remaining",
                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BudgetEditorDialog(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    budget: Budget?,
    categories: List<Category>,
    allCategories: List<Category>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (categoryId: String, limit: Money) -> Unit
) {
    var categoryId by remember(budget?.id) { mutableStateOf(budget?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var amountText by remember(budget?.id) {
        mutableStateOf(budget?.limit?.centavos?.let(::moneyInput).orEmpty())
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var localError by remember(budget?.id) { mutableStateOf<String?>(null) }
    val selectedCategory = allCategories.firstOrNull { it.id == categoryId }
    val options = (categories + listOfNotNull(selectedCategory?.takeIf { it.isArchived })).distinctBy { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "Add budget" else "Edit budget") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Previous budget month")
                    }
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next budget month")
                    }
                }
                Box {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Choose an expense category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth().clickable { categoryExpanded = true },
                        trailingIcon = { Text("▾") }
                    )
                    DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        options.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name + if (category.isArchived) " · Archived" else "") },
                                onClick = { categoryId = category.id; categoryExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly limit (PHP)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { MoneyCalculatorAction(amountText) { amountText = it } }
                )
                (error ?: localError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limit = Money.parse(amountText)
                when {
                    categoryId.isBlank() -> localError = "Choose an expense category."
                    limit == null || !limit.isPositive -> localError = "Enter a valid limit greater than zero."
                    else -> { localError = null; onSave(categoryId, limit) }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun moneyInput(centavos: Long): String = "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
