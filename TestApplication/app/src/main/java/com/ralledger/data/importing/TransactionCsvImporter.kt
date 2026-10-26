package com.ralledger.data.importing

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionTag
import com.ralledger.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CsvDateOrder(val displayName: String) {
    AUTO("Auto (month first when ambiguous)"),
    MONTH_FIRST("Month / day / year"),
    DAY_FIRST("Day / month / year")
}

data class CsvColumnMapping(
    val date: Int? = null,
    val type: Int? = null,
    val amount: Int? = null,
    val debitAmount: Int? = null,
    val creditAmount: Int? = null,
    val account: Int? = null,
    val destinationAccount: Int? = null,
    val category: Int? = null,
    val fee: Int? = null,
    val feeCategory: Int? = null,
    val counterparty: Int? = null,
    val description: Int? = null,
    val notes: Int? = null,
    val currency: Int? = null,
    val labels: Int? = null,
    val dateOrder: CsvDateOrder = CsvDateOrder.AUTO,
    val defaultAccountId: String? = null,
    val defaultDestinationAccountId: String? = null,
    val defaultIncomeCategoryId: String? = null,
    val defaultExpenseCategoryId: String? = null
)

data class ParsedCsvRow(val lineNumber: Int, val values: List<String>)

data class ParsedCsvDocument(val headers: List<String>, val rows: List<ParsedCsvRow>)

data class CsvImportedTransaction(
    val lineNumber: Int,
    val transactionType: TransactionType,
    val timestamp: Long,
    val amount: Money,
    val fee: Money,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val feeCategoryId: String? = null,
    val counterparty: String = "",
    val description: String,
    val notes: String = "",
    val tagIds: Set<String> = emptySet(),
    val dayStartInclusive: Long,
    val nextDayExclusive: Long
)

data class CsvImportPreviewRow(
    val lineNumber: Int,
    val transaction: CsvImportedTransaction? = null,
    val duplicate: Boolean = false,
    val sameFileDuplicate: Boolean = false,
    val error: String? = null,
    val warning: String? = null
)

data class CsvImportPreview(
    val rows: List<CsvImportPreviewRow>,
    val warnings: List<String> = emptyList()
) {
    val importableCount: Int get() = rows.count { it.transaction != null && !it.duplicate && it.error == null }
    val duplicateCount: Int get() = rows.count { it.duplicate }
    val sameFileDuplicateCount: Int get() = rows.count { it.sameFileDuplicate }
    val errorCount: Int get() = rows.count { it.error != null }
    val transactions: List<CsvImportedTransaction>
        get() = rows.mapNotNull { row -> row.transaction?.takeIf { !row.duplicate && row.error == null } }
}

data class CsvImportExecutionResult(
    val imported: Int,
    val duplicates: Int,
    val failedRows: List<Pair<Int, String>>
)

