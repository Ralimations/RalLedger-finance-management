package com.ralledger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ralledger.ui.planning.PlanningQuickAction

private data class QuickActionOption(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val action: PlanningQuickAction? = null,
    val transactionType: String? = null
)

private data class QuickActionGroup(
    val title: String,
    val options: List<QuickActionOption>
)

private data class PrimaryQuickActionOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tone: Color,
    val transactionType: String
)

private val quickActionGroups = listOf(
    QuickActionGroup(
        title = "Receive & collect",
        options = listOf(
            QuickActionOption("New receivable", "Record money someone owes you", Icons.Default.ReceiptLong, PlanningQuickAction.ADD_RECEIVABLE),
            QuickActionOption("Collect a payment", "Choose a receivable to update", Icons.Default.Payments, PlanningQuickAction.OPEN_RECEIVABLES),
            QuickActionOption("Expected income", "Plan income that is not confirmed yet", Icons.Default.ArrowDownward, PlanningQuickAction.ADD_EXPECTED_INCOME)
        )
    ),
    QuickActionGroup(
        title = "Plan & commit",
        options = listOf(
            QuickActionOption("Subscription", "Set up a recurring commitment", Icons.Default.EventRepeat, PlanningQuickAction.ADD_SUBSCRIPTION),
            QuickActionOption("Budget", "Set a monthly category limit", Icons.Default.AccountBalanceWallet, PlanningQuickAction.ADD_BUDGET),
            QuickActionOption("Savings goal", "Set a target and reserve cash", Icons.Default.Savings, PlanningQuickAction.ADD_SAVINGS_GOAL)
        )
    ),
    QuickActionGroup(
        title = "Other financial actions",
        options = listOf(
            QuickActionOption("Refund", "Record money returned to an expense", Icons.Default.Payments, transactionType = "REFUND"),
            QuickActionOption("Add a bill", "Track an expense you owe", Icons.Default.ArrowUpward, PlanningQuickAction.ADD_BILL),
            QuickActionOption("Add a loan", "Track borrowed money and repayments", Icons.Default.SwapHoriz, PlanningQuickAction.ADD_LOAN),
            QuickActionOption("Interest & deposits", "Review profiles and credited interest", Icons.Default.Savings, PlanningQuickAction.OPEN_INTEREST_PRODUCTS),
            QuickActionOption("Recurring transactions", "Review and confirm repeating income and expenses", Icons.Default.EventRepeat, PlanningQuickAction.OPEN_RECURRING_TRANSACTIONS)
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionHubSheet(
    onDismiss: () -> Unit,
    onTransactionSelected: (String) -> Unit,
    onPlanningActionSelected: (PlanningQuickAction) -> Unit
) {
    var showMoreActions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale
    val useTwoColumnActionGrid = screenWidthDp < 380 || (fontScale >= 1.2f && screenWidthDp < 460)
    val primaryActions = listOf(
        PrimaryQuickActionOption(
            title = "Income",
            description = "Money in",
            icon = Icons.Default.ArrowDownward,
            tone = MaterialTheme.colorScheme.tertiaryContainer,
            transactionType = "INCOME"
        ),
        PrimaryQuickActionOption(
            title = "Expense",
            description = "Money out",
            icon = Icons.Default.ArrowUpward,
            tone = MaterialTheme.colorScheme.errorContainer,
            transactionType = "EXPENSE"
        ),
        PrimaryQuickActionOption(
            title = "Transfer",
            description = "Between accounts",
            icon = Icons.Default.SwapHoriz,
            tone = MaterialTheme.colorScheme.secondaryContainer,
            transactionType = "TRANSFER"
        )
    )
    val primaryActionRows = if (useTwoColumnActionGrid) primaryActions.chunked(2) else listOf(primaryActions)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showMoreActions) "More actions" else "Add to PesoMind",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (showMoreActions) "Choose the financial task you want to do."
                        else "Choose an everyday transaction.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showMoreActions) {
                    TextButton(onClick = { showMoreActions = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Text("Back")
                    }
                }
            }

            if (showMoreActions) {
                quickActionGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        group.options.forEach { option ->
                            MoreActionRow(option) {
                                option.transactionType?.let(onTransactionSelected)
                                option.action?.let(onPlanningActionSelected)
                            }
                        }
                    }
                }
            } else {
                primaryActionRows.forEach { actionRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        actionRow.forEach { action ->
                            PrimaryQuickAction(
                                modifier = Modifier.weight(1f),
                                title = action.title,
                                description = action.description,
                                icon = action.icon,
                                tone = action.tone
                            ) { onTransactionSelected(action.transactionType) }
                        }
                        if (actionRow.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { showMoreActions = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("More", style = MaterialTheme.typography.titleSmall)
                            Text("Receivables, bills, budgets, goals, and more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun PrimaryQuickAction(
    modifier: Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    tone: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = tone)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoreActionRow(option: QuickActionOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                option.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(9.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleSmall)
                Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
