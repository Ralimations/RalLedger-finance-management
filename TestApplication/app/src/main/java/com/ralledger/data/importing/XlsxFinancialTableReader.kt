package com.ralledger.data.importing

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipInputStream

object XlsxFinancialTableReader {
    private const val MAX_ENTRIES = 128
    private const val MAX_UNCOMPRESSED_BYTES = 40_000_000
    private const val MAX_ROWS = 50_000
    private const val MAX_COLUMNS = 100
    private const val RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun parse(bytes: ByteArray): ParsedCsvDocument {
        require(bytes.size <= 20_000_000) { "Imports are limited to 20 MB per file" }
        val entries = readRelevantEntries(bytes)
        val workbook = entries["xl/workbook.xml"] ?: throw IllegalArgumentException("Excel workbook is missing its workbook index")
        val relationships = entries["xl/_rels/workbook.xml.rels"]
        val sheetTarget = chooseFirstSheet(workbook, relationships)
            ?: throw IllegalArgumentException("The Excel workbook has no readable worksheets")
        val sheetBytes = entries[sheetTarget] ?: throw IllegalArgumentException("The first Excel worksheet could not be read")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val dateStyles = entries["xl/styles.xml"]?.let(::parseDateStyles).orEmpty()
        val tableRows = parseSheet(sheetBytes, sharedStrings, dateStyles)
        require(tableRows.isNotEmpty()) { "The first worksheet is empty" }
        val headerRowIndex = findHeaderRow(tableRows)
        val headerRow = tableRows[headerRowIndex]
        val columnCount = maxOf(headerRow.values.keys.maxOrNull()?.plus(1) ?: 0, 1)
        require(columnCount <= MAX_COLUMNS) { "The worksheet has more than $MAX_COLUMNS columns" }
        val headers = (0 until columnCount).map { column ->
            headerRow.values[column]?.trim()?.takeIf(String::isNotBlank) ?: "Column ${column + 1}"
        }
        val rows = tableRows.drop(headerRowIndex + 1).mapNotNull { row ->
            val values = (0 until columnCount).map { row.values[it].orEmpty() }
            if (values.any(String::isNotBlank)) ParsedCsvRow(row.number, values) else null
        }
        return ParsedCsvDocument(headers, rows)
    }

    private data class SheetRow(val number: Int, val values: Map<Int, String>)

