package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.Money
import com.ralledger.domain.usecase.InterestCalculator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Composable
fun InterestProductsScreen(viewModel: InterestProductsViewModel) {
    val products by viewModel.products.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<InterestProductType?>(null) }
    var editing by remember { mutableStateOf<InterestProduct?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var creditTarget by remember { mutableStateOf<InterestProduct?>(null) }
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<InterestProduct?>(null) }
    var archiveTarget by remember { mutableStateOf<InterestProduct?>(null) }
    var reverseTarget by remember { mutableStateOf<Pair<InterestProduct, com.ralledger.domain.model.InterestCredit>?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val visible = products.filter { product ->
        product.isArchived == showArchived &&
            (typeFilter == null || product.type == typeFilter) &&
            (search.isBlank() || product.accountName.contains(search, true) || product.notes.contains(search, true))
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Interest & deposits", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Track terms, maturity and posted interest.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { editing = null; showEditor = true }) { Text("Add") }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search accounts") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                    FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All types") })
                    InterestProductType.entries.forEach { type ->
                        FilterChip(selected = typeFilter == type, onClick = { typeFilter = if (typeFilter == type) null else type }, label = { Text(if (type == InterestProductType.SAVINGS_ACCOUNT) "Savings" else "Deposits") })
                    }
                }
            }

            if (visible.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (search.isBlank()) "No interest profiles yet" else "No matching profiles", style = MaterialTheme.typography.titleMedium)
                    Text("Add an interest-bearing account or a time deposit to estimate interest and record actual credits.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (products.isEmpty()) Text("Create a Time Deposit account in Accounts first, with its principal as the opening balance.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { product ->
                        InterestProductCard(
                            product = product,
                            showHistory = historyTarget == product.id,
                            onToggleHistory = { historyTarget = if (historyTarget == product.id) null else product.id },
                            onEdit = { editing = product; showEditor = true },
                            onCredit = { creditTarget = product },
                            onArchive = { archiveTarget = product },
                            onDelete = { deleteTarget = product },
                            onReverse = { credit -> reverseTarget = product to credit }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        InterestProductEditor(
            product = editing,
            accounts = accounts,
            onDismiss = { showEditor = false; editing = null },
            onSave = { accountId, type, rate, tax, compound, opened, maturity, principal, notes ->
                val product = editing
                if (product == null) viewModel.create(accountId, type, rate, tax, compound, opened, maturity, principal, notes)
                else viewModel.update(product, rate, tax, compound, opened, maturity, principal, notes)
                showEditor = false
                editing = null
            }
        )
    }
    creditTarget?.let { product ->
        InterestCreditEditor(
            product = product,
            incomeCategories = categories.filter { it.type == CategoryType.INCOME && !it.isArchived },
            expenseCategories = categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived },
            onDismiss = { creditTarget = null },
            onSave = { gross, tax, incomeCategoryId, taxCategoryId, date, notes ->
                viewModel.credit(product.id, gross, tax, incomeCategoryId, taxCategoryId, date, notes)
                creditTarget = null
            }
        )
    }
    deleteTarget?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${product.accountName} interest profile?") },
            text = { Text("This removes the unused profile. Any profile with posted credit history must be archived so its audit trail remains available.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteUnused(product.id); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    archiveTarget?.let { product ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text(if (product.isArchived) "Restore interest profile?" else "Archive interest profile?") },
            text = { Text(if (product.isArchived) "The profile will return to the active list." else "The profile and its posted interest history will remain saved and can be restored later.") },
            confirmButton = { TextButton(onClick = { viewModel.archive(product.id, !product.isArchived); archiveTarget = null }) { Text(if (product.isArchived) "Restore" else "Archive") } },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Cancel") } }
        )
    }
    reverseTarget?.let { (product, credit) ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("Reverse this interest credit?") },
            text = { Text("This reverses the complete gross interest and tax posting for ${product.accountName}. The original record remains in history.") },
            confirmButton = { TextButton(onClick = { viewModel.reverse(credit.journalEntryId); reverseTarget = null }) { Text("Reverse") } },
            dismissButton = { TextButton(onClick = { reverseTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InterestProductCard(
    product: InterestProduct,
    showHistory: Boolean,
    onToggleHistory: () -> Unit,
    onEdit: () -> Unit,
    onCredit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onReverse: (com.ralledger.domain.model.InterestCredit) -> Unit,
) {
    var menuExpanded by remember(product.id) { mutableStateOf(false) }
    val today = System.currentTimeMillis()
    val estimate = remember(
        product.id, product.annualRateBasisPoints, product.taxRateBasisPoints, product.compounding,
        product.accountBalance, product.openedAt, product.maturityDate, product.principal
    ) {
        runCatching {
            if (product.type == InterestProductType.TIME_DEPOSIT) {
                InterestCalculator.estimate(product.principal ?: Money.ZERO, product.annualRateBasisPoints, product.taxRateBasisPoints,
                    product.compounding, product.openedAt, product.maturityDate ?: product.openedAt)
            } else {
                val nextYear = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, 365) }.timeInMillis
                InterestCalculator.estimate(product.accountBalance, product.annualRateBasisPoints, product.taxRateBasisPoints,
                    product.compounding, today, nextYear)
            }
        }.getOrNull()
    }
    val activeCredits = product.credits.filter { it.reversedAt == null }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.accountName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("${product.type.label} · ${formatPercent(product.annualRateBasisPoints)}% p.a. · ${product.compounding.label} compounding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Interest profile actions") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit terms") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(if (product.isArchived) "Restore" else "Archive") }, onClick = { menuExpanded = false; onArchive() })
                    DropdownMenuItem(text = { Text("Delete unused profile") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
            Text("Account balance · ${product.accountBalance.format()}", style = MaterialTheme.typography.bodyMedium)
            if (product.type == InterestProductType.TIME_DEPOSIT) {
                val maturity = product.maturityDate?.let(::formatInterestDate) ?: "Not set"
                val daysLeft = product.maturityDate?.let { ((it - today) / 86_400_000L).toInt() }
                Text("Maturity · $maturity${daysLeft?.let { if (it >= 0) " · $it days left" else " · matured" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Original principal · ${product.principal?.format() ?: "—"}", style = MaterialTheme.typography.bodySmall)
            }
            if (estimate != null) {
                Text(
                    if (product.type == InterestProductType.TIME_DEPOSIT) "Full-term estimate · gross ${estimate.gross.format()} · net ${estimate.net.format()}"
                    else "Next-year estimate · gross ${estimate.gross.format()} · net ${estimate.net.format()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else Text("Estimate exceeds the supported amount range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (product.notes.isNotBlank()) Text(product.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCredit, enabled = !product.isArchived) { Text("Record interest") }
                TextButton(onClick = onToggleHistory) { Text(if (showHistory) "Hide history" else "History (${product.credits.size})") }
            }
            if (showHistory) {
                if (product.credits.isEmpty()) Text("No interest credits recorded.", style = MaterialTheme.typography.bodySmall)
                product.credits.forEach { credit ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatInterestDate(credit.creditedAt)} · gross ${credit.gross.format()}", style = MaterialTheme.typography.bodySmall)
                            Text("Tax ${credit.tax.format()} · net ${credit.net.format()}${if (credit.reversedAt != null) " · reversed" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (credit.reversedAt == null) TextButton(onClick = { onReverse(credit) }) { Text("Reverse") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterestProductEditor(
    product: InterestProduct?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (String, InterestProductType, Int, Int, InterestCompounding, Long, Long?, Money?, String) -> Unit
) {
    var type by remember(product?.id) { mutableStateOf(product?.type ?: InterestProductType.SAVINGS_ACCOUNT) }
    val eligibleAccounts = accounts.filter { account ->
        !account.isArchived && if (type == InterestProductType.TIME_DEPOSIT) account.accountType == AccountType.TIME_DEPOSIT
        else account.accountType in setOf(AccountType.TRADITIONAL_BANK, AccountType.DIGITAL_BANK, AccountType.E_WALLET, AccountType.PHYSICAL_SAVINGS)
    }
    var accountId by remember(product?.id, type) { mutableStateOf(product?.accountId ?: eligibleAccounts.firstOrNull()?.id.orEmpty()) }
    var rate by remember(product?.id) { mutableStateOf(product?.annualRateBasisPoints?.let(::formatPercent).orEmpty()) }
    var tax by remember(product?.id) { mutableStateOf(product?.taxRateBasisPoints?.let(::formatPercent) ?: "0") }
    var compounding by remember(product?.id) { mutableStateOf(product?.compounding ?: InterestCompounding.MONTHLY) }
    var opened by remember(product?.id) { mutableStateOf(product?.openedAt?.let(::formatInterestDate) ?: LocalDate.now().toString()) }
    var maturity by remember(product?.id) { mutableStateOf(product?.maturityDate?.let(::formatInterestDate) ?: LocalDate.now().plusYears(1).toString()) }
    var principal by remember(product?.id) { mutableStateOf(product?.principal?.let(::formatMoneyInput).orEmpty()) }
    var notes by remember(product?.id) { mutableStateOf(product?.notes.orEmpty()) }
    var accountMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var compoundingMenu by remember { mutableStateOf(false) }
    var error by remember(product?.id) { mutableStateOf<String?>(null) }
    val hasPostedCredits = product?.credits?.isNotEmpty() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add interest profile" else "Edit interest terms") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (product == null) {
                    SelectText("Product type", type.label, typeMenu, { typeMenu = it }) {
                        InterestProductType.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { type = option; typeMenu = false; accountId = "" }) }
                    }
                    SelectText("Account", eligibleAccounts.firstOrNull { it.id == accountId }?.name ?: "Choose account", accountMenu, { accountMenu = it }) {
                        eligibleAccounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} · ${account.currentLedgerBalance.format()}") }, onClick = { accountId = account.id; accountMenu = false }) }
                    }
                    if (eligibleAccounts.isEmpty()) Text(
                        if (type == InterestProductType.TIME_DEPOSIT) "Add a Time Deposit account with its opening principal in Accounts first."
                        else "Add an eligible bank, e-wallet, or savings account first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else Text("${product.type.label} · ${product.accountName}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(rate, { rate = it; error = null }, label = { Text("Annual rate (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tax, { tax = it; error = null }, label = { Text("Withholding tax (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectText("Compounding", compounding.label, compoundingMenu, { compoundingMenu = it }) {
                    InterestCompounding.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { compounding = option; compoundingMenu = false }) }
                }
                OutlinedTextField(opened, { opened = it; error = null }, label = { Text("Opened (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !hasPostedCredits)
                if (type == InterestProductType.TIME_DEPOSIT) {
                    OutlinedTextField(maturity, { maturity = it; error = null }, label = { Text("Maturity (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(principal, { principal = it; error = null }, label = { Text("Original principal (PHP)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !hasPostedCredits)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("Interest estimates are informational. Only a recorded credit changes your ledger balance. Rate changes apply to future estimates; each posted credit keeps its original rate and tax snapshot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rateBps = parsePercent(rate)
                val taxBps = parsePercent(tax)
                val openedAt = parseInterestDate(opened)
                val maturityAt = if (type == InterestProductType.TIME_DEPOSIT) parseInterestDate(maturity) else null
                val principalMoney = if (type == InterestProductType.TIME_DEPOSIT) Money.parse(principal) else null
                when {
                    product == null && accountId.isBlank() -> error = "Choose an account first."
                    rateBps == null || rateBps !in 0..1_000_000 -> error = "Enter a valid annual rate from 0% to 10,000%."
                    taxBps == null || taxBps !in 0..10_000 -> error = "Enter a valid tax rate from 0% to 100%."
                    openedAt == null -> error = "Enter the opening date as YYYY-MM-DD."
                    type == InterestProductType.TIME_DEPOSIT && maturityAt == null -> error = "Enter a valid maturity date."
                    type == InterestProductType.TIME_DEPOSIT && maturityAt!! <= openedAt -> error = "Maturity must be after the opening date."
                    type == InterestProductType.TIME_DEPOSIT && principalMoney?.isPositive != true -> error = "Enter the original deposit principal."
                    else -> onSave(accountId.ifBlank { product?.accountId.orEmpty() }, type, rateBps, taxBps, compounding, openedAt,
                        maturityAt, principalMoney, notes.trim())
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InterestCreditEditor(
    product: InterestProduct,
    incomeCategories: List<Category>,
    expenseCategories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Money, Money, String, String?, Long, String) -> Unit
) {
    val now = System.currentTimeMillis()
    val estimate = runCatching {
        if (product.type == InterestProductType.TIME_DEPOSIT) {
            InterestCalculator.estimate(product.principal ?: Money.ZERO, product.annualRateBasisPoints, product.taxRateBasisPoints,
                product.compounding, product.openedAt, product.maturityDate ?: now)
        } else {
            val yearAhead = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 365) }.timeInMillis
            InterestCalculator.estimate(product.accountBalance, product.annualRateBasisPoints, product.taxRateBasisPoints,
                product.compounding, now, yearAhead)
        }
    }.getOrNull()
    var gross by remember(product.id) { mutableStateOf(estimate?.gross?.let(::formatMoneyInput).orEmpty()) }
    var tax by remember(product.id) { mutableStateOf(estimate?.tax?.let(::formatMoneyInput).orEmpty()) }
    var incomeCategoryId by remember(product.id) { mutableStateOf(incomeCategories.firstOrNull()?.id.orEmpty()) }
    var taxCategoryId by remember(product.id) { mutableStateOf(expenseCategories.firstOrNull()?.id.orEmpty()) }
    var date by remember(product.id) { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember(product.id) { mutableStateOf("") }
    var incomeMenu by remember { mutableStateOf(false) }
    var expenseMenu by remember { mutableStateOf(false) }
    var error by remember(product.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record interest · ${product.accountName}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estimate?.let { Text("Estimate · gross ${it.gross.format()} · tax ${it.tax.format()} · net ${it.net.format()}", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(gross, { gross = it; error = null }, label = { Text("Actual gross interest (PHP)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tax, { tax = it; error = null }, label = { Text("Tax withheld (PHP)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectText("Income category", incomeCategories.firstOrNull { it.id == incomeCategoryId }?.name ?: "Choose category", incomeMenu, { incomeMenu = it }) {
                    incomeCategories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { incomeCategoryId = category.id; incomeMenu = false }) }
                }
                if ((Money.parse(tax) ?: Money.ZERO).isPositive) SelectText("Tax expense category", expenseCategories.firstOrNull { it.id == taxCategoryId }?.name ?: "Choose category", expenseMenu, { expenseMenu = it }) {
                    expenseCategories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { taxCategoryId = category.id; expenseMenu = false }) }
                }
                OutlinedTextField(date, { date = it; error = null }, label = { Text("Credit date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Text("Only this actual credit is posted. Gross income, withheld tax expense and net account increase are recorded together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val grossMoney = Money.parse(gross)
                val taxMoney = Money.parse(tax.ifBlank { "0" })
                val timestamp = parseInterestDate(date)
                when {
                    grossMoney?.isPositive != true -> error = "Enter actual gross interest greater than zero."
                    taxMoney == null || taxMoney.isNegative || taxMoney > grossMoney -> error = "Tax must be zero or less than or equal to gross interest."
                    incomeCategoryId.isBlank() -> error = "Add or choose an income category."
                    taxMoney.isPositive && taxCategoryId.isBlank() -> error = "Add or choose an expense category for tax."
                    timestamp == null || timestamp > now -> error = "Enter a past or current credit date as YYYY-MM-DD."
                    else -> onSave(grossMoney, taxMoney, incomeCategoryId, if (taxMoney.isPositive) taxCategoryId else null, timestamp, notes.trim())
                }
            }) { Text("Post credit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SelectText(label: String, value: String, expanded: Boolean, onExpanded: (Boolean) -> Unit, menu: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { onExpanded(true) }, modifier = Modifier.fillMaxWidth()) { Text("$label · $value", modifier = Modifier.fillMaxWidth()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }, content = menu)
    }
}

private val interestDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private fun parseInterestDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim(), interestDateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
private fun formatInterestDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(interestDateFormatter)
private fun parsePercent(text: String): Int? = runCatching {
    BigDecimal(text.trim()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
}.getOrNull()
private fun formatPercent(basisPoints: Int): String = BigDecimal.valueOf(basisPoints.toLong())
    .divide(BigDecimal.valueOf(100L), 2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString()
private fun formatMoneyInput(money: Money): String = BigDecimal.valueOf(money.centavos, 2).setScale(2).toPlainString()
