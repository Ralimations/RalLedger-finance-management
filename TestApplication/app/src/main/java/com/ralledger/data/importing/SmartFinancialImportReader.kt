package com.ralledger.data.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class SmartFinancialImportReader(context: Context) {
    private val appContext = context.applicationContext

    fun parse(fileName: String?, mimeType: String?, bytes: ByteArray): FinancialImportDocument {
        val format = FinancialImportFileReader.detectFormat(fileName, mimeType, bytes)
        if (format != FinancialImportFormat.PDF) return FinancialImportFileReader.parseDetected(format, bytes)
        return PdfFinancialStatementReader(appContext).parse(bytes)
    }
}

private class PdfFinancialStatementReader(private val context: Context) {
    fun parse(bytes: ByteArray): FinancialImportDocument {
        require(bytes.isNotEmpty()) { "The selected PDF is empty" }
        require(bytes.size <= 20_000_000) { "Imports are limited to 20 MB per file" }
        synchronized(PDFBoxResourceLoader::class.java) {
            PDFBoxResourceLoader.init(context)
        }

        val document = try {
            PDDocument.load(ByteArrayInputStream(bytes))
        } catch (error: Exception) {
            if (error.javaClass.simpleName.contains("InvalidPassword", ignoreCase = true)) {
                throw IllegalArgumentException("This PDF is password-protected. Save an unlocked copy and try again.")
            }
            throw IllegalArgumentException("The PDF could not be opened. Check that it is a valid, unlocked PDF.")
        }

        document.use { pdf ->
            require(!pdf.isEncrypted) { "This PDF is password-protected. Save an unlocked copy and try again." }
            require(pdf.numberOfPages in 1..MAX_PAGES) { "PDF imports are limited to $MAX_PAGES pages" }
            val tempFile = File.createTempFile("ralledger-import-", ".pdf", context.cacheDir)
            try {
                FileOutputStream(tempFile).use { it.write(bytes) }
                val extractedPages = extractPageText(pdf, tempFile)
                val extracted = extractedPages.joinToString("\n") { it.text }
                require(extracted.isNotBlank()) {
                    "No readable transaction text was found in this PDF. Try exporting the statement as CSV or Excel."
                }
                val ocrUsed = extractedPages.any { it.ocr }
                val parsed = parseStatementLines(extracted)
                val warnings = buildList {
                    add("PDF import is a best-effort text read. Check every date, amount, description and account in the preview.")
                    if (ocrUsed) add("Some scanned pages were read with on-device OCR. Verify OCR results before importing.")
                    if (parsed.headers.any { it.startsWith("Review amount") }) {
                        add("Several monetary values were found on some rows. Choose the transaction amount column; automatic selection is disabled.")
                    }
                    if (parsed.rows.isEmpty()) add("No dated transaction rows were recognized. Use a CSV, TSV, XLSX, OFX or QIF export if available.")
                }
                return FinancialImportDocument(parsed, FinancialImportFormat.PDF, warnings)
            } finally {
                tempFile.delete()
            }
        }
    }

    private data class PageText(val text: String, val ocr: Boolean)

    private fun extractPageText(pdf: PDDocument, tempFile: File): List<PageText> {
        val stripper = PDFTextStripper()
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val output = mutableListOf<PageText>()
        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        stripper.startPage = index + 1
                        stripper.endPage = index + 1
                        val pageText = stripper.getText(pdf).trim()
                        if (pageText.length >= MIN_SELECTABLE_TEXT_CHARS) {
                            output += PageText(pageText, false)
                            continue
                        }
                        renderer.openPage(index).use { page ->
                            val scale = minOf(2.0f, MAX_RENDER_DIMENSION.toFloat() / maxOf(page.width, page.height))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val recognized = Tasks.await(
                                    ocr.process(InputImage.fromBitmap(bitmap, 0)),
                                    OCR_TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS
                                ).text
                                output += PageText(recognized, true)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            ocr.close()
        }
        return output
    }

