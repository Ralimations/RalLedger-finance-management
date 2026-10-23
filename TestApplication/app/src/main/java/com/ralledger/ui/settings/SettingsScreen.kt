package com.ralledger.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.ui.theme.ThemeMode
import com.ralledger.ui.theme.ThemePalette
import com.ralledger.ui.theme.ThemeSettings
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    appearance: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onManageCategories: () -> Unit
) {
    val context = LocalContext.current
    val backupStatus by viewModel.backupStatus.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    var password by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val saveBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.createEncryptedBackup(password) { bytes ->
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("The selected destination could not be opened")
                output.use { it.write(bytes) }
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected backup could not be opened")
                val bytes = input.use { it.readBytes() }
                viewModel.inspectBackup(bytes, password)
                bytes.fill(0)
            } catch (error: Exception) {
                validationError = error.localizedMessage ?: "Could not read the selected backup"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Data Ownership",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Know your money. Own your data. Stay in control.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Make it yours", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Choose the look that feels right. Your preference stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Appearance", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appearance.mode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text("Color palette", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePalette.entries.forEach { palette ->
                        FilterChip(
                            selected = appearance.palette == palette,
                            onClick = { onThemePaletteChange(palette) },
                            label = { Text(palette.label) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Manage income sources, expense categories, icons and colors.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onManageCategories) { Text("Manage categories") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Encrypted Backup & Restore", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "The backup contains your current accounts, categories, budgets, savings goals and allocation history, journal, receivables, payment history, bills and loans, subscriptions, and reconciliations. It is encrypted with a password you choose. Keep that password separately; it cannot be recovered.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; validationError = null },
                    label = { Text("Backup password (12+ characters)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = {
                        if (password.length < 12) {
                            validationError = "Use at least 12 characters before creating or opening a backup."
                        } else {
                            saveBackup.launch("RalLedger_Backup_${System.currentTimeMillis()}.rlbackup")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save Encrypted Backup") }

                TextButton(
                    onClick = {
                        if (password.length < 12) {
                            validationError = "Enter the backup password first."
                        } else {
                            openBackup.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore from Backup") }

                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Version: 2.0 development", style = MaterialTheme.typography.bodySmall)
                Text("Architecture: Native Android + Room + Double-Entry Accounting Engine", style = MaterialTheme.typography.bodySmall)
                Text("Mode: Offline-first", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text("Review Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Created ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(preview.createdAt))}")
                    Text("${preview.accounts} accounts · ${preview.categories} categories")
                    Text("${preview.budgets} budgets")
                    Text("${preview.expectedIncome} expected-income records")
                    Text("${preview.debts} bills and loans · ${preview.debtPayments} debt payments")
                    Text("${preview.savingsGoals} savings goals · ${preview.savingsGoalEvents} allocations")
                    Text("${preview.journalEntries} journal entries · ${preview.journalLines} journal lines")
                    Text("${preview.receivables} receivables · ${preview.payments} payments")
                    Text("${preview.writeOffs} receivable write-offs")
                    Text("${preview.subscriptions} subscriptions · ${preview.reconciliations} reconciliations")
                    Text(
                        "Restoring replaces all current RalLedger records on this device.",
                        color = MaterialTheme.colorScheme.error
                    )
                    preview.validationErrors.forEach { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRestore() }, enabled = preview.canRestore) {
                    Text("Replace Data and Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("Cancel") }
            }
        )
    }
}