object TransactionCsvImporter {
    private const val MAX_ROWS = 50_000
    private const val MAX_COLUMNS = 100
    internal const val MAX_CELLS = 500_000
    internal const val MAX_CELL_LENGTH = 100_000
    private val utcFormatter = DateTimeFormatter.ISO_INSTANT
    private val localDatePatterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/M/d", Locale.US)
    )

    fun parse(csvText: String): ParsedCsvDocument {
        require(csvText.isNotBlank()) { "The CSV file is empty" }
        require(csvText.length <= 20_000_000) { "The CSV file is larger than the 20 MB import limit" }
        val text = csvText.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(text)
        val records = mutableListOf<ParsedCsvRow>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var recordStartLine = 1
        var index = 0
        var totalCells = 0

        fun finishCell() {
            require(row.size < MAX_COLUMNS) { "The CSV file has more than $MAX_COLUMNS columns" }
            require(totalCells < MAX_CELLS) { "The CSV file has more than 500,000 cells" }
            row += cell.toString()
            totalCells++
            cell.setLength(0)
        }
        fun appendCellCharacter(character: Char) {
            require(cell.length < MAX_CELL_LENGTH) { "A CSV field is too large" }
            cell.append(character)
        }
        fun finishRecord() {
            finishCell()
            if (row.any { it.isNotBlank() }) records += ParsedCsvRow(recordStartLine, row)
            row = mutableListOf()
            recordStartLine = line + 1
            require(records.size <= MAX_ROWS + 1) { "The CSV file has more than $MAX_ROWS data rows" }
        }

        while (index < text.length) {
            val character = text[index]
            if (quoted) {
                when (character) {
                    '"' -> if (text.getOrNull(index + 1) == '"') {
                        appendCellCharacter('"')
                        index++
                    } else {
                        quoted = false
                    }
                    '\r' -> {
                        if (text.getOrNull(index + 1) == '\n') index++
                        appendCellCharacter('\n')
                        line++
                    }
                    '\n' -> {
                        appendCellCharacter('\n')
                        line++
                    }
                    else -> appendCellCharacter(character)
                }
            } else {
                when (character) {
                    '"' -> {
                        require(cell.isEmpty()) { "Unexpected quote on line $line" }
                        quoted = true
                    }
                    delimiter -> finishCell()
                    '\r', '\n' -> {
                        finishRecord()
                        if (character == '\r' && text.getOrNull(index + 1) == '\n') index++
                        line++
                        recordStartLine = line
                    }
                    else -> appendCellCharacter(character)
                }
            }
            index++
        }
        require(!quoted) { "The CSV file has an unfinished quoted field" }
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRecord()
        require(records.isNotEmpty()) { "The CSV file has no header row" }

        val headers = records.first().values.mapIndexed { columnIndex, value ->
            value.trim().ifBlank { "Column ${columnIndex + 1}" }
        }
        require(headers.size <= MAX_COLUMNS) { "The CSV file has more than $MAX_COLUMNS columns" }
        require(headers.any { it.isNotBlank() }) { "The CSV file has no usable column names" }
        return ParsedCsvDocument(headers, records.drop(1).filter { it.values.any(String::isNotBlank) })
    }

    fun detectedDelimiter(text: String): Char = detectDelimiter(text)

    fun suggestMapping(headers: List<String>): CsvColumnMapping {
        val normalized = headers.map { header -> header.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) }
        fun find(vararg names: String): Int? {
            val choices = names.map { it.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) }.toSet()
            return normalized.indexOfFirst { it in choices }.takeIf { it >= 0 }
        }
        return CsvColumnMapping(
            date = find("timestamp_epoch_ms", "date", "transactiondate", "dateoftransaction", "transdate", "postedatutc", "timestamp", "transactiontime", "postdate", "posteddate", "bookingdate", "valuedate"),
            type = find("transactiontype", "type", "transactionkind", "kind", "trntype", "flow", "debitcredit", "drcr"),
            amount = find("amount", "transactionamount", "value", "total", "signedamount", "amountsigned", "netamount", "netvalue"),
            debitAmount = find("debit", "debitamount", "withdrawal", "withdrawals", "moneyout", "paidout", "outgoing"),
            creditAmount = find("credit", "creditamount", "deposit", "deposits", "moneyin", "paidin", "incoming"),
            account = find("accountid", "account", "accountname", "sourceaccount", "fromaccount", "accountnumber"),
            destinationAccount = find("destinationaccountid", "destinationaccount", "destinationaccountname", "toaccount", "transferaccount"),
            category = find("categoryid", "category", "categoryname", "incomeexpensecategory", "categorydescription"),
            fee = find("fee", "transferfee", "charges", "transactionfee"),
            feeCategory = find("feecategory", "transferfeecategory", "feecategoryname"),
            counterparty = find("counterparty", "payee", "merchant", "payer", "name", "beneficiary"),
            description = find("description", "memo", "title", "transactiondescription", "details", "narrative", "reference"),
            notes = find("notes", "note", "comment", "comments"),
            currency = find("accountcurrency", "currency", "currencycode", "curr"),
            labels = find("labels", "tags", "transactionlabels")
        )
    }

    fun preview(
        document: ParsedCsvDocument,
        mapping: CsvColumnMapping,
        accounts: List<Account>,
        categories: List<Category>,
        tags: List<TransactionTag>,
        existingTransactions: List<Transaction>
    ): CsvImportPreview {
        val activeAccounts = accounts.filter { !it.isArchived && it.accountType.isAsset && !it.accountType.isReceivable }
        val activeCategories = categories.filterNot { it.isArchived }
        val existingKeys = existingTransactions.mapNotNull(::duplicateKey).toMutableSet()
        val firstFileRowsByKey = mutableMapOf<String, Int>()
        val warnings = mutableSetOf<String>()
        val rows = document.rows.map { row ->
            try {
                fun value(column: Int?): String = column?.let { row.values.getOrNull(it)?.trim().orEmpty() }.orEmpty()
                require(mapping.date != null) { "Map a date column" }
                require(mapping.amount != null || mapping.debitAmount != null || mapping.creditAmount != null) {
                    "Map an amount column or debit/credit columns"
                }
                require(mapping.account != null || mapping.defaultAccountId != null) { "Choose an account for these rows" }

                val rawAmount = resolveImportedAmount(row, mapping)
                require(rawAmount.centavos != Long.MIN_VALUE) { "Amount is outside the supported range" }
                val typeText = value(mapping.type)
                val type = parseTransactionType(typeText, rawAmount)
                val amount = Money(kotlin.math.abs(rawAmount.centavos))
                require(amount.isPositive) { "Amount must be greater than zero" }
                val timestamp = parseTimestamp(value(mapping.date), mapping.dateOrder)
                val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val nextDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val account = mapping.defaultAccountId
                    ?.let { resolveAccount(it, activeAccounts) }
                    ?: resolveAccount(value(mapping.account), activeAccounts)
                val rawCurrency = value(mapping.currency)
                require(rawCurrency.isBlank() || rawCurrency.equals(account.currency, ignoreCase = true)) {
                    "Currency $rawCurrency does not match ${account.currency} on ${account.name}"
                }
                val destination = if (type == TransactionType.TRANSFER) {
                    mapping.defaultDestinationAccountId
                        ?.let { resolveAccount(it, activeAccounts) }
                        ?: resolveAccount(value(mapping.destinationAccount), activeAccounts)
                } else null
                require(destination == null || destination.id != account.id) { "Transfer source and destination must differ" }
                require(destination == null || destination.currency == account.currency) {
                    "Transfers between different currencies are not supported"
                }

                val feeRaw = value(mapping.fee)
                val parsedFee = if (feeRaw.isBlank()) Money.ZERO else parseImportedAmount(feeRaw)
                    ?: throw IllegalArgumentException("Fee is invalid")
                require(parsedFee.centavos >= 0L) { "Fee cannot be negative" }
                val feeCategory = if (type == TransactionType.TRANSFER && parsedFee.isPositive) {
                    resolveCategory(value(mapping.feeCategory).ifBlank { value(mapping.category) }, activeCategories, CategoryType.EXPENSE)
                } else null

                val category = when (type) {
                    TransactionType.INCOME -> resolveCategory(
                        value(mapping.category).ifBlank { mapping.defaultIncomeCategoryId.orEmpty() },
                        activeCategories,
                        CategoryType.INCOME
                    )
                    TransactionType.EXPENSE -> resolveCategory(
                        value(mapping.category).ifBlank { mapping.defaultExpenseCategoryId.orEmpty() },
                        activeCategories,
                        CategoryType.EXPENSE
                    )
                    TransactionType.REFUND -> resolveCategory(
                        value(mapping.category).ifBlank { mapping.defaultExpenseCategoryId.orEmpty() },
                        activeCategories,
                        CategoryType.EXPENSE
                    )
                    TransactionType.TRANSFER -> null
                    else -> throw IllegalArgumentException("This transaction type cannot be imported from a statement")
                }
                val counterparty = value(mapping.counterparty)
                val description = value(mapping.description).ifBlank { "Imported transaction" }
                val notes = value(mapping.notes)
                val selectedTags = resolveTags(value(mapping.labels), tags, warnings)
                val item = CsvImportedTransaction(
                    lineNumber = row.lineNumber,
                    transactionType = type,
                    timestamp = timestamp,
                    amount = amount,
                    fee = parsedFee,
                    accountId = account.id,
                    destinationAccountId = destination?.id,
                    categoryId = category?.id ?: feeCategory?.id,
                    feeCategoryId = feeCategory?.id,
                    counterparty = counterparty,
                    description = description,
                    notes = notes,
                    tagIds = selectedTags,
                    dayStartInclusive = dayStart,
                    nextDayExclusive = nextDay
                )
                val key = duplicateKey(item)
                val duplicate = key in existingKeys
                val firstMatchingLine = firstFileRowsByKey[key]
                if (firstMatchingLine == null) firstFileRowsByKey[key] = row.lineNumber
                CsvImportPreviewRow(
                    lineNumber = row.lineNumber,
                    transaction = item,
                    duplicate = duplicate,
                    sameFileDuplicate = firstMatchingLine != null,
                    warning = firstMatchingLine?.let {
                        "Matches row $it in this file; both rows are kept. Check for overlapping statement exports."
                    }
                )
            } catch (error: Exception) {
                CsvImportPreviewRow(
                    lineNumber = row.lineNumber,
                    error = error.localizedMessage ?: "Row could not be read"
                )
            }
        }
        return CsvImportPreview(rows, warnings.toList().sorted())
    }

    private fun detectDelimiter(text: String): Char {
        val candidates = listOf(',', ';', '\t', '|')
        fun countOutsideQuotes(line: String, delimiter: Char): Int {
            var quoted = false
            var count = 0
            var index = 0
            while (index < line.length) {
                when (line[index]) {
                    '"' -> if (quoted && line.getOrNull(index + 1) == '"') index++ else quoted = !quoted
                    delimiter -> if (!quoted) count++
                }
                index++
            }
            return count
        }
        text.lineSequence().filter(String::isNotBlank).take(12).forEach { line ->
            val counts = candidates.associateWith { countOutsideQuotes(line, it) }
            val best = counts.maxByOrNull { it.value }
            if (best != null && best.value > 0) return best.key
        }
        return ','
    }

    private fun resolveImportedAmount(row: ParsedCsvRow, mapping: CsvColumnMapping): Money {
        fun value(column: Int?): String = column?.let { row.values.getOrNull(it)?.trim().orEmpty() }.orEmpty()
        if (mapping.amount != null) {
            return parseImportedAmount(value(mapping.amount))
                ?: throw IllegalArgumentException("Amount is missing or invalid")
        }
        require(mapping.debitAmount != null || mapping.creditAmount != null) {
            "Map an amount column or debit/credit columns"
        }
        val debitText = value(mapping.debitAmount)
        val creditText = value(mapping.creditAmount)
        val debit = debitText.takeIf(String::isNotBlank)?.let(::parseImportedAmount)
            ?: if (debitText.isBlank()) null else throw IllegalArgumentException("Debit amount is invalid")
        val credit = creditText.takeIf(String::isNotBlank)?.let(::parseImportedAmount)
            ?: if (creditText.isBlank()) null else throw IllegalArgumentException("Credit amount is invalid")
        require(debit == null || debit.isZero || credit == null || credit.isZero) {
            "Both debit and credit amounts are set"
        }
        return when {
            debit != null && !debit.isZero -> {
                require(debit.centavos != Long.MIN_VALUE) { "Debit amount is outside the supported range" }
                Money(-kotlin.math.abs(debit.centavos))
            }
            credit != null && !credit.isZero -> {
                require(credit.centavos != Long.MIN_VALUE) { "Credit amount is outside the supported range" }
                Money(kotlin.math.abs(credit.centavos))
            }
            else -> throw IllegalArgumentException("Amount is missing or invalid")
        }
    }

    private fun parseImportedAmount(raw: String): Money? {
        var value = raw.trim()
            .replace('\u2212', '-')
            .replace('\u00A0', ' ')
            .replace(" ", "")
        if (value.isEmpty()) return null
        val isParenthesizedNegative = value.startsWith('(') && value.endsWith(')')
        if (isParenthesizedNegative) value = value.substring(1, value.length - 1)
        value = value.replace(Regex("(?i)(USD|EUR|GBP|PHP|CAD|AUD|JPY|INR)"), "")
            .filterNot { it in setOf('₱', '$', '€', '£', '¥', '₹') }
            .replace("+", "")
        if (value.isEmpty()) return null
        val negative = isParenthesizedNegative || value.startsWith('-')
        value = value.removePrefix("-")
        val lastDot = value.lastIndexOf('.')
        val lastComma = value.lastIndexOf(',')
        val decimalSeparator = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastComma >= 0 -> if (value.length - lastComma - 1 in 1..2) ',' else null
            lastDot >= 0 -> if (value.length - lastDot - 1 in 1..2) '.' else null
            else -> null
        }
        val normalized = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    character == decimalSeparator -> append('.')
                    character == '.' || character == ',' || character == '\'' || character == '_' -> Unit
                    else -> return null
                }
            }
        }
        if (normalized.isBlank() || normalized == ".") return null
        return Money.parse(if (negative) "-$normalized" else normalized)
    }

    fun duplicateKey(transaction: CsvImportedTransaction): String = listOf(
        transaction.timestamp.toLocalDateKey(), transaction.transactionType.name, transaction.amount.centavos,
        transaction.fee.centavos, transaction.accountId, transaction.destinationAccountId.orEmpty(),
        transaction.categoryId.orEmpty(),
        transaction.description.trim().lowercase(Locale.ROOT), transaction.counterparty.trim().lowercase(Locale.ROOT)
    ).joinToString("\u0000")

    private fun duplicateKey(transaction: Transaction): String? {
        if (transaction.transactionType !in setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER, TransactionType.REFUND)) return null
        val date = Instant.ofEpochMilli(transaction.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        return listOf(
            date, transaction.transactionType.name, transaction.amount.centavos, transaction.fee.centavos,
            transaction.accountId, transaction.destinationAccountId.orEmpty(), transaction.categoryId.orEmpty(),
            transaction.description.trim().lowercase(Locale.ROOT), transaction.counterparty.trim().lowercase(Locale.ROOT)
        ).joinToString("\u0000")
    }

    private fun Long.toLocalDateKey(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun parseTransactionType(raw: String, amount: Money): TransactionType {
        if (raw.isBlank()) return if (amount.isNegative) TransactionType.EXPENSE else TransactionType.INCOME
        return when (raw.trim().lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')) {
            "income", "credit", "deposit", "money in", "received" -> TransactionType.INCOME
            "expense", "debit", "withdrawal", "money out", "payment", "purchase" -> TransactionType.EXPENSE
            "refund", "refund received", "return", "purchase return" -> TransactionType.REFUND
            "transfer", "internal transfer", "account transfer" -> TransactionType.TRANSFER
            else -> throw IllegalArgumentException("Unsupported transaction type: $raw")
        }
    }

    private fun resolveAccount(value: String, accounts: List<Account>): Account {
        require(value.isNotBlank()) { "Account is missing" }
        accounts.firstOrNull { it.id == value }?.let { return it }
        val matches = accounts.filter { it.name.equals(value.trim(), ignoreCase = true) }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw IllegalArgumentException("Account '$value' was not found or is archived; restore it or map the CSV value to an existing account")
            else -> throw IllegalArgumentException("Account '$value' matches multiple accounts; use a unique account name or ID")
        }
    }

    private fun resolveCategory(value: String, categories: List<Category>, expectedType: CategoryType): Category {
        require(value.isNotBlank()) { "${expectedType.name.lowercase().replaceFirstChar(Char::uppercase)} category is missing" }
        categories.firstOrNull { it.id == value && it.type == expectedType }?.let { return it }
        val matches = categories.filter { it.type == expectedType && it.name.equals(value.trim(), ignoreCase = true) }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw IllegalArgumentException("${expectedType.name.lowercase().replaceFirstChar(Char::uppercase)} category '$value' was not found or is archived")
            else -> throw IllegalArgumentException("Category '$value' matches multiple categories; use a unique name or ID")
        }
    }

    private fun resolveTags(value: String, tags: List<TransactionTag>, warnings: MutableSet<String>): Set<String> {
        if (value.isBlank()) return emptySet()
        val tokens = value.split(" | ").map(String::trim).filter(String::isNotBlank)
        val matched = mutableSetOf<String>()
        tokens.forEach { token ->
            val found = tags.firstOrNull { it.id == token }
                ?: tags.firstOrNull { !it.isArchived && it.name.equals(token, ignoreCase = true) }
            if (found == null || found.isArchived) warnings += "Unknown or archived transaction labels are skipped: $token"
            else matched += found.id
        }
        return matched
    }

    private fun parseTimestamp(value: String, dateOrder: CsvDateOrder): Long {
        require(value.isNotBlank()) { "Date is missing" }
        val normalized = value.trim().replace(Regex("(?<=\\d)'(?=\\d{2}$)"), "/")
        normalized.toLongOrNull()?.let { numeric ->
            if (normalized.length == 8 && normalized.all(Char::isDigit)) {
                val date = LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE)
                return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            return if (kotlin.math.abs(numeric) < 100_000_000_000L) Math.multiplyExact(numeric, 1_000L) else numeric
        }
        runCatching { Instant.from(utcFormatter.parse(normalized)).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()?.let { return it }

        val slashFormat = when (dateOrder) {
            CsvDateOrder.MONTH_FIRST -> listOf("M/d/yyyy", "MM/dd/yyyy", "M/d/yy", "MM/dd/yy")
            CsvDateOrder.DAY_FIRST -> listOf("d/M/yyyy", "dd/MM/yyyy", "d/M/yy", "dd/MM/yy")
            CsvDateOrder.AUTO -> {
                val parts = normalized.split('/')
                val first = parts.firstOrNull()?.toIntOrNull()
                val second = parts.getOrNull(1)?.toIntOrNull()
                when {
                    first != null && first > 12 -> listOf("d/M/yyyy", "dd/MM/yyyy", "d/M/yy", "dd/MM/yy")
                    second != null && second > 12 -> listOf("M/d/yyyy", "MM/dd/yyyy", "M/d/yy", "MM/dd/yy")
                    else -> listOf("M/d/yyyy", "MM/dd/yyyy", "M/d/yy", "MM/dd/yy")
                }
            }
        }
        val patterns = localDatePatterns + slashFormat.map { DateTimeFormatter.ofPattern(it, Locale.US) }
        patterns.forEach { formatter ->
            runCatching {
                val date = LocalDate.parse(normalized, formatter)
                return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        throw IllegalArgumentException("Date '$value' is not supported; use ISO dates or set the day/month order")
    }
}
