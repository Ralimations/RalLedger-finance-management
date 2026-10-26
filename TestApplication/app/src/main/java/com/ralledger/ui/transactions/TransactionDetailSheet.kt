package com.ralledger.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType.*
import com.ralledger.ui.settings.displayDate
import com.ralledger.ui.settings.displayMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailSheet(
    transaction: Transaction,
    journal: List<Transaction>,
    onDismiss: () -> Unit,
    onCorrect: () -> Unit,
    onDuplicate: () -> Unit,
    onReverse: () -> Unit,
    onManageTags: () -> Unit,
    onAssignProject: () -> Unit
) {
    val tone = when (transaction.transactionType) {
        INCOME, REFUND, INTEREST_CREDIT, RECEIVABLE_COLLECTION, LOAN_DISBURSEMENT -> MaterialTheme.colorScheme.secondary
        EXPENSE, SUBSCRIPTION_PAYMENT, RECEIVABLE_WRITEOFF, PAYABLE_PAYMENT, LOAN_REPAYMENT -> MaterialTheme.colorScheme.error
        TRANSFER, REVERSAL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (transaction.transactionType) {
        INCOME, REFUND, INTEREST_CREDIT, RECEIVABLE_COLLECTION, LOAN_DISBURSEMENT -> "+"
        EXPENSE, SUBSCRIPTION_PAYMENT, RECEIVABLE_WRITEOFF, PAYABLE_PAYMENT, LOAN_REPAYMENT -> "−"
        else -> ""
    }
    val netAmount = if (transaction.transactionType == INTEREST_CREDIT) {
        transaction.amount - transaction.fee
    } else transaction.amount
    val canCorrect = transaction.transactionType in setOf(INCOME, EXPENSE, TRANSFER) && !transaction.isReversed
    val canDuplicate = transaction.transactionType in setOf(INCOME, EXPENSE, TRANSFER, REFUND) && !transaction.isReversed
    val canReverse = transaction.transactionType != REVERSAL && !transaction.isReversed
    val relatedEntries = relatedJournalEntries(transaction, journal)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Transaction details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(transaction.transactionType.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close transaction details")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        transaction.description.ifBlank { transaction.transactionType.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$prefix${displayMoney(netAmount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone
                    )
                    Text(displayDate(transaction.timestamp, includeTime = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (transaction.transactionType == INTEREST_CREDIT) {
                        Text(
                            "Gross ${displayMoney(transaction.amount)} · tax withheld ${displayMoney(transaction.fee)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (transaction.transactionType == TRANSFER) {
                    DetailLine("From account", transaction.accountName)
                    transaction.destinationAccountName?.let { DetailLine("To account", it) }
                } else {
                    DetailLine("Account", transaction.accountName)
                }
                transaction.categoryName?.let { DetailLine("Category", it) }
                transaction.secondaryCategoryName?.let { DetailLine("Secondary category", it) }
                if (transaction.fee.isPositive && transaction.transactionType != INTEREST_CREDIT) {
                    DetailLine("Fee", displayMoney(transaction.fee))
                }
                transaction.counterparty.takeIf(String::isNotBlank)?.let { DetailLine("Counterparty", it) }
                transaction.projectName?.let { DetailLine("Project", it) }
                if (transaction.tagNames.isNotEmpty()) DetailLine("Labels", transaction.tagNames.joinToString(" · "))
                transaction.notes.takeIf(String::isNotBlank)?.let { DetailLine("Notes", it) }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History and audit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                DetailLine("Status", transactionAuditStatus(transaction))
                DetailLine("Journal entry", transaction.journalEntryId)
                transaction.reversesEntryId?.let { DetailLine("Reverses entry", it) }
                if (relatedEntries.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("Related journal entries", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    relatedEntries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.transactionType.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${displayDate(entry.timestamp, includeTime = true)} · ${entry.accountName} · ${displayMoney(entry.amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (canCorrect) TextButton(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text("Edit / Correct") }
                if (canDuplicate) TextButton(onClick = onDuplicate, modifier = Modifier.fillMaxWidth()) { Text("Duplicate") }
                if (canReverse) TextButton(onClick = onReverse, modifier = Modifier.fillMaxWidth()) { Text("Reverse / Void") }
                TextButton(onClick = onManageTags, modifier = Modifier.fillMaxWidth()) { Text("Manage labels") }
                if (transaction.reversesEntryId == null) {
                    TextButton(onClick = onAssignProject, modifier = Modifier.fillMaxWidth()) {
                        Text(if (transaction.projectId == null) "Assign project" else "Change project")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
    }
}
