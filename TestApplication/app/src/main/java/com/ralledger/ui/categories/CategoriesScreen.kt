package com.ralledger.ui.categories
import com.ralledger.ui.settings.displayMoney
import com.ralledger.ui.components.DiscardChangesDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Transaction
import java.text.SimpleDateFormat
import com.ralledger.ui.settings.displayDate
import java.util.Date
import java.util.Locale

private val categoryColors = listOf("#00695C", "#1565C0", "#6A1B9A", "#EF6C00", "#C62828", "#455A64", "#2E7D32")
private val categoryIcons = listOf(
    "label", "shopping_cart", "restaurant", "home", "payments", "work", "directions_car", "favorite", "receipt_long", "school"
)

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var historyCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var mergingCategory by remember { mutableStateOf<Category?>(null) }
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val managedCategories = categories.filter { it.type == CategoryType.INCOME || it.type == CategoryType.EXPENSE }
    val visibleCategories = managedCategories.filter {
        (showArchived || !it.isArchived) && (search.isBlank() || it.name.contains(search, true))
    }
    val categoryNames = categories.associate { it.id to it.name }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Categories", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add category")
                }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search categories") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { }
                    .toggleable(
                        value = showArchived,
                        role = Role.Checkbox,
                        onValueChange = { showArchived = it }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = showArchived, onCheckedChange = null)
                Text("Show archived", style = MaterialTheme.typography.bodyMedium)
            }
            if (visibleCategories.isEmpty()) {
                Text(
                    if (managedCategories.isEmpty()) "Add income and expense categories for your records."
                    else "No categories match this view.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val income = visibleCategories.filter { it.type == CategoryType.INCOME }
                    val expense = visibleCategories.filter { it.type == CategoryType.EXPENSE }
                    if (income.isNotEmpty()) {
                        item { CategorySectionHeader("Income") }
                        items(income, key = { it.id }) { category ->
                            CategoryItemCard(
                                category = category,
                                parentName = category.parentCategoryId?.let(categoryNames::get),
                                childCount = categories.count { it.parentCategoryId == category.id },
                                onEdit = { editingCategory = category },
                                onHistory = { historyCategory = category },
                                onArchiveToggle = { viewModel.setArchived(category.id, !category.isArchived) },
                                onDelete = { deletingCategory = category },
                                onMerge = { mergingCategory = category }
                            )
                        }
                    }
                    if (expense.isNotEmpty()) {
                        item { CategorySectionHeader("Expenses") }
                        items(expense, key = { it.id }) { category ->
                            CategoryItemCard(
                                category = category,
                                parentName = category.parentCategoryId?.let(categoryNames::get),
                                childCount = categories.count { it.parentCategoryId == category.id },
                                onEdit = { editingCategory = category },
                                onHistory = { historyCategory = category },
                                onArchiveToggle = { viewModel.setArchived(category.id, !category.isArchived) },
                                onDelete = { deletingCategory = category },
                                onMerge = { mergingCategory = category }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryFormDialog(
            category = null,
            categories = managedCategories,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, parentId, icon, color ->
                viewModel.createCategory(name, type, parentId, icon, color)
                showAddDialog = false
            }
        )
    }
    editingCategory?.let { category ->
        CategoryFormDialog(
            category = category,
            categories = managedCategories,
            onDismiss = { editingCategory = null },
            onSave = { name, _, parentId, icon, color ->
                viewModel.updateCategory(category.id, name, parentId, icon, color)
                editingCategory = null
            }
        )
    }
    historyCategory?.let { category ->
        CategoryHistoryDialog(
            category = category,
            transactions = transactions.filter { it.categoryId == category.id }.sortedByDescending { it.timestamp },
            onDismiss = { historyCategory = null }
        )
    }
    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text("Permanent deletion is allowed only when this category has no transaction history or subcategories. Otherwise, archive it to preserve past records.")
            },
            confirmButton = {
                Button(onClick = { viewModel.deleteUnused(category.id); deletingCategory = null }) {
                    Text("Delete if unused")
                }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Cancel") } }
        )
    }
    mergingCategory?.let { category ->
        MergeCategoryDialog(
            source = category,
            targets = managedCategories.filter {
                it.id != category.id && it.type == category.type && !it.isArchived && it.parentCategoryId == null
            },
            onDismiss = { mergingCategory = null },
            onMerge = { targetId ->
                viewModel.merge(category.id, targetId)
                mergingCategory = null
            }
        )
    }
    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Category") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun CategorySectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CategoryItemCard(
    category: Category,
    parentName: String?,
    childCount: Int,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = categoryColor(category.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (category.isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(categoryIcon(category.iconName), null, tint = accent) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    if (category.isDefault) Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category.isArchived) Text("Archived", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val detail = when {
                    parentName != null -> "Subcategory · $parentName"
                    childCount > 0 -> "$childCount subcategories"
                    category.type == CategoryType.INCOME -> "Income source"
                    else -> "Expense"
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "${category.name} actions")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("View history") }, onClick = { expanded = false; onHistory() })
                    DropdownMenuItem(text = { Text("Edit category") }, onClick = { expanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Merge into…") }, onClick = { expanded = false; onMerge() })
                    DropdownMenuItem(
                        text = { Text(if (category.isArchived) "Restore" else "Archive") },
                        onClick = { expanded = false; onArchiveToggle() }
                    )
                    DropdownMenuItem(text = { Text("Delete if unused") }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeCategoryDialog(
    source: Category,
    targets: List<Category>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit
) {
    val initialTargetId = remember(source.id, targets) { targets.firstOrNull()?.id.orEmpty() }
    var targetId by remember(source.id, targets) { mutableStateOf(initialTargetId) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember(source.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isDirty = targetId != initialTargetId
    val requestDismiss = { if (isDirty) confirmDiscard = true else onDismiss() }
    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text("Merge ${source.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Past transactions will be reassigned to the destination category. Their amounts and account balances will not change.")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = targets.firstOrNull { it.id == targetId }?.name ?: "Choose a category",
                        onValueChange = {}, readOnly = true, label = { Text("Merge into") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        targets.forEach { target ->
                            DropdownMenuItem(text = { Text(target.name) }, onClick = {
                                targetId = target.id
                                expanded = false
                                error = null
                            })
                        }
                    }
                }
                if (targets.isEmpty()) Text("Create or restore another ${source.type.name.lowercase()} category first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (targetId.isBlank()) error = "Choose a destination category."
                else onMerge(targetId)
            }, enabled = targets.isNotEmpty()) { Text("Merge") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormDialog(
    category: Category?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, CategoryType, String?, String, String) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var type by remember(category?.id) { mutableStateOf(category?.type ?: CategoryType.EXPENSE) }
    var parentId by remember(category?.id) { mutableStateOf(category?.parentCategoryId) }
    var iconName by remember(category?.id) { mutableStateOf(category?.iconName ?: categoryIcons.first()) }
    var colorHex by remember(category?.id) { mutableStateOf(category?.colorHex ?: categoryColors.first()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var parentExpanded by remember { mutableStateOf(false) }
    var error by remember(category?.id) { mutableStateOf<String?>(null) }
    val originalName = remember(category?.id) { name }
    val originalType = remember(category?.id) { type }
    val originalParentId = remember(category?.id) { parentId }
    val originalIconName = remember(category?.id) { iconName }
    val originalColorHex = remember(category?.id) { colorHex }
    var confirmDiscard by remember(category?.id) { mutableStateOf(false) }
    val requestDismiss = {
        if (name != originalName || type != originalType || parentId != originalParentId ||
            iconName != originalIconName || colorHex != originalColorHex
        ) confirmDiscard = true else onDismiss()
    }
    val parentOptions = categories.filter {
        it.type == type && it.parentCategoryId == null && it.id != category?.id && !it.isArchived
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (category == null) "Add category" else "Edit category") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 470.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (category == null) {
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                        OutlinedTextField(
                            value = if (type == CategoryType.INCOME) "Income source" else "Expense",
                            onValueChange = {}, readOnly = true, label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            listOf(CategoryType.INCOME, CategoryType.EXPENSE).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (option == CategoryType.INCOME) "Income source" else "Expense") },
                                    onClick = { type = option; parentId = null; typeExpanded = false }
                                )
                            }
                        }
                    }
                } else {
                    Text(if (type == CategoryType.INCOME) "Income source" else "Expense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExposedDropdownMenuBox(expanded = parentExpanded, onExpandedChange = { parentExpanded = !parentExpanded }) {
                    OutlinedTextField(
                        value = parentOptions.firstOrNull { it.id == parentId }?.name ?: "No parent",
                        onValueChange = {}, readOnly = true, label = { Text("Subcategory of (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parentExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        DropdownMenuItem(text = { Text("No parent") }, onClick = { parentId = null; parentExpanded = false })
                        parentOptions.forEach { parent ->
                            DropdownMenuItem(text = { Text(parent.name) }, onClick = { parentId = parent.id; parentExpanded = false })
                        }
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    categoryIcons.forEach { icon ->
                        IconButton(
                            onClick = { iconName = icon },
                            modifier = Modifier.size(38.dp).border(
                                if (iconName == icon) 2.dp else 1.dp,
                                if (iconName == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                        ) { Icon(categoryIcon(icon), icon, tint = categoryColor(colorHex), modifier = Modifier.size(20.dp)) }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    categoryColors.forEach { color ->
                        Box(
                            modifier = Modifier.size(32.dp).background(categoryColor(color), CircleShape).border(
                                if (colorHex.equals(color, true)) 3.dp else 1.dp,
                                if (colorHex.equals(color, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(onClick = { colorHex = color }, color = Color.Transparent, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                                if (colorHex.equals(color, true)) Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, "Selected color", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = "Enter a category name."
                else onSave(name.trim(), type, parentId, iconName, colorHex)
            }) { Text(if (category == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Cancel") } }
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeepEditing = { confirmDiscard = false })
    }
}

@Composable
private fun CategoryHistoryDialog(category: Category, transactions: List<Transaction>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.name} history") },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions recorded for this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(transactions, key = { it.journalEntryId }) { transaction ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(transaction.description.ifBlank { transaction.transactionType.displayName }, modifier = Modifier.weight(1f))
                                Text(displayMoney(transaction.amount))
                            }
                            Text(
                                "${displayDate(transaction.timestamp, includeTime = true)} · ${transaction.transactionType.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun categoryColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
    .getOrDefault(Color(0xFF1565C0))

private fun categoryIcon(iconName: String): ImageVector = when (iconName) {
    "shopping_cart" -> Icons.Default.ShoppingCart
    "restaurant" -> Icons.Default.Restaurant
    "home" -> Icons.Default.Home
    "payments" -> Icons.Default.Payments
    "work" -> Icons.Default.Work
    "directions_car" -> Icons.Default.DirectionsCar
    "favorite" -> Icons.Default.Favorite
    "receipt_long" -> Icons.Default.ReceiptLong
    "school" -> Icons.Default.School
    else -> Icons.Default.Label
}
