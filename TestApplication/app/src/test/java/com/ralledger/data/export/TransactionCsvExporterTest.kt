package com.ralledger.data.export

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvExporterTest {
    @Test
    fun exportKeepsExactAmountsAndQuotesSpreadsheetTextSafely() {
        val transaction = Transaction(
            id = "entry-1",
            journalEntryId = "entry-1",
            timestamp = 1_700_000_000_123L,
            transactionType = TransactionType.EXPENSE,
            amount = Money(1_234_567L),
            fee = Money(25L),
            accountId = "wallet",
            accountName = "Main, wallet",
            categoryId = "food",
            categoryName = "Meals",
            description = "=HYPERLINK(\"https://example.invalid\")",
            notes = "Bought \"fresh\" food\nfor home",
            tagIds = setOf("label-a", "label-b"),
            tagNames = listOf("Food", "Home"),
            projectId = "project-1",
            projectName = "Kitchen remodel"
        )

        val csv = TransactionCsvExporter.encode(listOf(transaction), mapOf("wallet" to "PHP"))
            .toString(Charsets.UTF_8)
        val body = csv.removePrefix("\uFEFF").trimEnd()

        assertTrue(
            body.startsWith("\"record_id\",\"timestamp_epoch_ms\",\"posted_at_utc\",\"transaction_type\"")
        )
        assertTrue(body.contains(",12345.67,0.25,"))
        assertTrue(body.contains("\"Main, wallet\""))
        assertTrue(body.contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(body.contains("\"Bought \"\"fresh\"\" food\nfor home\""))
        assertTrue(body.contains("\"label-a | label-b\",\"Food | Home\""))
        assertTrue(body.contains("\"project-1\",\"Kitchen remodel\""))
        assertTrue(body.contains("2023-11-14T22:13:20.123Z"))
    }

    @Test
    fun exportSortsByPostingTimeAndMarksReversalLinks() {
        val later = Transaction(
            id = "later",
            journalEntryId = "later",
            timestamp = 200L,
            transactionType = TransactionType.REVERSAL,
            amount = Money(500L),
            accountId = "wallet",
            reversesEntryId = "original"
        )
        val earlier = later.copy(id = "earlier", journalEntryId = "earlier", timestamp = 100L)

        val rows = TransactionCsvExporter.encode(listOf(later, earlier), emptyMap())
            .toString(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().lines()

        assertTrue(rows[1].startsWith("\"earlier\",100,"))
        assertTrue(rows[2].startsWith("\"later\",200,"))
        assertTrue(rows[2].endsWith("\"original\",\"\",\"\",\"\",\"\""))
    }
}
