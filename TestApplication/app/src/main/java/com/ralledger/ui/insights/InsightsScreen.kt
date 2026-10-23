package com.ralledger.ui.insights

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private data class CategorySlice(
    val id: String,
    val name: String,
    val amount: Money,
    val color: Color
)

private val fallbackChartColors = listOf(
    Color(0xFF00796B), Color(0xFF5376D1), Color(0xFFDB8B29), Color(0xFFC45570),
    Color(0xFF719746), Color(0xFF8B63B6), Color(0xFF398EA0), Color(0xFFAD633E)
)

@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    val reversedEntries = remember(state.transactions) { state.transactions.mapNotNull { it.reversesEntryId }.toSet() }
    val monthTransactions = remember(state.transactions, selectedMonth, reversedEntries) {
        state.transactions.filter { transaction ->
            transaction.journalEntryId !in reversedEntries &&
                transaction.transactionType in setOf(TransactionType.INCOME, TransactionType.INTEREST_CREDIT, TransactionType.EXPENSE, TransactionType.SUBSCRIPTION_PAYMENT, TransactionType.RECEIVABLE_WRITEOFF) &&
                transaction.belongsTo(selectedMonth)
        }
    }
    val monthIncome = monthTransactions.filter { it.transactionType in setOf(TransactionType.INCOME, TransactionType.INTEREST_CREDIT) }
        .fold(Money.ZERO) { sum, transaction -> sum + transaction.amount }
    val monthExpenses = monthTransactions.fold(Money.ZERO) { sum, transaction ->
        sum + when (transaction.transactionType) {
            TransactionType.EXPENSE, TransactionType.SUBSCRIPTION_PAYMENT, TransactionType.RECEIVABLE_WRITEOFF -> transaction.amount
            TransactionType.INTEREST_CREDIT -> transaction.fee
            else -> Money.ZERO
        }
    }
    val monthInterestNet = monthTransactions.filter { it.transactionType == TransactionType.INTEREST_CREDIT }
        .fold(Money.ZERO) { sum, transaction -> sum + (transaction.amount - transaction.fee) }
    val monthNet = monthIncome - monthExpenses

    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }
    val slices = remember(monthTransactions, categoryById) {
        val grouped = monthTransactions.flatMap { transaction ->
            when (transaction.transactionType) {
                TransactionType.EXPENSE, TransactionType.SUBSCRIPTION_PAYMENT, TransactionType.RECEIVABLE_WRITEOFF -> listOf(
                    (transaction.categoryId ?: "uncategorized") to transaction.amount
                )
                TransactionType.INTEREST_CREDIT -> if (transaction.fee.isPositive) listOf(
                    (transaction.secondaryCategoryId ?: "interest-tax") to transaction.fee
                ) else emptyList()
                else -> emptyList()
            }
        }.groupBy({ it.first }, { it.second })
        grouped.entries.mapIndexed { index, (id, amounts) ->
            val category = categoryById[id]
            val amount = amounts.fold(Money.ZERO, Money::plus)
            CategorySlice(
                id = id,
                name = category?.name ?: if (id == "interest-tax") "Interest tax withheld" else "Uncategorized",
                amount = amount,
                color = category?.colorHex.toComposeColor() ?: fallbackChartColors[index % fallbackChartColors.size]
            )
        }.sortedByDescending { it.amount.centavos }
    }
    val selectedSlice = slices.firstOrNull { it.id == selectedCategoryId } ?: slices.firstOrNull()
    val maxCategoryAmount = slices.maxOfOrNull { it.amount.centavos }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Your insights", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
                    }
                    Text(selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
                    }
                }
            }
        }

        item { MonthSummaryCard(monthIncome, monthExpenses, monthNet) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interest credited · net", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(monthInterestNet.format(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Spending by category", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Tap a ring segment or category to explore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (slices.isEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("No expenses recorded this month yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                    } else {
                        Spacer(Modifier.height(14.dp))
                        SpendingDonut(
                            slices = slices,
                            selectedId = selectedSlice?.id,
                            selectedAmount = selectedSlice?.amount ?: Money.ZERO,
                            total = monthExpenses,
                            onSelect = { selectedCategoryId = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        slices.forEach { slice ->
                            CategoryBreakdownRow(
                                slice = slice,
                                total = monthExpenses,
                                maximum = maxCategoryAmount,
                                selected = slice.id == selectedSlice?.id,
                                onClick = { selectedCategoryId = slice.id }
                            )
                        }
                    }
                }
            }
        }

        item {
            OverviewCard(
                title = "Accounts",
                subtitle = "Current balance by account",
                values = state.accountDistribution
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Commitments", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subscriptions · monthly", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(state.activeSubscriptionTotal.format(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    state.totalReceivablesAging.toList().sortedBy { it.first }.forEach { (status, amount) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Receivables · ${status.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(amount.format(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

private fun Transaction.belongsTo(month: YearMonth): Boolean = runCatching {
    YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()) == month
}.getOrDefault(false)

private fun String?.toComposeColor(): Color? = runCatching {
    this?.let { Color(AndroidColor.parseColor(it)) }
}.getOrNull()

@Composable
private fun MonthSummaryCard(income: Money, expenses: Money, net: Money) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monthly cash flow", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryAmount("Income", income, MaterialTheme.colorScheme.primary)
                SummaryAmount("Expenses", expenses, MaterialTheme.colorScheme.error)
                SummaryAmount("Net", net, if (net.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: Money, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        Text(amount.format(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = tint)
    }
}

@Composable
private fun SpendingDonut(
    slices: List<CategorySlice>,
    selectedId: String?,
    selectedAmount: Money,
    total: Money,
    onSelect: (String) -> Unit
) {
    val totalCentavos = total.centavos.coerceAtLeast(1L)
    Box(
        modifier = Modifier.size(220.dp).pointerInput(slices, selectedId) {
            detectTapGestures { tap ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = tap.x - center.x
                val dy = tap.y - center.y
                val distance = hypot(dx, dy)
                val outer = min(size.width, size.height) * 0.49f
                val inner = min(size.width, size.height) * 0.25f
                if (distance in inner..outer) {
                    val degrees = ((atan2(dy, dx) * 180f / Math.PI.toFloat()) + 450f) % 360f
                    var cursor = 0f
                    for (slice in slices) {
                        val sweep = 360f * slice.amount.centavos / totalCentavos
                        if (degrees >= cursor && degrees < cursor + sweep) {
                            onSelect(slice.id)
                            break
                        }
                        cursor += sweep
                    }
                }
            }
        }.semantics { contentDescription = "Interactive spending chart. Select a segment to view its category." },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 22.dp.toPx()
            var start = -90f
            val strokeWidth = 30.dp.toPx()
            slices.forEach { slice ->
                val sweep = 360f * slice.amount.centavos / totalCentavos
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = if (slice.id == selectedId) strokeWidth + 7.dp.toPx() else strokeWidth)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(slices.firstOrNull { it.id == selectedId }?.name ?: "Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(selectedAmount.format(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    slice: CategorySlice,
    total: Money,
    maximum: Long,
    selected: Boolean,
    onClick: () -> Unit
) {
    val share = if (total.centavos > 0L) (slice.amount.centavos * 100.0 / total.centavos).toInt() else 0
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(slice.color, CircleShape))
            Text(slice.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(slice.amount.format(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text("$share%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(Modifier.fillMaxWidth((slice.amount.centavos.toFloat() / maximum).coerceIn(0f, 1f)).height(6.dp).background(slice.color, CircleShape))
        }
    }
}

@Composable
private fun OverviewCard(title: String, subtitle: String, values: List<Pair<String, Money>>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (values.isEmpty()) {
                Text("No accounts yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                values.forEach { (name, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(amount.format(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}
