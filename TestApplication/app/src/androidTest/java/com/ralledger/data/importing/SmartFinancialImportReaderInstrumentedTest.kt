package com.ralledger.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SmartFinancialImportReaderInstrumentedTest {
    @Test
    fun readsFirstVisibleWorksheetFromXlsxAndFindsItsHeader() {
        val workbook = createXlsx()
        val document = FinancialImportFileReader.parse("statement.xlsx", null, workbook)
        val parsed = document.table

        assertEquals(FinancialImportFormat.XLSX, document.format)
        assertEquals(listOf("Date", "Amount", "Account", "Category", "Description"), parsed.headers)
        assertEquals("2026-06-01", parsed.rows.single().values[0])
        assertEquals("12.50", parsed.rows.single().values[1])
        assertEquals("Market", parsed.rows.single().values[4])
    }

    @Test
    fun rejectsXlsxCellBudgetOverflowDuringParsing() {
        val worksheet = buildString {
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">")
            repeat(500_001) { append("<c r=\"A1\"/>") }
            append("</row></sheetData></worksheet>")
        }

        try {
            XlsxFinancialTableReader.parse(createXlsx(worksheet))
            fail("XLSX input beyond the total cell limit must be rejected")
        } catch (error: IllegalArgumentException) {
            assertEquals("The worksheet has more than 500,000 cells", error.message)
        }
    }

    @Test
    fun extractsSelectablePdfTextIntoAReviewableTransactionRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val bytes = PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(48f, 720f)
                content.showText("Statement transactions")
                content.newLineAtOffset(0f, -24f)
                content.showText("2026-06-01 Market purchase +12.50")
                content.endText()
            }
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
        val parsed = SmartFinancialImportReader(context).parse("statement.pdf", "application/pdf", bytes)

        assertEquals(FinancialImportFormat.PDF, parsed.format)
        assertEquals(1, parsed.table.rows.size)
        assertEquals("2026-06-01", parsed.table.rows.single().values.first())
        assertEquals("+12.50", parsed.table.rows.single().values[1])
        assertTrue(parsed.table.rows.single().values[5].contains("purchase", ignoreCase = true))
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun readsScannedPdfTextWithOnDeviceOcr() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val bitmap = Bitmap.createBitmap(1_800, 280, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("2026-06-01 Market purchase 12.50", 12f, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 72f
            })
        }
        val bytes = try {
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                val image = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(document, page).use { content -> content.drawImage(image, 36f, 600f, 540f, 84f) }
                ByteArrayOutputStream().also(document::save).toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val parsed = SmartFinancialImportReader(context).parse("scanned.pdf", "application/pdf", bytes)

        assertEquals(FinancialImportFormat.PDF, parsed.format)
        assertEquals(1, parsed.table.rows.size)
        assertEquals("2026-06-01", parsed.table.rows.single().values.first())
        assertEquals("-12.50", parsed.table.rows.single().values[1])
        assertTrue(parsed.table.rows.single().values[5].contains("purchase", ignoreCase = true))
        assertTrue(parsed.warnings.any { it.contains("OCR") })
    }

    private fun createXlsx(worksheetXml: String? = null): ByteArray {
        val entries = linkedMapOf(
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to (worksheetXml ?: """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Date</t></is></c><c r="B1" t="inlineStr"><is><t>Amount</t></is></c><c r="C1" t="inlineStr"><is><t>Account</t></is></c><c r="D1" t="inlineStr"><is><t>Category</t></is></c><c r="E1" t="inlineStr"><is><t>Description</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>2026-06-01</t></is></c><c r="B2"><v>12.50</v></c><c r="C2" t="inlineStr"><is><t>Wallet</t></is></c><c r="D2" t="inlineStr"><is><t>Food</t></is></c><c r="E2" t="inlineStr"><is><t>Market</t></is></c></row></sheetData></worksheet>""")
        )
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}
