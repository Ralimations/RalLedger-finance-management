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
        require(!openingBalance.isZero) { "Opening balance cannot be zero for entry creation" }
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

    fun createReconciliationAdjustmentEntry(
        accountId: String,
        difference: Money,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): JournalEntry {
        require(!difference.isZero) { "Adjustment difference cannot be zero" }
        val entryId = UUID.randomUUID().toString()

        val lines = if (difference.isPositive) {
            listOf(
                JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.DEBIT, difference),
                JournalLine(UUID.randomUUID().toString(), entryId, RalLedgerDatabase.SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID, LineType.CREDIT, difference)
            )
        } else {
            val absDiff = -difference
            listOf(
                JournalLine(UUID.randomUUID().toString(), entryId, RalLedgerDatabase.SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID, LineType.DEBIT, absDiff),
                JournalLine(UUID.randomUUID().toString(), entryId, accountId, LineType.CREDIT, absDiff)
            )
        }

        val entry = JournalEntry(
            id = entryId,
            timestamp = timestamp,
            description = description.ifBlank { "Reconciliation balance adjustment" },
            lines = lines
        )
        check(entry.isBalanced()) { "Reconciliation adjustment entry must be balanced" }
        return entry
    }
}
