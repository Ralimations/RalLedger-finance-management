package com.ralledger.data.export

import com.ralledger.domain.model.Transaction
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Produces a spreadsheet-friendly, UTF-8 CSV snapshot of the posted transaction journal. */
object TransactionCsvExporter {
    private val headers = listOf(
        "record_id",
        "timestamp_epoch_ms",
        "posted_at_utc",
        "transaction_type",
        "amount",
        "fee",
        "account_id",
        "account",
        "account_currency",
        "destination_account_id",
        "destination_account",
        "destination_currency",
        "category_id",
        "category",
        "secondary_category_id",
        "secondary_category",
        "counterparty",
        "description",
        "notes",
        "is_reversed",
        "reverses_entry_id",
        "label_ids",
        "labels"
    )

    fun encode(transactions: List<Transaction>, accountCurrencies: Map<String, String>): ByteArray {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val csv = buildString {
            append('\uFEFF') // Excel and other desktop spreadsheet programs recognize UTF-8 reliably.
            append(headers.joinToString(",") { textCell(it) })
            append('\n')
            transactions.sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.journalEntryId })
                .forEach { transaction ->
                    val primaryCurrency = accountCurrencies[transaction.accountId] ?: "PHP"
                    val destinationCurrency = transaction.destinationAccountId?.let(accountCurrencies::get)
                        ?: primaryCurrency
                    val values = listOf(
                        textCell(transaction.journalEntryId),
                        transaction.timestamp.toString(),
                        textCell(dateFormat.format(Date(transaction.timestamp))),
                        textCell(transaction.transactionType.name),
                        decimal(transaction.amount.centavos),
                        decimal(transaction.fee.centavos),
                        textCell(transaction.accountId),
                        textCell(transaction.accountName),
                        textCell(primaryCurrency),
                        textCell(transaction.destinationAccountId.orEmpty()),
                        textCell(transaction.destinationAccountName.orEmpty()),
                        textCell(destinationCurrency),
                        textCell(transaction.categoryId.orEmpty()),
                        textCell(transaction.categoryName.orEmpty()),
                        textCell(transaction.secondaryCategoryId.orEmpty()),
                        textCell(transaction.secondaryCategoryName.orEmpty()),
                        textCell(transaction.counterparty),
                        textCell(transaction.description),
                        textCell(transaction.notes),
                        transaction.isReversed.toString(),
                        textCell(transaction.reversesEntryId.orEmpty()),
                        textCell(transaction.tagIds.sorted().joinToString(" | ")),
                        textCell(transaction.tagNames.joinToString(" | "))
                    )
                    append(values.joinToString(","))
                    append('\n')
                }
        }
        return csv.toByteArray(Charsets.UTF_8)
    }

    private fun decimal(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString()

    private fun textCell(value: String): String {
        val firstMeaningfulCharacter = value.dropWhile(Char::isWhitespace).firstOrNull()
        val safeValue = if (firstMeaningfulCharacter in setOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }
}
