package com.ralledger.domain.usecase

import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingEngineTest {

    @Test
    fun openingBalance_createsBalancedEntryAgainstEquity() {
        val amount = Money(100000L) // 1,000.00 PHP
        val entry = AccountingEngine.createOpeningBalanceEntry(
            accountId = "bank_1",
            accountType = AccountType.TRADITIONAL_BANK,
            openingBalance = amount
        )

        assertTrue(entry.isBalanced())

        val debitLine = entry.lines.find { it.lineType == LineType.DEBIT }!!
        val creditLine = entry.lines.find { it.lineType == LineType.CREDIT }!!

        assertEquals("bank_1", debitLine.accountId)
        assertEquals(amount, debitLine.amount)
        assertEquals(amount, creditLine.amount)
    }

    @Test
    fun income_createsBalancedDebitAssetAndCreditIncome() {
        val amount = Money(500000L) // 5,000.00 PHP
        val entry = AccountingEngine.createIncomeEntry(
            accountId = "bank_1",
            incomeCategoryId = "cat_salary",
            amount = amount,
            description = "Monthly Salary"
        )

        assertTrue(entry.isBalanced())

        val debitLine = entry.lines.find { it.lineType == LineType.DEBIT }!!
        val creditLine = entry.lines.find { it.lineType == LineType.CREDIT }!!

        assertEquals("bank_1", debitLine.accountId)
        assertEquals("cat_salary", creditLine.accountId)
        assertEquals(amount, debitLine.amount)
    }

    @Test
    fun refund_createsBalancedDebitAssetAndCreditExpense() {
        val amount = Money(2_350L)
        val entry = AccountingEngine.createRefundEntry(
            accountId = "wallet_1",
            expenseCategoryId = "cat_food",
            amount = amount,
            description = "Returned groceries"
        )

        assertTrue(entry.isBalanced())
        assertEquals("wallet_1", entry.lines.single { it.lineType == LineType.DEBIT }.accountId)
        assertEquals("cat_food", entry.lines.single { it.lineType == LineType.CREDIT }.accountId)
        assertTrue(runCatching {
            AccountingEngine.createRefundEntry("wallet_1", "cat_food", Money.ZERO, "Invalid")
        }.isFailure)
    }

    @Test
    fun transferWithFee_preservesTotalBalanceAndRecordsFeeOnce() {
        val transferAmount = Money(200000L) // 2,000.00 PHP
        val feeAmount = Money(1500L) // 15.00 PHP

        val entry = AccountingEngine.createTransferEntry(
            sourceAccountId = "wallet_1",
            destinationAccountId = "bank_1",
            amount = transferAmount,
            fee = feeAmount,
            feeCategoryId = "cat_other_expense",
            description = "Bank Cash-In"
        )

        assertTrue(entry.isBalanced())

        val destDebit = entry.lines.find { it.accountId == "bank_1" && it.lineType == LineType.DEBIT }!!
        val feeDebit = entry.lines.find { it.accountId == "cat_other_expense" && it.lineType == LineType.DEBIT }!!
        val sourceCredit = entry.lines.find { it.accountId == "wallet_1" && it.lineType == LineType.CREDIT }!!

        assertEquals(transferAmount, destDebit.amount)
        assertEquals(feeAmount, feeDebit.amount)
        assertEquals(transferAmount + feeAmount, sourceCredit.amount)
    }

    @Test
    fun receivableRecognition_createsBalancedEntry() {
        val amount = Money(1500000L) // 15,000.00 PHP
        val entry = AccountingEngine.createReceivableRecognitionEntry(
            receivableAccountId = "rec_1",
            incomeCategoryId = "cat_freelance",
            amount = amount,
            description = "Invoice Recognized"
        )
        assertTrue(entry.isBalanced())
        assertEquals(2, entry.lines.size)
        assertEquals(amount, entry.lines.find { it.lineType == LineType.DEBIT }?.amount)
    }

    @Test
    fun receivableCollection_transfersFromReceivableAssetToBankAssetWithoutDoubleIncome() {
        val collectionAmount = Money(300000L) // 3,000.00 PHP

        val entry = AccountingEngine.createReceivableCollectionEntry(
            destinationAccountId = "bank_1",
            receivableAccountId = "rec_123",
            amount = collectionAmount,
            description = "Invoice 123 Partial Payment"
        )

        assertTrue(entry.isBalanced())

        val bankDebit = entry.lines.find { it.lineType == LineType.DEBIT }!!
        val recCredit = entry.lines.find { it.lineType == LineType.CREDIT }!!

        assertEquals("bank_1", bankDebit.accountId)
        assertEquals("rec_123", recCredit.accountId)
        assertEquals(collectionAmount, bankDebit.amount)
        assertEquals(collectionAmount, recCredit.amount)
    }

    @Test
    fun loanDisbursementAndRepayment_createBalancedEntries() {
        val loanAmount = Money(500000L)
        val disburseEntry = AccountingEngine.createLoanDisbursementEntry(
            destinationAccountId = "bank_1",
            liabilityAccountId = "loan_1",
            amount = loanAmount,
            description = "Loan taken"
        )
        assertTrue(disburseEntry.isBalanced())

        val repayEntry = AccountingEngine.createLoanRepaymentEntry(
            sourceAccountId = "bank_1",
            liabilityAccountId = "loan_1",
            amount = loanAmount,
            description = "Loan repaid"
        )
        assertTrue(repayEntry.isBalanced())
    }

    @Test
    fun moneyPrecision_handlesCentavosAccuratelyWithoutFloatingPointRoundingErrors() {
        val m1 = Money.fromDouble(12.34)
        val m2 = Money.fromDouble(56.78)
        val sum = m1 + m2

        assertEquals(1234L, m1.centavos)
        assertEquals(5678L, m2.centavos)
        assertEquals(6912L, sum.centavos)
        assertEquals("₱69.12", sum.format("PHP"))
    }

    @Test
    fun moneyFormatting_preservesCentavosBeyondDoubleExactIntegerRange() {
        val amount = Money(9_007_199_254_740_993L)

        assertTrue(amount.format("PHP").endsWith("409.93"))
    }

    @Test
    fun moneyParser_preservesCentavosAndRejectsMalformedOrRoundedInput() {
        assertEquals(Money(123456L), Money.parse("1,234.56"))
        assertEquals(Money(1L), Money.parse("0.01"))
        assertEquals(Money(-250L), Money.parse("-2.50"))
        assertEquals(null, Money.parse("1,23.45"))
        assertEquals(null, Money.parse("12.345"))
        assertEquals(null, Money.parse("not money"))
        assertEquals(null, Money.parse("92233720368547758.08"))
    }

    @Test
    fun reconciliationAdjustment_usesNormalSideForAssetAndLiabilityChanges() {
        val cases = listOf(
            Triple(AccountType.TRADITIONAL_BANK, Money(100L), LineType.DEBIT),
            Triple(AccountType.TRADITIONAL_BANK, Money(-100L), LineType.CREDIT),
            Triple(AccountType.LIABILITY_ACCOUNT, Money(100L), LineType.CREDIT),
            Triple(AccountType.LIABILITY_ACCOUNT, Money(-100L), LineType.DEBIT)
        )

        cases.forEach { (type, difference, expectedAccountSide) ->
            val entry = AccountingEngine.createReconciliationAdjustmentEntry(
                accountId = "account_1",
                accountType = type,
                difference = difference,
                description = "Test reconciliation"
            )
            val accountLine = entry.lines.first { it.accountId == "account_1" }
            assertEquals(expectedAccountSide, accountLine.lineType)
            assertEquals(Money(100L), accountLine.amount)
            assertTrue(entry.isBalanced())
        }
    }

    @Test
    fun reversalEntry_mirrorsEveryOriginalLineAndRemainsBalanced() {
        val original = AccountingEngine.createTransferEntry(
            sourceAccountId = "wallet_1",
            destinationAccountId = "bank_1",
            amount = Money(10000L),
            fee = Money(125L),
            feeCategoryId = "cat_other_expense",
            description = "Cash in"
        )

        val reversal = AccountingEngine.createReversalEntry(original, original.id)

        assertTrue(reversal.isBalanced())
        assertEquals(original.lines.size, reversal.lines.size)
        original.lines.forEach { line ->
            val reversed = reversal.lines.single { it.accountId == line.accountId }
            assertEquals(line.amount, reversed.amount)
            assertEquals(if (line.lineType == LineType.DEBIT) LineType.CREDIT else LineType.DEBIT, reversed.lineType)
        }
    }
}
