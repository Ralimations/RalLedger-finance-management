package com.ralledger.ui.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.compose.setContent
import com.ralledger.MainActivity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionCorrectionTypeSelectorInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun correctionDialogCanConvertExpenseToIncome() {
        var savedCorrection: TransactionCorrection? = null
        val accounts = listOf(
            Account(id = "wallet", name = "Wallet", accountType = AccountType.PHYSICAL_WALLET)
        )
        val categories = listOf(
            Category(id = "food", name = "Food", type = CategoryType.EXPENSE),
            Category(id = "salary", name = "Salary", type = CategoryType.INCOME)
        )
        val original = Transaction(
            id = "expense",
            journalEntryId = "expense",
            timestamp = 1_000L,
            transactionType = TransactionType.EXPENSE,
            amount = Money(1_000L),
            accountId = "wallet",
            categoryId = "food",
            description = "Original purchase"
        )

        composeRule.activity.setContent {
            MaterialTheme {
                RecordTransactionDialog(
                    accounts = accounts,
                    categories = categories,
                    initialTransaction = original,
                    template = null,
                    isCorrection = true,
                    onDismiss = {},
                    onIncome = { _, _, _, _, _, _, _ -> error("Correction must use onCorrect") },
                    onExpense = { _, _, _, _, _, _, _ -> error("Correction must use onCorrect") },
                    onTransfer = { _, _, _, _, _, _, _, _ -> error("Correction must use onCorrect") },
                    onCorrect = { savedCorrection = it }
                )
            }
        }

        composeRule.onNodeWithText("Income").performClick()
        composeRule.onNodeWithText("Saving adds a reversal and a corrected entry. The original stays in history.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Save correction").performClick()

        composeRule.runOnIdle {
            assertEquals(TransactionType.INCOME, savedCorrection?.transactionType)
            assertEquals("salary", savedCorrection?.categoryId)
            assertEquals("wallet", savedCorrection?.accountId)
        }
    }
}