    private fun readRelevantEntries(bytes: ByteArray): Map<String, ByteArray> {
        val relevant = linkedMapOf<String, ByteArray>()
        var uncompressedTotal = 0
        var entryCount = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "Excel workbook contains too many internal files" }
                val shouldKeep = entry.name == "xl/workbook.xml" ||
                    entry.name == "xl/_rels/workbook.xml.rels" ||
                    entry.name == "xl/sharedStrings.xml" ||
                    entry.name == "xl/styles.xml" ||
                    (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml"))
                val output = if (shouldKeep) ByteArrayOutputStream() else null
                val buffer = ByteArray(8_192)
                var entryBytes = 0
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entryBytes += count
                    uncompressedTotal += count
                    require(uncompressedTotal <= MAX_UNCOMPRESSED_BYTES) { "Excel workbook expands beyond the safe import limit" }
                    require(entryBytes <= 20_000_000) { "An Excel worksheet or shared-string table is too large" }
                    output?.write(buffer, 0, count)
                }
                if (output != null) relevant[entry.name] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return relevant
    }

    private fun chooseFirstSheet(workbook: ByteArray, relationships: ByteArray?): String? {
        val parser = newParser(workbook)
        var relationshipId: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                if (parser.getAttributeValue(null, "state") !in setOf("hidden", "veryHidden")) {
                    relationshipId = parser.getAttributeValue(RELATIONSHIP_NS, "id")
                    if (relationshipId != null) break
                }
            }
        }
        if (relationshipId == null) return "xl/worksheets/sheet1.xml"
        if (relationships == null) return "xl/worksheets/sheet1.xml"
        val relationParser = newParser(relationships)
        var target: String? = null
        while (relationParser.next() != XmlPullParser.END_DOCUMENT) {
            if (relationParser.eventType == XmlPullParser.START_TAG && relationParser.name == "Relationship" &&
                relationParser.getAttributeValue(null, "Id") == relationshipId
            ) {
                target = relationParser.getAttributeValue(null, "Target")
                break
            }
        }
        val path = target ?: return "xl/worksheets/sheet1.xml"
        val normalized = normalizeRelationshipTarget(path)
        require(normalized.startsWith("xl/worksheets/") && normalized.endsWith(".xml")) {
            "The workbook's first sheet points outside its worksheet folder"
        }
        return normalized
    }

    private fun normalizeRelationshipTarget(target: String): String {
        val parts = mutableListOf<String>()
        val source = if (target.startsWith('/')) target.drop(1) else "xl/$target"
        source.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else error("Invalid workbook relationship")
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val values = mutableListOf<String>()
        val parser = newParser(bytes)
        var inItem = false
        var inText = false
        val text = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; text.setLength(0) }
                    "t" -> if (inItem) inText = true
                }
                XmlPullParser.TEXT -> if (inItem && inText) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> { values += text.toString(); inItem = false }
                }
            }
            require(values.size <= MAX_ROWS * MAX_COLUMNS) { "Excel workbook has too many shared strings" }
        }
        return values
    }

    private fun parseDateStyles(bytes: ByteArray): Set<Int> {
        val parser = newParser(bytes)
        val customFormats = mutableSetOf<Int>()
        val cellFormatIds = mutableListOf<Int>()
        var inCellFormats = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "numFmt" -> {
                        val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                        val format = parser.getAttributeValue(null, "formatCode").orEmpty()
                        if (id != null && looksLikeDateFormat(format)) customFormats += id
                    }
                    "cellXfs" -> inCellFormats = true
                    "xf" -> if (inCellFormats) cellFormatIds += parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                }
                XmlPullParser.END_TAG -> if (parser.name == "cellXfs") inCellFormats = false
            }
        }
        return cellFormatIds.mapIndexedNotNull { styleIndex, numberFormatId ->
            styleIndex.takeIf { numberFormatId in 14..22 || numberFormatId in 45..47 || numberFormatId in customFormats }
        }.toSet()
    }

    private fun looksLikeDateFormat(format: String): Boolean {
        val unquoted = format.lowercase(Locale.ROOT)
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\\\."), "")
        return unquoted.contains('y') || unquoted.contains('d') || unquoted.contains('m')
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>, dateStyles: Set<Int>): List<SheetRow> {
        val parser = newParser(bytes)
        val rows = mutableListOf<SheetRow>()
        var currentRowNumber = 0
        var currentValues: MutableMap<Int, String>? = null
        var currentColumn = -1
        var currentType = ""
        var currentStyle = -1
        var inValue = false
        var inInlineText = false
        var value = StringBuilder()
        var cellText = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: currentRowNumber + 1
                        currentValues = linkedMapOf()
                    }
                    "c" -> {
                        val reference = parser.getAttributeValue(null, "r").orEmpty()
                        currentColumn = columnIndex(reference)
                        require(currentColumn < MAX_COLUMNS) { "The worksheet has more than $MAX_COLUMNS columns" }
                        currentType = parser.getAttributeValue(null, "t").orEmpty()
                        currentStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        value = StringBuilder()
                        cellText = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> if (currentType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> when {
                    inValue -> value.append(parser.text)
                    inInlineText -> cellText.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = if (currentType == "inlineStr") cellText.toString() else value.toString()
                        val resolved = when (currentType) {
                            "s" -> raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                            "inlineStr", "str" -> raw
                            "b" -> if (raw == "1") "true" else "false"
                            "e" -> ""
                            else -> if (currentStyle in dateStyles) excelDate(raw) else raw
                        }
                        if (resolved.length > 100_000) throw IllegalArgumentException("A worksheet cell is too large")
                        if (currentColumn >= 0 && currentValues != null) currentValues?.put(currentColumn, resolved)
                        currentColumn = -1
                    }
                    "row" -> {
                        val values = currentValues.orEmpty()
                        if (values.values.any(String::isNotBlank)) rows += SheetRow(currentRowNumber, values.toMap())
                        currentValues = null
                        require(rows.size <= MAX_ROWS + 20) { "The worksheet has more than 50,000 data rows" }
                    }
                }
            }
        }
        return rows
    }

    private fun findHeaderRow(rows: List<SheetRow>): Int {
        val candidates = rows.take(20).mapIndexed { index, row ->
            val width = (row.values.keys.maxOrNull() ?: -1) + 1
            val values = (0 until width).map { row.values[it].orEmpty() }
            index to TransactionCsvImporter.suggestMapping(values).let { mapping ->
                (if (mapping.date != null) 4 else 0) +
                    (if (mapping.amount != null) 4 else 0) +
                    (if (mapping.debitAmount != null || mapping.creditAmount != null) 3 else 0) +
                    (if (mapping.account != null) 1 else 0) +
                    (if (mapping.category != null) 1 else 0)
            }
        }
        val best = candidates.maxByOrNull { it.second }
        return if (best != null && best.second >= 8) best.first else 0
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter).uppercase(Locale.ROOT)
        if (letters.isEmpty()) return -1
        var index = 0
        letters.forEach { character -> index = index * 26 + character.code - 'A'.code + 1 }
        return index - 1
    }

    private fun excelDate(raw: String): String = runCatching {
        val serial = raw.toDouble()
        val wholeDays = kotlin.math.floor(serial).toLong()
        LocalDate.of(1899, 12, 30).plusDays(wholeDays).toString()
    }.getOrDefault(raw)

    private fun newParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }
}
