package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.RecurringTransaction
import com.ralledger.domain.model.RecurringTransactionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun RecurringTransactionsScreen(
    viewModel: RecurringTransactionsViewModel,
    onRecordNext: (RecurringTransaction) -> Unit
) {
    val schedules by viewModel.schedules.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()
    var cancelCandidate by remember { mutableStateOf<RecurringTransaction?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Recurring transactions", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(
            "Upcoming income and expenses stay as reminders until you choose to record them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (schedules.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No recurring transactions", style = MaterialTheme.typography.titleMedium)
                    Text("Choose Repeat when adding an income or expense to set one up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        schedules.forEach { schedule ->
            val account = accounts.firstOrNull { it.id == schedule.accountId }
            val category = categories.firstOrNull { it.id == schedule.categoryId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(schedule.description.ifBlank { schedule.counterparty.ifBlank { category?.name ?: "Transaction" } }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${schedule.transactionType.displayName} · ${schedule.frequency.name.lowercase().replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            schedule.amount.format(account?.currency ?: "PHP"),
                            color = if (schedule.transactionType == com.ralledger.domain.model.TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Text(
                        "${category?.name ?: "Category unavailable"} · ${account?.name ?: "Account unavailable"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${schedule.status.name.lowercase().replace('_', ' ')} · next ${formatScheduleDate(schedule.nextOccurrenceTimestamp, schedule.timeZoneId)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (schedule.status == RecurringTransactionStatus.ACTIVE) {
                            Button(onClick = { onRecordNext(schedule) }) { Text("Record next") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { viewModel.setStatus(schedule.id, RecurringTransactionStatus.PAUSED) }
                                        .onFailure { actionError = it.localizedMessage ?: "Could not pause this schedule." }
                                }
                            }) { Text("Pause") }
                            TextButton(onClick = { cancelCandidate = schedule }) { Text("Cancel") }
                        } else if (schedule.status == RecurringTransactionStatus.PAUSED) {
                            Button(onClick = {
                                scope.launch {
                                    runCatching { viewModel.setStatus(schedule.id, RecurringTransactionStatus.ACTIVE) }
                                        .onFailure { actionError = it.localizedMessage ?: "Could not resume this schedule." }
                                }
                            }) { Text("Resume") }
                            TextButton(onClick = { cancelCandidate = schedule }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(1.dp))
    }

    cancelCandidate?.let { schedule ->
        AlertDialog(
            onDismissRequest = { cancelCandidate = null },
            title = { Text("Cancel this schedule?") },
            text = { Text("Past transactions stay in Activity. Future occurrences will no longer be offered.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelCandidate = null
                    scope.launch {
                        runCatching { viewModel.setStatus(schedule.id, RecurringTransactionStatus.CANCELLED) }
                            .onFailure { actionError = it.localizedMessage ?: "Could not cancel this schedule." }
                    }
                }) { Text("Cancel schedule") }
            },
            dismissButton = { TextButton(onClick = { cancelCandidate = null }) { Text("Keep it") } }
        )
    }
}

private fun formatScheduleDate(timestamp: Long, timeZoneId: String): String {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    return Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
}