    private fun parseStatementLines(text: String): ParsedCsvDocument {
        val rows = mutableListOf<ParsedCsvRow>()
        var maxAmounts = 0
        val pending = mutableListOf<RawPdfRow>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
            if (line.isBlank()) return@forEachIndexed
            val dateMatch = datePattern.find(line) ?: return@forEachIndexed
            val amounts = amountPattern.findAll(line).filterNot { it.range.first in dateMatch.range }.toList()
            val description = buildString {
                append(line)
                val removals: List<IntRange> = amounts.map { it.range }.plus(listOf(dateMatch.range))
                removals.sortedByDescending { it.first }.forEach { range ->
                    delete(range.first, range.last + 1)
                }
            }.replace(Regex("[|•·,:;\\s]+$"), "")
                .replace(Regex("^[|•·,:;\\s]+"), "")
                .trim()
            val hasExpenseCue = expenseDescriptionPattern.containsMatchIn(description)
            val hasIncomeCue = incomeDescriptionPattern.containsMatchIn(description)
            val inferExpense = amounts.size == 1 && hasExpenseCue && !hasIncomeCue
            val values = amounts.map { match ->
                val amount = match.value.trim()
                if (inferExpense && !hasExplicitSign(amount)) "-$amount" else amount
            }
            maxAmounts = maxOf(maxAmounts, values.size)
            pending += RawPdfRow(index + 1, dateMatch.value, values, description)
            require(pending.size <= MAX_ROWS) { "The PDF contains more than $MAX_ROWS dated rows" }
        }

        if (pending.isEmpty()) return ParsedCsvDocument(listOf("Date", "Amount", "Account", "Category", "Counterparty", "Description", "Notes"), emptyList())
        val amountHeaders = when {
            maxAmounts <= 1 -> listOf("Amount")
            else -> (1..maxAmounts).map { "Review amount $it" }
        }
        val headers = listOf("Date") + amountHeaders + listOf("Account", "Category", "Counterparty", "Description", "Notes")
        pending.forEach { row ->
            val amountCells = if (maxAmounts <= 1) {
                listOf(row.amounts.singleOrNull().orEmpty())
            } else {
                (0 until maxAmounts).map { row.amounts.getOrNull(it).orEmpty() }
            }
            rows += ParsedCsvRow(
                row.lineNumber,
                listOf(row.date) + amountCells + listOf("", "", "", row.description, "")
            )
        }
        return ParsedCsvDocument(headers, rows)
    }

    private data class RawPdfRow(val lineNumber: Int, val date: String, val amounts: List<String>, val description: String)

    private fun hasExplicitSign(amount: String): Boolean = explicitAmountSignPattern.containsMatchIn(amount)

    private companion object {
        const val MAX_PAGES = 100
        const val MAX_ROWS = 50_000
        const val MIN_SELECTABLE_TEXT_CHARS = 24
        const val MAX_RENDER_DIMENSION = 1_800
        const val OCR_TIMEOUT_SECONDS = 30L
        val datePattern = Regex(
            "(?i)\\b(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{2,4})\\b"
        )
        val amountPattern = Regex(
            "(?<![\\w/])(?:\\(?[-−+]?\\s*(?:[$€£¥₹₱]\\s*)?\\d{1,3}(?:[ ,]\\d{3})*(?:[.,]\\d{2})\\)?|\\(?[-−+]?\\s*(?:[$€£¥₹₱]\\s*)?\\d+[.,]\\d{2}\\)?)(?![\\w/])"
        )
        val explicitAmountSignPattern = Regex("^\\s*(?:[$€£¥₹₱]\\s*)?(?:[-−+]|\\()")
        val expenseDescriptionPattern = Regex(
            "\\b(?:purchase|purchased|debit(?:ed)?|withdraw(?:al|n)?|cash\\s+withdrawal|card\\s+payment|bill\\s+payment|paid\\s+to|pos)\\b",
            RegexOption.IGNORE_CASE
        )
        val incomeDescriptionPattern = Regex(
            "\\b(?:salary|payroll|deposit|credited|credit|income|interest|cashback|received)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
