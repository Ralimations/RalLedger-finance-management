package com.ralledger.domain.usecase

import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class InsightsPeriodType(val label: String) {
    DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), CUSTOM("Custom"), ALL_TIME("All time")
}

data class InsightsDateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "End date must not be before start date" }
    }

    val endExclusive: LocalDate get() = endInclusive.plusDays(1)
    val dayCount: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, endExclusive)
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

data class InsightsPeriod(
    val type: InsightsPeriodType,
    val anchorDate: LocalDate,
    val customRange: InsightsDateRange? = null
) {
    fun resolveRange(transactions: List<Transaction>, zone: ZoneId): InsightsDateRange? = when (type) {
        InsightsPeriodType.DAY -> InsightsDateRange(anchorDate, anchorDate)
        InsightsPeriodType.WEEK -> {
            val start = anchorDate.with(DayOfWeek.MONDAY)
            InsightsDateRange(start, start.plusDays(6))
        }
        InsightsPeriodType.MONTH -> {
            val start = YearMonth.from(anchorDate).atDay(1)
            InsightsDateRange(start, YearMonth.from(anchorDate).atEndOfMonth())
        }
        InsightsPeriodType.YEAR -> {
            val start = anchorDate.withDayOfYear(1)
            InsightsDateRange(start, anchorDate.withDayOfYear(anchorDate.lengthOfYear()))
        }
        InsightsPeriodType.CUSTOM -> customRange
        InsightsPeriodType.ALL_TIME -> {
            val dates = transactions.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            dates.minOrNull()?.let { earliest -> InsightsDateRange(earliest, dates.maxOrNull()!!) }
        }
    }

    fun move(direction: Int): InsightsPeriod {
        require(direction != 0) { "Direction must be non-zero" }
        return when (type) {
            InsightsPeriodType.DAY -> copy(anchorDate = anchorDate.plusDays(direction.toLong()))
            InsightsPeriodType.WEEK -> copy(anchorDate = anchorDate.plusWeeks(direction.toLong()))
            InsightsPeriodType.MONTH -> copy(anchorDate = anchorDate.plusMonths(direction.toLong()))
            InsightsPeriodType.YEAR -> copy(anchorDate = anchorDate.plusYears(direction.toLong()))
            InsightsPeriodType.CUSTOM -> {
                val range = customRange ?: InsightsDateRange(anchorDate, anchorDate)
                val shift = Math.multiplyExact(range.dayCount, direction.toLong())
                val next = InsightsDateRange(range.start.plusDays(shift), range.endInclusive.plusDays(shift))
                copy(anchorDate = next.start, customRange = next)
            }
            InsightsPeriodType.ALL_TIME -> this
        }
    }

    fun label(locale: Locale = Locale.getDefault(), zone: ZoneId, transactions: List<Transaction> = emptyList()): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return when (type) {
            InsightsPeriodType.DAY -> anchorDate.format(formatter)
            InsightsPeriodType.WEEK -> resolveRange(transactions, zone)?.let { "${it.start.format(formatter)} – ${it.endInclusive.format(formatter)}" } ?: "Week"
            InsightsPeriodType.MONTH -> anchorDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            InsightsPeriodType.YEAR -> anchorDate.year.toString()
            InsightsPeriodType.CUSTOM -> customRange?.let { "${it.start.format(formatter)} – ${it.endInclusive.format(formatter)}" } ?: "Choose dates"
            InsightsPeriodType.ALL_TIME -> "All time"
        }
    }
}

data class InsightsBucket(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val label: String,
    val income: Money = Money.ZERO,
    val expenses: Money = Money.ZERO
)

data class InsightsNamedAmount(val id: String, val name: String, val amount: Money)

data class InsightsResult(
    val range: InsightsDateRange?,
    val income: Money,
    val expenses: Money,
    val net: Money,
    val grossInterest: Money,
    val interestTax: Money,
    val netInterest: Money,
    val subscriptionPayments: Money,
    val receivablesCollected: Money,
    val categoryTotals: List<InsightsNamedAmount>,
    val incomeSources: List<InsightsNamedAmount>,
    val buckets: List<InsightsBucket>
)

