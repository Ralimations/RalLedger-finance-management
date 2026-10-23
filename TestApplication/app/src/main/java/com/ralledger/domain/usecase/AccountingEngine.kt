package com.ralledger.domain.usecase

import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.JournalLine
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import java.util.UUID

object AccountingEngine {

    fun createOpeningBalanceEntry(
        accountId: String,
        accountType: AccountType,
        openingBalance: Money,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(openingBalance.isPositive) { "Opening balance must be positive for entry creation" }
        val entryId = UUID.randomUUID().toString()

        val lines = if (accountType.isAsset) {
            listOf(
                JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.DEBIT, openingBalance),
                JournalLine(UUID.randomUUID().toString(), entryId, RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID, LineType.CREDIT, openingBalance)
            )
        } else {
            listOf(
                JournalLine(UUID.randomUUID().toString(), entryId, RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID, LineType.DEBIT, openingBalance),
                JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.CREDIT, openingBalance)
            )
        }

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = "Opening balance setup",
            lines = lines
        )
        check(entry.isBalanced()) { "Opening balance journal entry must be balanced" }
        return entry
    }

    fun createIncomeEntry(
        accountId: String,
        incomeCategoryId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Income amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, incomeCategoryId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Income transaction" },
            lines = lines
        )
        check(entry.isBalanced()) { "Income journal entry must be balanced" }
        return entry
    }

    fun createInterestCreditEntry(
        accountId: String,
        incomeCategoryId: String,
        taxExpenseCategoryId: String?,
        gross: Money,
        taxWithheld: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(gross.isPositive) { "Gross interest must be greater than zero" }
        require(!taxWithheld.isNegative && taxWithheld <= gross) { "Tax withheld must be zero or less than or equal to gross interest" }
        require(taxWithheld.isZero || !taxExpenseCategoryId.isNullOrBlank()) { "Choose an expense category for tax withheld" }
        val entryId = UUID.randomUUID().toString()
        val lines = buildList {
            val net = gross - taxWithheld
            if (net.isPositive) add(JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.DEBIT, net))
            if (taxWithheld.isPositive) {
                add(JournalLine(UUID.randomUUID().toString(), entryId, taxExpenseCategoryId!!, LineType.DEBIT, taxWithheld))
            }
            add(JournalLine(UUID.randomUUID().toString(), entryId, incomeCategoryId, LineType.CREDIT, gross))
        }
        return JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Interest credited" },
            lines = lines
        ).also { check(it.isBalanced()) { "Interest credit journal entry must be balanced" } }
    }

    fun createExpenseEntry(
        accountId: String,
        expenseCategoryId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Expense amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, expenseCategoryId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Expense transaction" },
            lines = lines
        )
        check(entry.isBalanced()) { "Expense journal entry must be balanced" }
        return entry
    }

    fun createTransferEntry(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: Money,
        fee: Money = Money.ZERO,
        feeCategoryId: String? = null,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Transfer amount must be positive" }
        require(sourceAccountId != destinationAccountId) { "Source and destination accounts must differ" }
        require(!fee.isNegative) { "Transfer fee cannot be negative" }
        require(fee.isZero || !feeCategoryId.isNullOrBlank()) { "A fee category is required for a positive transfer fee" }
        val entryId = UUID.randomUUID().toString()

        val totalCreditFromSource = amount + fee
        val lines = mutableListOf<JournalLine>()

        // Debit Destination Asset (+amount)
        lines.add(JournalLine(UUID.randomUUID().toString(), entryId, destinationAccountId, LineType.DEBIT, amount))

        // If Fee > 0, Debit Fee Category (+fee)
        if (fee.isPositive && feeCategoryId != null) {
            lines.add(JournalLine(UUID.randomUUID().toString(), entryId, feeCategoryId, LineType.DEBIT, fee))
        }

        // Credit Source Asset (+(amount + fee))
        lines.add(JournalLine(UUID.randomUUID().toString(), entryId, sourceAccountId, LineType.CREDIT, totalCreditFromSource))

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Transfer between accounts" },
            lines = lines
        )
        check(entry.isBalanced()) { "Transfer journal entry must be balanced" }
        return entry
    }

    fun createReceivableRecognitionEntry(
        receivableAccountId: String,
        incomeCategoryId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Receivable amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, receivableAccountId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, incomeCategoryId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Receivable recognized" },
            lines = lines
        )
        check(entry.isBalanced()) { "Receivable recognition journal entry must be balanced" }
        return entry
    }

    fun createReceivableCollectionEntry(
        destinationAccountId: String,
        receivableAccountId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Collection amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, destinationAccountId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, receivableAccountId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Receivable collected" },
            lines = lines
        )
        check(entry.isBalanced()) { "Receivable collection journal entry must be balanced" }
        return entry
    }

    fun createLoanDisbursementEntry(
        destinationAccountId: String,
        liabilityAccountId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Loan amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, destinationAccountId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, liabilityAccountId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Loan disbursement received" },
            lines = lines
        )
        check(entry.isBalanced()) { "Loan disbursement journal entry must be balanced" }
        return entry
    }

    fun createLoanRepaymentEntry(
        sourceAccountId: String,
        liabilityAccountId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Repayment amount must be positive" }
        val entryId = UUID.randomUUID().toString()

        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, liabilityAccountId, LineType.DEBIT, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, sourceAccountId, LineType.CREDIT, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Loan repayment" },
            lines = lines
        )
        check(entry.isBalanced()) { "Loan repayment journal entry must be balanced" }
        return entry
    }

    fun createPayableRecognitionEntry(
        payableAccountId: String,
        expenseCategoryId: String,
        amount: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(amount.isPositive) { "Bill amount must be positive" }
        val entryId = UUID.randomUUID().toString()
        return JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Bill recorded" },
            lines = listOf(
                JournalLine(UUID.randomUUID().toString(), entryId, expenseCategoryId, LineType.DEBIT, amount),
                JournalLine(UUID.randomUUID().toString(), entryId, payableAccountId, LineType.CREDIT, amount)
            )
        ).also { check(it.isBalanced()) { "Bill recognition journal entry must be balanced" } }
    }

    fun createDebtPaymentEntry(
        sourceAccountId: String,
        payableAccountId: String,
        principal: Money,
        interest: Money,
        interestCategoryId: String?,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(!principal.isNegative && !interest.isNegative && (principal.isPositive || interest.isPositive)) {
            "Enter a positive principal or interest payment"
        }
        require(interest.isZero || !interestCategoryId.isNullOrBlank()) {
            "Choose an expense category for interest"
        }
        val entryId = UUID.randomUUID().toString()
        val lines = buildList {
            if (principal.isPositive) add(JournalLine(UUID.randomUUID().toString(), entryId, payableAccountId, LineType.DEBIT, principal))
            if (interest.isPositive) add(JournalLine(UUID.randomUUID().toString(), entryId, interestCategoryId!!, LineType.DEBIT, interest))
            add(JournalLine(UUID.randomUUID().toString(), entryId, sourceAccountId, LineType.CREDIT, principal + interest))
        }
        return JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Debt payment" },
            lines = lines
        ).also { check(it.isBalanced()) { "Debt payment journal entry must be balanced" } }
    }

    fun createReconciliationAdjustmentEntry(
        accountId: String,
        accountType: AccountType,
        difference: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(!difference.isZero) { "Adjustment difference cannot be zero" }
        require(accountType.isAsset || accountType.isLiability) { "Only asset and liability accounts can be reconciled" }
        val entryId = UUID.randomUUID().toString()

        val accountIncreasesOnCredit = accountType.isLiability
        val accountLineType = when {
            difference.isPositive && accountIncreasesOnCredit -> LineType.CREDIT
            difference.isPositive -> LineType.DEBIT
            accountIncreasesOnCredit -> LineType.DEBIT
            else -> LineType.CREDIT
        }
        val equityLineType = if (accountLineType == LineType.DEBIT) LineType.CREDIT else LineType.DEBIT
        val amount = if (difference.isPositive) difference else -difference
        val lines = listOf(
            JournalLine(UUID.randomUUID().toString(), entryId, accountId, accountLineType, amount),
            JournalLine(UUID.randomUUID().toString(), entryId, RalLedgerDatabase.SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID, equityLineType, amount)
        )

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Reconciliation balance adjustment" },
            lines = lines
        )
        check(entry.isBalanced()) { "Reconciliation adjustment entry must be balanced" }
        return entry
    }

    fun createReversalEntry(
        original: JournalEntry,
        originalEntryId: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(original.id == originalEntryId) { "Original entry ID does not match" }
        require(original.isBalanced()) { "Only balanced entries can be reversed" }
        val reversalId = UUID.randomUUID().toString()
        val reversedLines = original.lines.map { line ->
            JournalLine(
                id = UUID.randomUUID().toString(),
                journalEntryId = reversalId,
                accountId = line.accountId,
                lineType = if (line.lineType == LineType.DEBIT) LineType.CREDIT else LineType.DEBIT,
                amount = line.amount
            )
        }
        return JournalEntry(
            id = reversalId,
            timestamp = timestamp,
            description = "Reversal of ${original.description.ifBlank { originalEntryId }}",
            lines = reversedLines
        ).also { check(it.isBalanced()) { "Reversal entry must be balanced" } }
    }
}
