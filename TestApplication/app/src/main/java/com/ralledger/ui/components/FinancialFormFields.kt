package com.ralledger.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category

/** Shared read-only selector for the accounts eligible for a financial action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialAccountSelector(
    accounts: List<Account>,
    selectedId: String,
    onSelect: (String) -> Unit,
    label: String = "Account",
    emptyLabel: String = "Select an account",
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    var expanded by remember(accounts) { mutableStateOf(false) }
    val canSelect = enabled && accounts.isNotEmpty()
    val selectedName = accounts.firstOrNull { it.id == selectedId }?.name ?: emptyLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (canSelect) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = canSelect,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = canSelect).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { onSelect(account.id); expanded = false },
                    enabled = enabled
                )
            }
        }
    }
}

/** Shared read-only selector for categories already filtered for the current action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialCategorySelector(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    label: String = "Category",
    emptyLabel: String = "Select a category",
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    var expanded by remember(categories) { mutableStateOf(false) }
    val canSelect = enabled && categories.isNotEmpty()
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: emptyLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (canSelect) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = canSelect,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = canSelect).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelect(category.id); expanded = false },
                    enabled = enabled
                )
            }
        }
    }
}

/** Shared compact date action; callers own picker state and timezone conversion. */
@Composable
fun FinancialDateSelector(
    value: String,
    onClick: () -> Unit,
    label: String = "Date",
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text("$label: $value")
    }
}

@Composable
fun FinancialDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Description",
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier
    )
}

@Composable
fun FinancialNotesField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Notes",
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        minLines = 2,
        maxLines = 4,
        modifier = modifier
    )
}