/** Calculates date-bounded statistics from journal-backed transaction projections. */
object InsightsCalculator {
    fun calculate(
        transactions: List<Transaction>,
        categories: List<Category>,
        period: InsightsPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): InsightsResult {
        val range = period.resolveRange(transactions, zone)
            ?: return InsightsResult(
                null, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
                emptyList(), emptyList(), emptyList()
            )
        val categoryNames = categories.associate { it.id to it.name }
        val entriesByJournalId = transactions.associateBy { it.journalEntryId }
        val bucketSpecs = makeBuckets(range, period.type, locale)
        val bucketValues = bucketSpecs.map { it.copy(income = Money.ZERO, expenses = Money.ZERO) }.toMutableList()
        val categoryTotals = linkedMapOf<String, Pair<String, Money>>()
        val incomeSources = linkedMapOf<String, Pair<String, Money>>()
        var income = Money.ZERO
        var expenses = Money.ZERO
        var grossInterest = Money.ZERO
        var interestTax = Money.ZERO
        var netInterest = Money.ZERO
        var subscriptionPayments = Money.ZERO
        var receivablesCollected = Money.ZERO

        transactions.forEach { transaction ->
            val date = Instant.ofEpochMilli(transaction.timestamp).atZone(zone).toLocalDate()
            if (!range.contains(date)) return@forEach

            val isReversal = transaction.transactionType == TransactionType.REVERSAL
            val source = if (isReversal) transaction.reversesEntryId?.let(entriesByJournalId::get) else transaction
            if (source == null) return@forEach
            val sign = if (isReversal) -1 else 1
            val event = eventFor(source, categoryNames)
            val signedIncome = event.income.withSign(sign)
            val signedExpenses = event.expenses.withSign(sign)
            val signedGrossInterest = event.grossInterest.withSign(sign)
            val signedInterestTax = event.interestTax.withSign(sign)
            val signedInterest = event.netInterest.withSign(sign)
            val signedSubscriptionPayments = event.subscriptionPayments.withSign(sign)
            val signedReceivablesCollected = event.receivablesCollected.withSign(sign)
            income += signedIncome
            expenses += signedExpenses
            grossInterest += signedGrossInterest
            interestTax += signedInterestTax
            netInterest += signedInterest
            subscriptionPayments += signedSubscriptionPayments
            receivablesCollected += signedReceivablesCollected
            event.incomeSource?.let { (id, name, amount) ->
                addAmount(incomeSources, id, name, amount.withSign(sign))
            }
            event.categoryExpenses.forEach { (id, name, amount) ->
                addAmount(categoryTotals, id, name, amount.withSign(sign))
            }

            val bucketIndex = bucketSpecs.indexOfFirst { !date.isBefore(it.start) && date.isBefore(it.endExclusive) }
            if (bucketIndex >= 0) {
                val current = bucketValues[bucketIndex]
                bucketValues[bucketIndex] = current.copy(
                    income = current.income + signedIncome,
                    expenses = current.expenses + signedExpenses
                )
            }
        }

        return InsightsResult(
            range = range,
            income = income,
            expenses = expenses,
            net = income - expenses,
            grossInterest = grossInterest,
            interestTax = interestTax,
            netInterest = netInterest,
            subscriptionPayments = subscriptionPayments,
            receivablesCollected = receivablesCollected,
            categoryTotals = categoryTotals.map { (id, pair) -> InsightsNamedAmount(id, pair.first, pair.second) }
                .sortedByDescending { it.amount.centavos },
            incomeSources = incomeSources.map { (id, pair) -> InsightsNamedAmount(id, pair.first, pair.second) }
                .sortedByDescending { it.amount.centavos },
            buckets = bucketValues
        )
    }

    private data class Event(
        val income: Money = Money.ZERO,
        val expenses: Money = Money.ZERO,
        val grossInterest: Money = Money.ZERO,
        val interestTax: Money = Money.ZERO,
        val netInterest: Money = Money.ZERO,
        val subscriptionPayments: Money = Money.ZERO,
        val receivablesCollected: Money = Money.ZERO,
        val incomeSource: Triple<String, String, Money>? = null,
        val categoryExpenses: List<Triple<String, String, Money>> = emptyList()
    )

