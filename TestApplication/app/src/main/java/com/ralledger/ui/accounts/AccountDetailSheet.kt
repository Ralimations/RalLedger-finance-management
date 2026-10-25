package com.ralledger.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import com.ralledger.ui.settings.displayDate
import com.ralledger.ui.settings.displayMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDetailSheet(
    account: Account,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onReceive: () -> Unit,
    onTransfer: () -> Unit,
    onViewHistory: () -> Unit,
    onReconcile: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val canMoveMoney = account.accountType.isAsset && !account.accountType.isReceivable && !account.isArchived
    val accent = accountColor(account.colorHex)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Account details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(account.accountType.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close account details") }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(color = accent.copy(alpha = 0.16f), shape = CircleShape) {
                        Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                            Icon(accountIcon(account.iconName), contentDescription = null, tint = accent)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (account.institution.isNotBlank()) Text(account.institution, style = MaterialTheme.typography.bodySmall)
                        if (account.isArchived) Text("Archived", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ledger balance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(displayMoney(account.currentLedgerBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                account.lastReconciledAt?.let { Text("Last reconciled ${displayDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (account.includeInDashboardTotals) {
                    Text(
                        "Available Money is calculated across liquid accounts on Home. Savings-goal reserves are not assigned to an individual account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("This account is excluded from dashboard totals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (account.description.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(account.description, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onViewHistory) { Text("See all") }
            }
            if (transactions.isEmpty()) {
                Text("No transactions recorded for this account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                transactions.take(5).forEach { transaction ->
                    val amount = if (transaction.transactionType == TransactionType.INTEREST_CREDIT) {
                        transaction.amount - transaction.fee
                    } else transaction.amount
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(transaction.description.ifBlank { transaction.transactionType.displayName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(
                                "${transaction.transactionType.displayName} · ${displayDate(transaction.timestamp, includeTime = true)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Text(
                            "${accountTransactionDirection(transaction, account.id)}${displayMoney(amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (accountTransactionDirection(transaction, account.id)) {
                                "+" -> MaterialTheme.colorScheme.secondary
                                "−" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Account actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (canMoveMoney) {
                    TextButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("Transfer from this account") }
                    TextButton(onClick = onReceive, modifier = Modifier.fillMaxWidth()) { Text("Receive into this account") }
                }
                if (!account.isArchived && (account.accountType.isAsset || account.accountType.isLiability)) {
                    TextButton(onClick = onReconcile, modifier = Modifier.fillMaxWidth()) { Text("Reconcile balance") }
                }
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit account") }
                TextButton(onClick = onArchiveToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (account.isArchived) "Restore account" else "Archive account")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete if unused") }
            }
        }
    }
}
