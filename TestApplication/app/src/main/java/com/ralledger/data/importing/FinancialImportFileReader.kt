package com.ralledger.data.importing

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

enum class FinancialImportFormat(val displayName: String) {
    CSV("CSV / text table"),
    TSV("Tab-separated table"),
    XLSX("Excel workbook"),
    OFX("OFX / QFX statement"),
    QIF("QIF statement"),
    PDF("PDF statement")
}

data class FinancialImportDocument(
    val table: ParsedCsvDocument,
    val format: FinancialImportFormat,
    val warnings: List<String> = emptyList()
)

object FinancialImportFileReader {
    private const val MAX_INPUT_BYTES = 20_000_000

    fun detectFormat(fileName: String?, mimeType: String?, bytes: ByteArray): FinancialImportFormat {
        require(bytes.isNotEmpty()) { "The selected file is empty" }
        require(bytes.size <= MAX_INPUT_BYTES) { "Imports are limited to 20 MB per file" }
        if (bytes.startsWithAscii("%PDF-")) return FinancialImportFormat.PDF

        val lowerName = fileName.orEmpty().lowercase(Locale.ROOT)
        val lowerMime = mimeType.orEmpty().lowercase(Locale.ROOT)
        if (bytes.startsWith(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) {
            if (containsWorkbookEntry(bytes)) return FinancialImportFormat.XLSX
            throw IllegalArgumentException("This ZIP file is not an Excel workbook")
        }
        if (bytes.startsWith(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()))) {
            throw IllegalArgumentException("Legacy .xls workbooks are not supported yet. Save the file as .xlsx or CSV and try again.")
        }

        val text = decodeText(bytes)
        val trimmed = text.trimStart('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
        when {
            lowerName.endsWith(".pdf") || lowerMime == "application/pdf" -> return FinancialImportFormat.PDF
            lowerName.endsWith(".ofx") || lowerName.endsWith(".qfx") ||
                lowerMime.contains("ofx") || lowerMime.contains("qfx") ||
                trimmed.contains("<OFX", ignoreCase = true) || trimmed.contains("<STMTTRN", ignoreCase = true) ->
                return FinancialImportFormat.OFX
            lowerName.endsWith(".qif") || lowerMime.contains("qif") || trimmed.startsWith("!Type:", ignoreCase = true) ->
                return FinancialImportFormat.QIF
            lowerName.endsWith(".xlsx") || lowerMime.contains("spreadsheetml") ->
                throw IllegalArgumentException("The Excel workbook could not be read. Try saving it again as .xlsx.")
            lowerName.endsWith(".tsv") || lowerName.endsWith(".tab") || lowerMime.contains("tab-separated") ->
                return FinancialImportFormat.TSV
            lowerName.endsWith(".csv") || lowerMime.contains("csv") || lowerMime.startsWith("text/") ||
                lowerMime == "application/vnd.ms-excel" || lowerName.isBlank() ||
                lowerMime == "application/octet-stream" || lowerMime == "*/*" -> {
                return if (TransactionCsvImporter.detectedDelimiter(text) == '\t') {
                    FinancialImportFormat.TSV
                } else {
                    FinancialImportFormat.CSV
                }
            }
            else -> throw IllegalArgumentException("This file type is not supported yet. Choose a CSV, TSV, XLSX, OFX, QFX, QIF or PDF file.")
        }
    }

    fun parse(fileName: String?, mimeType: String?, bytes: ByteArray): FinancialImportDocument {
        val format = detectFormat(fileName, mimeType, bytes)
        return when (format) {
            FinancialImportFormat.CSV, FinancialImportFormat.TSV -> FinancialImportDocument(
                table = normalizeHeaderRow(TransactionCsvImporter.parse(decodeText(bytes))),
                format = format
            )
            FinancialImportFormat.OFX -> FinancialImportDocument(OfxFinancialStatementReader.parse(decodeText(bytes)), format)
            FinancialImportFormat.QIF -> FinancialImportDocument(QifFinancialStatementReader.parse(decodeText(bytes)), format)
            FinancialImportFormat.XLSX -> FinancialImportDocument(XlsxFinancialTableReader.parse(bytes), format)
            FinancialImportFormat.PDF -> throw IllegalArgumentException("PDF reading requires the on-device document reader")
        }
    }

    private fun normalizeHeaderRow(document: ParsedCsvDocument): ParsedCsvDocument {
        fun score(values: List<String>): Int {
            val mapping = TransactionCsvImporter.suggestMapping(values)
            return (if (mapping.date != null) 5 else 0) +
                (if (mapping.amount != null) 5 else 0) +
                (if (mapping.debitAmount != null || mapping.creditAmount != null) 4 else 0) +
                (if (mapping.account != null) 1 else 0) +
                (if (mapping.category != null) 1 else 0)
        }
        val candidates = listOf(document.headers) + document.rows.take(20).map { it.values }
        val bestIndex = candidates.indices.maxByOrNull { score(candidates[it]) } ?: 0
        if (score(candidates[bestIndex]) < 9) {
            val dataRowIndex = document.rows.indexOfFirst { row -> looksLikeTransactionRow(row.values) }
            if (looksLikeTransactionRow(document.headers)) {
                return ParsedCsvDocument(
                    headers = generatedHeaders(document.headers.size),
                    rows = listOf(ParsedCsvRow(1, document.headers)) + document.rows
                )
            }
            if (dataRowIndex >= 0) {
                val width = document.rows[dataRowIndex].values.size
                return ParsedCsvDocument(generatedHeaders(width), document.rows.drop(dataRowIndex))
            }
            return document
        }
        if (bestIndex == 0) return document
        val headers = candidates[bestIndex].mapIndexed { index, value -> value.trim().ifBlank { "Column ${index + 1}" } }
        val rows = document.rows.drop(bestIndex).map { row ->
            ParsedCsvRow(row.lineNumber, row.values)
        }
        return ParsedCsvDocument(headers, rows)
    }

    private fun generatedHeaders(width: Int): List<String> = (0 until width).map { "Column ${it + 1}" }

    private fun looksLikeTransactionRow(values: List<String>): Boolean {
        val hasDate = values.any { value ->
            val candidate = value.trim()
            candidate.matches(Regex("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}")) ||
                candidate.matches(Regex("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}")) ||
                candidate.matches(Regex("(?i)(?:\\d{1,2}\\s+[A-Za-z]{3,9}|[A-Za-z]{3,9}\\s+\\d{1,2}),?\\s+\\d{2,4}"))
        }
        val hasAmount = values.any { value ->
            val candidate = value.trim().replace("₱", "").replace("$", "").replace("€", "").replace("£", "").replace("¥", "")
            candidate.matches(Regex("\\(?[-−+]?\\s*\\d[\\d ,.'’]*(?:[.,]\\d{1,2})\\)?"))
        }
        return hasDate && hasAmount
    }

    fun decodeText(bytes: ByteArray): String {
        val offset: Int
        val charset: Charset
        when {
            bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) -> {
                offset = 3
                charset = StandardCharsets.UTF_8
            }
            bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) -> {
                offset = 2
                charset = StandardCharsets.UTF_16LE
            }
            bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) -> {
                offset = 2
                charset = StandardCharsets.UTF_16BE
            }
            else -> {
                offset = 0
                charset = StandardCharsets.UTF_8
            }
        }
        val decoder = charset.newDecoder()
        if (charset == StandardCharsets.UTF_8) {
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        }
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (_: Exception) {
            Charset.forName("windows-1252").decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        }
    }

    private fun containsWorkbookEntry(bytes: ByteArray): Boolean = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.any { entry ->
                entry.name == "xl/workbook.xml"
            }
        }
    }.getOrDefault(false)

    private fun ByteArray.startsWithAscii(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

object OfxFinancialStatementReader {
    private val transactionBlock = Regex(
        "<STMTTRN\\b[^>]*>(.*?)(?:</STMTTRN>|(?=<STMTTRN\\b)|(?=</BANKTRANLIST>))",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tagValue = Regex("<([A-Z0-9_.-]+)\\b[^>]*>([^<\\r\\n]*)", RegexOption.IGNORE_CASE)
    private val headers = listOf("Date", "Type", "Amount", "Account", "Category", "Counterparty", "Description", "Notes")

    fun parse(text: String): ParsedCsvDocument {
        require(text.isNotBlank()) { "The OFX file is empty" }
        val blocks = transactionBlock.findAll(text).toList()
        require(blocks.isNotEmpty()) { "No OFX transactions were found" }
        require(blocks.size <= 50_000) { "The statement has more than 50,000 transactions" }
        val rows = blocks.mapIndexed { index, match ->
            val fields = tagValue.findAll(match.groupValues[1]).associate { result ->
                result.groupValues[1].uppercase(Locale.ROOT) to decodeEntities(result.groupValues[2].trim())
            }
            val rawDate = fields["DTPOSTED"].orEmpty()
            val date = rawDate.take(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
                ?.let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" }
                ?: rawDate
            val type = when (fields["TRNTYPE"]?.uppercase(Locale.ROOT)) {
                "CREDIT", "DEP", "DIRECTDEP", "INT", "DIV", "TAXREFUND" -> "Income"
                "DEBIT", "CHECK", "PAYMENT", "POS", "ATM", "FEE", "SRVCHG" -> "Expense"
                "XFER" -> "Transfer"
                else -> ""
            }
            val payee = fields["NAME"].orEmpty()
            val memo = fields["MEMO"].orEmpty()
            listOf(
                date,
                type,
                fields["TRNAMT"].orEmpty(),
                "",
                "",
                payee,
                memo.ifBlank { payee },
                listOfNotNull(
                    fields["CHECKNUM"].orEmpty().takeIf(String::isNotBlank)?.let { "Check $it" },
                    fields["FITID"].orEmpty().takeIf(String::isNotBlank)?.let { "OFX ID $it" }
                )
                    .joinToString(" · ")
            ).let { ParsedCsvRow(index + 1, it) }
        }
        return ParsedCsvDocument(headers, rows)
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
}

object QifFinancialStatementReader {
    private val headers = listOf("Date", "Type", "Amount", "Account", "Destination account", "Category", "Counterparty", "Description", "Notes")

    fun parse(text: String): ParsedCsvDocument {
        require(text.isNotBlank()) { "The QIF file is empty" }
        val rows = mutableListOf<ParsedCsvRow>()
        var transaction = linkedMapOf<Char, MutableList<String>>()
        var recordLine = 1
        var lineNumber = 0
        var accountName = ""
        var section = ""

        fun finishRecord() {
            if (transaction.isNotEmpty() && !section.equals("Account", ignoreCase = true)) {
                val date = transaction['D']?.firstOrNull().orEmpty()
                val amount = transaction['T']?.firstOrNull() ?: transaction['U']?.firstOrNull().orEmpty()
                val payee = transaction['P']?.firstOrNull().orEmpty()
                val memo = transaction['M']?.firstOrNull().orEmpty()
                val rawCategory = transaction['L']?.firstOrNull().orEmpty()
                val destination = rawCategory.takeIf { it.startsWith('[') && it.endsWith(']') }
                    ?.removeSurrounding("[", "]").orEmpty()
                val category = rawCategory.takeUnless { destination.isNotBlank() }.orEmpty()
                val splitCount = transaction['S']?.size ?: 0
                val notes = buildList {
                    transaction['N']?.firstOrNull()?.takeIf(String::isNotBlank)?.let { add("Ref $it") }
                    if (splitCount > 0) add("QIF split categories require review")
                }.joinToString(" · ")
                if (date.isNotBlank() || amount.isNotBlank()) {
                    rows += ParsedCsvRow(
                        recordLine,
                        listOf(
                            date,
                            when {
                                section.equals("Invst", ignoreCase = true) -> "Investment"
                                destination.isNotBlank() -> "Transfer"
                                else -> ""
                            },
                            if (splitCount > 0) "" else amount,
                            accountName,
                            destination,
                            category,
                            payee,
                            memo.ifBlank { payee },
                            notes
                        )
                    )
                }
            }
            transaction = linkedMapOf()
            recordLine = lineNumber + 1
        }

        text.lineSequence().forEachIndexed { index, rawLine ->
            lineNumber = index + 1
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEachIndexed
            when {
                line.startsWith("!Type:", ignoreCase = true) -> {
                    finishRecord()
                    section = line.substringAfter(':').trim()
                }
                line.equals("!Account", ignoreCase = true) -> {
                    finishRecord()
                    section = "Account"
                }
                line.startsWith("!Option:", ignoreCase = true) -> Unit
                line == "^" -> finishRecord()
                line.startsWith("!") -> Unit
                line.firstOrNull()?.isLetter() == true -> {
                    val field = line.first().uppercaseChar()
                    val value = line.drop(1).trim()
                    if (section.equals("Account", ignoreCase = true)) {
                        if (field == 'N') accountName = value
                    } else {
                        transaction.getOrPut(field) { mutableListOf() }.add(value)
                    }
                }
            }
        }
        finishRecord()
        require(rows.isNotEmpty()) { "No QIF transactions were found" }
        require(rows.size <= 50_000) { "The statement has more than 50,000 transactions" }
        return ParsedCsvDocument(headers, rows)
    }
}
