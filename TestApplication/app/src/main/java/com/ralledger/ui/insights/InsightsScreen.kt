package com.ralledger.ui.insights
import com.ralledger.ui.settings.displayMoney

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Money
import com.ralledger.domain.usecase.InsightsCalculator
import com.ralledger.domain.usecase.NetWorthHistoryCalculator
import com.ralledger.domain.usecase.ReceivablesAgingCalculator
import com.ralledger.domain.usecase.InsightsBucket
import com.ralledger.domain.usecase.InsightsDateRange
import com.ralledger.domain.usecase.InsightsPeriod
import com.ralledger.domain.usecase.InsightsPeriodType
import com.ralledger.domain.usecase.FinancialProjectPortfolioCalculator
import com.ralledger.domain.usecase.InsightsResult
import com.ralledger.ui.settings.displayDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    defaultPeriodType: InsightsPeriodType = InsightsPeriodType.MONTH
) {
    val state by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedType by remember(defaultPeriodType) {
        mutableStateOf(defaultPeriodType.takeIf { it != InsightsPeriodType.CUSTOM } ?: InsightsPeriodType.MONTH)
    }
    var anchorDate by remember { mutableStateOf(today) }
    var customStart by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var customEnd by remember { mutableStateOf(today) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var pickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }
    val selectedPeriod = remember(selectedType, anchorDate, customStart, customEnd) {
        InsightsPeriod(
            type = selectedType,
            anchorDate = anchorDate,
            customRange = if (selectedType == InsightsPeriodType.CUSTOM) InsightsDateRange(customStart, customEnd) else null
        )
    }
    val calculation = remember(state.transactions, state.categories, selectedPeriod) {
        InsightsCalculator.calculate(state.transactions, state.categories, selectedPeriod)
    }
    val projectPortfolio = remember(state.transactions, state.categories, state.projects, selectedPeriod) {
        FinancialProjectPortfolioCalculator.calculate(
            transactions = state.transactions,
            categories = state.categories,
            projects = state.projects,
            period = selectedPeriod
        )
    }
    val receivablesAging = remember(state.receivables, today) {
        ReceivablesAgingCalculator.calculate(state.receivables, today)
    }
    val totalOutstandingReceivables = remember(receivablesAging) {
        receivablesAging.fold(Money.ZERO) { total, bucket -> total + bucket.amount }
    }
    val balanceHistory = remember(state.journalEntries, state.accounts, state.receivables, state.debts, calculation.buckets) {
        NetWorthHistoryCalculator.calculate(
            journalEntries = state.journalEntries,
            accounts = state.accounts,
            receivableAccountIds = state.receivables.mapTo(mutableSetOf()) { it.id },
            debtAccountIds = state.debts.mapTo(mutableSetOf()) { it.id },
            buckets = calculation.buckets
        )
    }
    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }
    val slices = remember(calculation.categoryTotals, categoryById) {
        calculation.categoryTotals.filter { it.amount.isPositive }.mapIndexed { index, total ->
            val category = categoryById[total.id]
            CategorySlice(
                id = total.id,
                name = category?.name ?: total.name,
                amount = total.amount,
                color = category?.colorHex.toComposeColor() ?: fallbackChartColors[index % fallbackChartColors.size]
            )
        }
    }
    val selectedSlice = slices.firstOrNull { it.id == selectedCategoryId } ?: slices.firstOrNull()
    val maxCategoryAmount = slices.maxOfOrNull { it.amount.centavos }?.coerceAtLeast(1L) ?: 1L
    val categoryChartTotal = slices.fold(Money.ZERO) { total, slice -> total + slice.amount }
    val zone = remember { ZoneId.systemDefault() }
    val periodLabel = selectedPeriod.label(zone = zone, transactions = state.transactions)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Insights", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightsPeriodType.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (type == InsightsPeriodType.CUSTOM && customEnd.isBefore(customStart)) customEnd = customStart
                        },
                        label = { Text(type.label) }
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { selectedPeriod.move(-1).also { anchorDate = it.anchorDate; customStart = it.customRange?.start ?: customStart; customEnd = it.customRange?.endInclusive ?: customEnd } }, enabled = selectedType != InsightsPeriodType.ALL_TIME) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous period")
                        }
                        Text(periodLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        IconButton(onClick = { selectedPeriod.move(1).also { anchorDate = it.anchorDate; customStart = it.customRange?.start ?: customStart; customEnd = it.customRange?.endInclusive ?: customEnd } }, enabled = selectedType != InsightsPeriodType.ALL_TIME) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next period")
                        }
                    }
                    if (selectedType == InsightsPeriodType.CUSTOM) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { pickerTarget = DatePickerTarget.START }) { Text("From · ${displayDate(customStart)}") }
                            TextButton(onClick = { pickerTarget = DatePickerTarget.END }) { Text("To · ${displayDate(customEnd)}") }
                        }
                    }
                }
            }
        }

        item { PeriodSummaryCard(calculation.income, calculation.expenses, calculation.net) }

        item {
            PersonalBusinessSummaryCard(
                personal = projectPortfolio.personal,
                business = projectPortfolio.business,
                unassigned = projectPortfolio.unassigned
            )
        }

        item {
            CashFlowChart(calculation.buckets)
        }

        item { BalanceHistoryChart(balanceHistory) }

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
                    Text("Interest earnings", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Gross · ${displayMoney(calculation.grossInterest)}", style = MaterialTheme.typography.bodySmall)
                        Text("Tax withheld · ${displayMoney(calculation.interestTax)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text("Net · ${displayMoney(calculation.netInterest)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    }
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
                    }
                    if (slices.isEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("No category spending in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                    } else {
                        Spacer(Modifier.height(14.dp))
                        SpendingDonut(
                            slices = slices,
                            selectedId = selectedSlice?.id,
                            selectedAmount = selectedSlice?.amount ?: Money.ZERO,
                            total = categoryChartTotal,
                            onSelect = { selectedCategoryId = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        slices.forEach { slice ->
                            CategoryBreakdownRow(
                                slice = slice,
                                total = categoryChartTotal,
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
                title = "Income sources",
                subtitle = periodLabel,
                values = calculation.incomeSources.filter { !it.amount.isZero }.take(8).map { it.name to it.amount },
                emptyMessage = "No income entries this period."
            )
        }

        item {
            OverviewCard(
                title = "Asset accounts",
                subtitle = "Current balance by account",
                values = state.accountDistribution,
                emptyMessage = "No asset accounts yet."
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
                        Text(displayMoney(state.activeSubscriptionTotal), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subscriptions paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(displayMoney(calculation.subscriptionPayments), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Collected this period · net of reversals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(displayMoney(calculation.receivablesCollected), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(displayMoney(totalOutstandingReceivables), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    receivablesAging.filter { it.amount.isPositive }.forEach { bucket ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${bucket.band.label} · ${bucket.receivableCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(displayMoney(bucket.amount), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val currentDate = if (target == DatePickerTarget.START) customStart else customEnd
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedMillis ->
                        val selectedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == DatePickerTarget.START) {
                            customStart = selectedDate
                            if (customEnd.isBefore(selectedDate)) customEnd = selectedDate
                        } else {
                            customEnd = selectedDate
                            if (customStart.isAfter(selectedDate)) customStart = selectedDate
                        }
                        anchorDate = customStart
                        selectedType = InsightsPeriodType.CUSTOM
                    }
                    pickerTarget = null
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { pickerTarget = null }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

private enum class DatePickerTarget { START, END }

private fun String?.toComposeColor(): Color? = runCatching {
    this?.let { Color(AndroidColor.parseColor(it)) }
}.getOrNull()

@Composable
private fun CashFlowChart(buckets: List<InsightsBucket>) {
    var selectedStart by remember(buckets) { mutableStateOf(buckets.firstOrNull()?.start) }
    val selectedBucket = buckets.firstOrNull { it.start == selectedStart }
    val maximum = buckets.maxOfOrNull {
        maxOf(kotlin.math.abs(it.income.centavos.toDouble()), kotlin.math.abs(it.expenses.centavos.toDouble()))
    }?.coerceAtLeast(1.0) ?: 1.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Income vs expenses", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ChartLegend("Income", MaterialTheme.colorScheme.primary)
                ChartLegend("Expenses", MaterialTheme.colorScheme.error)
            }
            if (buckets.isEmpty()) {
                Text("No records in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    buckets.forEach { bucket ->
                        val bucketDescription = "${bucket.label}: income ${displayMoney(bucket.income)}, expenses ${displayMoney(bucket.expenses)}"
                        Column(
                            modifier = Modifier.width(46.dp)
                                .clickable(role = Role.Button) { selectedStart = bucket.start }
                                .semantics {
                                    contentDescription = bucketDescription
                                }
                                .background(
                                    if (bucket.start == selectedStart) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent,
                                    RoundedCornerShape(9.dp)
                                )
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                CashFlowBar(bucket.income, maximum, MaterialTheme.colorScheme.primary)
                                CashFlowBar(bucket.expenses, maximum, MaterialTheme.colorScheme.error)
                            }
                            Text(bucket.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                selectedBucket?.let { bucket ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(bucket.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(displayMoney(bucket.income), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Text(displayMoney(bucket.expenses), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(tint, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalanceHistoryChart(points: List<com.ralledger.domain.usecase.NetWorthSnapshot>) {
    val latest = points.lastOrNull()
    val chartWidth = (points.size * 54).coerceAtLeast(280).dp
    val netWorthColor = MaterialTheme.colorScheme.primary
    val liquidAssetsColor = MaterialTheme.colorScheme.tertiary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Net worth & liquid assets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ChartLegend("Net worth", netWorthColor)
                ChartLegend("Liquid assets", liquidAssetsColor)
            }
            if (points.isEmpty()) {
                Text("No balance history in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(chartWidth).height(150.dp)) {
                        val horizontalInset = 8.dp.toPx()
                        val verticalInset = 12.dp.toPx()
                        val allValues = points.flatMap { listOf(it.netWorth.centavos.toDouble(), it.liquidAssets.centavos.toDouble()) }
                        val rawMin = allValues.minOrNull() ?: 0.0
                        val rawMax = allValues.maxOrNull() ?: 0.0
                        val span = (rawMax - rawMin).takeIf { it > 0.0 } ?: 1.0
                        val minValue = rawMin - span * 0.08
                        val maxValue = rawMax + span * 0.08
                        val range = maxValue - minValue
                        val chartHeight = size.height - verticalInset * 2
                        val chartSpan = size.width - horizontalInset * 2

                        fun point(index: Int, value: Long): Offset {
                            val x = if (points.size == 1) size.width / 2f
                            else horizontalInset + chartSpan * index / (points.size - 1)
                            val y = size.height - verticalInset - ((value.toDouble() - minValue) / range * chartHeight).toFloat()
                            return Offset(x, y)
                        }

                        val netPath = Path()
                        val liquidPath = Path()
                        points.forEachIndexed { index, snapshot ->
                            val netPoint = point(index, snapshot.netWorth.centavos)
                            val liquidPoint = point(index, snapshot.liquidAssets.centavos)
                            if (index == 0) {
                                netPath.moveTo(netPoint.x, netPoint.y)
                                liquidPath.moveTo(liquidPoint.x, liquidPoint.y)
                            } else {
                                netPath.lineTo(netPoint.x, netPoint.y)
                                liquidPath.lineTo(liquidPoint.x, liquidPoint.y)
                            }
                            drawCircle(netWorthColor, radius = 3.dp.toPx(), center = netPoint)
                            drawCircle(liquidAssetsColor, radius = 3.dp.toPx(), center = liquidPoint)
                        }
                        drawPath(netPath, netWorthColor, style = Stroke(width = 2.5.dp.toPx()))
                        drawPath(liquidPath, liquidAssetsColor, style = Stroke(width = 2.5.dp.toPx()))
                    }
                    Row {
                        points.forEach { snapshot ->
                            Text(
                                snapshot.label,
                                modifier = Modifier.width((chartWidth.value / points.size).coerceAtLeast(44f).dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                latest?.let { snapshot ->
                    Text(
                        "Period end · ${displayDate(snapshot.date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Net worth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(displayMoney(snapshot.netWorth), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = netWorthColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Liquid assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(displayMoney(snapshot.liquidAssets), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = liquidAssetsColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashFlowBar(value: Money, maximum: Double, tint: Color) {
    val magnitude = (kotlin.math.abs(value.centavos.toDouble()) / maximum).toFloat().coerceIn(0f, 1f)
    val color = if (value.isNegative) tint.copy(alpha = 0.42f) else tint
    Box(
        modifier = Modifier.width(16.dp).height(90.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(magnitude)
                .background(color, RoundedCornerShape(5.dp))
        )
    }
}

@Composable
private fun PeriodSummaryCard(income: Money, expenses: Money, net: Money) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Period summary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryAmount("Income", income, MaterialTheme.colorScheme.primary)
                SummaryAmount("Expenses", expenses, MaterialTheme.colorScheme.error)
                SummaryAmount("Net", net, if (net.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun PersonalBusinessSummaryCard(
    personal: InsightsResult,
    business: InsightsResult,
    unassigned: InsightsResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Personal and Business", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "Based on project assignments in this period. Unassigned activity is listed separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProjectScopeSummaryRow("Personal", personal)
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ProjectScopeSummaryRow("Business", business)
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ProjectScopeSummaryRow("Unassigned", unassigned)
            Text(
                "Assign transactions from their menu to include them in a Personal or Business total.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProjectScopeSummaryRow(label: String, result: InsightsResult) {
    var breakdownExpanded by remember(label) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "Net ${displayMoney(result.net)}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (result.net.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayMoney(result.income), style = MaterialTheme.typography.labelSmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayMoney(result.expenses), style = MaterialTheme.typography.labelSmall)
        }
        if (result.incomeSources.isNotEmpty() || result.categoryTotals.isNotEmpty()) {
            TextButton(onClick = { breakdownExpanded = !breakdownExpanded }) {
                Text(if (breakdownExpanded) "Hide income and spending details" else "Income sources and spending categories")
            }
            if (breakdownExpanded) {
                if (result.incomeSources.isNotEmpty()) {
                    AmountBreakdown("Income by source", result.incomeSources.map { it.name to it.amount })
                }
                if (result.categoryTotals.isNotEmpty()) {
                    AmountBreakdown("Spending by category", result.categoryTotals.map { it.name to it.amount })
                }
            }
        }
    }
}

@Composable
private fun AmountBreakdown(title: String, values: List<Pair<String, Money>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { (name, amount) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(displayMoney(amount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: Money, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        Text(displayMoney(amount), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = tint)
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
            Text(displayMoney(selectedAmount), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
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
            Text(displayMoney(slice.amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text("$share%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(Modifier.fillMaxWidth((slice.amount.centavos.toFloat() / maximum).coerceIn(0f, 1f)).height(6.dp).background(slice.color, CircleShape))
        }
    }
}

@Composable
private fun OverviewCard(title: String, subtitle: String, values: List<Pair<String, Money>>, emptyMessage: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (values.isEmpty()) {
                Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                values.forEach { (name, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(displayMoney(amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}
