package com.ralledger.ui.reconciliation
import com.ralledger.ui.settings.displayMoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Money
import com.ralledger.ui.components.MoneyAmountField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    viewModel: ReconciliationViewModel
) {
    val accounts by viewModel.accounts.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val reconcilableAccounts = accounts.filter {
        !it.isArchived && (it.accountType.isAsset || it.accountType.isLiability)
    }

    var selectedAccountId by remember { mutableStateOf("") }
    var actualBalanceText by remember { mutableStateOf("") }
    var adjustmentReason by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reconcilableAccounts, selectedAccountId) {
        if (selectedAccountId.isBlank() && reconcilableAccounts.isNotEmpty()) {
            selectedAccountId = reconcilableAccounts.first().id
        }
    }

    val selectedAccount = accounts.find { it.id == selectedAccountId }
    val recordedBalance = selectedAccount?.currentLedgerBalance ?: Money.ZERO
    val actualBalance = Money.parse(actualBalanceText)
    val difference = actualBalance?.minus(recordedBalance)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Account Reconciliation",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Audit recorded ledger balances against bank statement balances",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Select Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Account to Reconcile") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            reconcilableAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${displayMoney(acc.currentLedgerBalance)})") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Recorded Balance", style = MaterialTheme.typography.labelSmall)
                            Text(displayMoney(recordedBalance), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("Discrepancy / Difference", style = MaterialTheme.typography.labelSmall)
                            Text(
                                difference?.let { displayMoney(it) } ?: "—",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (difference?.isZero == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    MoneyAmountField(
                        value = actualBalanceText,
                        onValueChange = { actualBalanceText = it; inputError = null },
                        label = "Actual Statement / Wallet Balance"
                    )

                    OutlinedTextField(
                        value = adjustmentReason,
                        onValueChange = { adjustmentReason = it },
                        label = { Text("Reason for Adjustment (if different)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            when {
                                selectedAccountId.isBlank() -> inputError = "Select an account to reconcile."
                                actualBalance == null -> inputError = "Enter a valid actual balance."
                                else -> {
                                    viewModel.reconcileAccount(selectedAccountId, actualBalance, adjustmentReason)
                                    actualBalanceText = ""
                                    adjustmentReason = ""
                                }
                            }
                        }
                    ) {
                        Text("Audit & Post Reconciliation")
                    }

                }
            }
        }
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationMessage,
            title = { Text("Reconciliation") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearOperationMessage) { Text("OK") } }
        )
    }
}
