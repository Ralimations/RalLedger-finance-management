package com.ralledger.data.importing

import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvImporterTest {
    @Test
    fun parsesQuotedCommasQuotesAndMultilineCells() {
        val document = TransactionCsvImporter.parse(
            "\uFEFFDate,Description,Amount\r\n2026-01-02,\"Market, \"\"fresh\"\"\nproduce\",12.50\r\n"
        )

        assertEquals(listOf("Date", "Description", "Amount"), document.headers)
        assertEquals(1, document.rows.size)
        assertEquals("Market, \"fresh\"\nproduce", document.rows.single().values[1])
        assertEquals("12.50", document.rows.single().values[2])
    }

    @Test
    fun mapsRowsToExistingFinancialRecordsAndFindsDuplicates() {
        val document = TransactionCsvImporter.parse(
            "Date,Type,Amount,Account,Category,Description,Counterparty\n" +
                "2026-02-03,Expense,12.50,Wallet,Food,Groceries,Market\n" +
                "2026-02-04,Income,250.00,Wallet,Salary,Pay,Employer\n"
        )
        val wallet = Account("wallet", "Wallet", accountType = AccountType.E_WALLET, currency = "PHP")
        val categories = listOf(
            Category("food", "Food", CategoryType.EXPENSE),
            Category("salary", "Salary", CategoryType.INCOME)
        )
        val existing = Transaction(
            id = "existing",
            journalEntryId = "existing",
            timestamp = java.time.LocalDate.parse("2026-02-03")
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            transactionType = TransactionType.EXPENSE,
            amount = Money(1_250L),
            accountId = "wallet",
            categoryId = "food",
            counterparty = "Market",
            description = "Groceries"
        )
        val mapping = TransactionCsvImporter.suggestMapping(document.headers)
        val preview = TransactionCsvImporter.preview(document, mapping, listOf(wallet), categories, emptyList(), listOf(existing))

        assertEquals(1, preview.importableCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(0, preview.errorCount)
        val imported = preview.transactions.single()
        assertEquals(TransactionType.INCOME, imported.transactionType)
        assertEquals(Money(25_000L), imported.amount)
        assertEquals("salary", imported.categoryId)
        assertEquals("Employer", imported.counterparty)
    }

    @Test
    fun requiresExistingMatchingAccountsCategoriesAndExplicitTransferDestination() {
        val document = TransactionCsvImporter.parse(
            "Date,Type,Amount,Account,Category,To Account\n" +
                "2026-03-01,Expense,5.00,Unknown,Food,\n" +
                "2026-03-02,Transfer,10.00,Wallet,,\n"
        )
        val wallet = Account("wallet", "Wallet", accountType = AccountType.E_WALLET)
        val categories = listOf(Category("food", "Food", CategoryType.EXPENSE))
        val preview = TransactionCsvImporter.preview(
            document,
            TransactionCsvImporter.suggestMapping(document.headers),
            listOf(wallet), categories, emptyList(), emptyList()
        )

        assertEquals(0, preview.importableCount)
        assertEquals(2, preview.errorCount)
        assertTrue(preview.rows[0].error.orEmpty().contains("Unknown"))
        assertTrue(preview.rows[1].error.orEmpty().contains("Account is missing"))
    }

    @Test
    fun allowsSelectingDayFirstDatesForAmbiguousExternalFiles() {
        val document = TransactionCsvImporter.parse("Date,Type,Amount,Account,Category\n02/03/2026,Income,1.00,Wallet,Salary\n")
        val mapping = TransactionCsvImporter.suggestMapping(document.headers).copy(dateOrder = CsvDateOrder.DAY_FIRST)
        val wallet = Account("wallet", "Wallet", accountType = AccountType.E_WALLET)
        val category = Category("salary", "Salary", CategoryType.INCOME)
        val preview = TransactionCsvImporter.preview(document, mapping, listOf(wallet), listOf(category), emptyList(), emptyList())

        assertEquals(1, preview.importableCount)
        val importedDate = java.time.Instant.ofEpochMilli(preview.transactions.single().timestamp)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        assertEquals(java.time.LocalDate.of(2026, 3, 2), importedDate)
    }
}
