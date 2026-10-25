package com.ralledger.ui.accounts

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDetailPresentationTest {
    @Test
    fun transferDirectionFollowsTheSelectedAccount() {
        val transfer = transaction(
            id = "transfer",
            type = TransactionType.TRANSFER,
            accountId = "source",
            destinationAccountId = "destination"
        )

        assertEquals("−", accountTransactionDirection(transfer, "source"))
        assertEquals("+", accountTransactionDirection(transfer, "destination"))
        assertEquals("", accountTransactionDirection(transfer, "other"))
    }

    @Test
    fun accountHistoryIncludesBothSidesAndSortsNewestFirst() {
        val outgoing = transaction("out", TransactionType.EXPENSE, "wallet", timestamp = 10)
        val incomingTransfer = transaction("in", TransactionType.TRANSFER, "other", "wallet", timestamp = 30)
        val unrelated = transaction("unrelated", TransactionType.INCOME, "other", timestamp = 50)

        assertEquals(
            listOf("in", "out"),
            transactionsForAccount("wallet", listOf(outgoing, unrelated, incomingTransfer)).map { it.id }
        )
    }

    @Test
    fun incomeAndExpenseAmountsUseSemanticSigns() {
        assertEquals("+", accountTransactionDirection(transaction("income", TransactionType.INCOME, "wallet"), "wallet"))
        assertEquals("−", accountTransactionDirection(transaction("expense", TransactionType.EXPENSE, "wallet"), "wallet"))
        assertEquals("", accountTransactionDirection(transaction("opening", TransactionType.OPENING_BALANCE, "wallet"), "wallet"))
    }

    private fun transaction(
        id: String,
        type: TransactionType,
        accountId: String,
        destinationAccountId: String? = null,
        timestamp: Long = 1
    ) = Transaction(
        id = id,
        journalEntryId = id,
        timestamp = timestamp,
        transactionType = type,
        amount = Money(100),
        accountId = accountId,
        destinationAccountId = destinationAccountId
    )
}