    private fun eventFor(transaction: Transaction, categoryNames: Map<String, String>): Event {
        fun category(id: String?, recordedName: String?, fallback: String): Pair<String, String> =
            (id ?: fallback) to (id?.let(categoryNames::get) ?: recordedName ?: fallback.replace('-', ' '))

        val incomeCategory = category(transaction.categoryId, transaction.categoryName, "other-income")
        val expenseCategory = category(transaction.categoryId, transaction.categoryName, "uncategorized")
        return when (transaction.transactionType) {
            TransactionType.INCOME, TransactionType.RECEIVABLE_RECOGNITION -> Event(
                income = transaction.amount,
                incomeSource = Triple(incomeCategory.first, incomeCategory.second, transaction.amount)
            )
            TransactionType.INTEREST_CREDIT -> {
                val taxCategory = category(transaction.secondaryCategoryId, transaction.secondaryCategoryName, "interest-tax")
                Event(
                    income = transaction.amount,
                    expenses = transaction.fee,
                    grossInterest = transaction.amount,
                    interestTax = transaction.fee,
                    netInterest = transaction.amount - transaction.fee,
                    incomeSource = Triple(incomeCategory.first, incomeCategory.second, transaction.amount),
                    categoryExpenses = if (transaction.fee.isPositive) listOf(Triple(taxCategory.first, taxCategory.second, transaction.fee)) else emptyList()
                )
            }
            TransactionType.EXPENSE, TransactionType.SUBSCRIPTION_PAYMENT,
            TransactionType.RECEIVABLE_WRITEOFF, TransactionType.PAYABLE_RECOGNITION -> Event(
                expenses = transaction.amount,
                subscriptionPayments = if (transaction.transactionType == TransactionType.SUBSCRIPTION_PAYMENT) transaction.amount else Money.ZERO,
                categoryExpenses = listOf(Triple(expenseCategory.first, expenseCategory.second, transaction.amount))
            )
            TransactionType.REFUND -> {
                val offset = Money.ZERO - transaction.amount
                Event(
                    expenses = offset,
                    categoryExpenses = listOf(Triple(expenseCategory.first, expenseCategory.second, offset))
                )
            }
            TransactionType.RECEIVABLE_COLLECTION -> Event(receivablesCollected = transaction.amount)
            TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT -> {
                val interestCategory = category(transaction.categoryId, transaction.categoryName, "interest-expense")
                Event(
                    expenses = transaction.fee,
                    categoryExpenses = if (transaction.fee.isPositive) listOf(Triple(interestCategory.first, interestCategory.second, transaction.fee)) else emptyList()
                )
            }
            TransactionType.TRANSFER -> {
                val feeCategory = category(transaction.categoryId, transaction.categoryName, "transfer-fee")
                Event(
                    expenses = transaction.fee,
                    categoryExpenses = if (transaction.fee.isPositive) listOf(Triple(feeCategory.first, feeCategory.second, transaction.fee)) else emptyList()
                )
            }
            else -> Event()
        }
    }

    private fun makeBuckets(range: InsightsDateRange, type: InsightsPeriodType, locale: Locale): List<InsightsBucket> {
        val cadence = when (type) {
            InsightsPeriodType.DAY, InsightsPeriodType.WEEK, InsightsPeriodType.MONTH -> Cadence.DAY
            InsightsPeriodType.YEAR -> Cadence.MONTH
            InsightsPeriodType.CUSTOM -> when {
                range.dayCount <= 31 -> Cadence.DAY
                range.dayCount <= 180 -> Cadence.WEEK
                range.dayCount <= 1095 -> Cadence.MONTH
                else -> Cadence.YEAR
            }
            InsightsPeriodType.ALL_TIME -> when {
                range.dayCount <= 31 -> Cadence.DAY
                range.dayCount <= 180 -> Cadence.WEEK
                range.dayCount <= 1095 -> Cadence.MONTH
                else -> Cadence.YEAR
            }
        }
        val result = mutableListOf<InsightsBucket>()
        var cursor = range.start
        while (cursor.isBefore(range.endExclusive)) {
            val naturalEnd = when (cadence) {
                Cadence.DAY -> cursor.plusDays(1)
                Cadence.WEEK -> cursor.with(DayOfWeek.MONDAY).plusWeeks(1).let { if (it.isAfter(cursor)) it else cursor.plusWeeks(1) }
                Cadence.MONTH -> YearMonth.from(cursor).plusMonths(1).atDay(1)
                Cadence.YEAR -> cursor.withDayOfYear(1).plusYears(1)
            }
            val end = minOf(naturalEnd, range.endExclusive)
            val label = when (cadence) {
                Cadence.DAY -> cursor.format(
                    DateTimeFormatter.ofPattern(
                        if (type == InsightsPeriodType.WEEK || type == InsightsPeriodType.CUSTOM || type == InsightsPeriodType.ALL_TIME) "d MMM" else "d",
                        locale
                    )
                )
                Cadence.WEEK -> cursor.format(DateTimeFormatter.ofPattern("d MMM", locale))
                Cadence.MONTH -> cursor.format(DateTimeFormatter.ofPattern("MMM", locale))
                Cadence.YEAR -> cursor.format(DateTimeFormatter.ofPattern("yyyy", locale))
            }
            result += InsightsBucket(cursor, end, label)
            cursor = end
        }
        return result
    }

    private enum class Cadence { DAY, WEEK, MONTH, YEAR }

    private fun Money.withSign(sign: Int): Money = if (sign < 0) -this else this

    private fun addAmount(target: MutableMap<String, Pair<String, Money>>, id: String, name: String, amount: Money) {
        val current = target[id]
        target[id] = name to ((current?.second ?: Money.ZERO) + amount)
    }
}
