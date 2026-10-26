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
import com.ralledger.ui.navigation.QuickActionHubSheet
import com.ralledger.ui.planning.PlanningQuickAction
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
                    onRefund = { _, _, _, _, _, _, _ -> error("Correction must use onCorrect") },
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

    @Test
    fun duplicateRefundUsesRefundPostingCallback() {
        var savedRefund: List<Any?>? = null
        val accounts = listOf(Account(id = "wallet", name = "Wallet", accountType = AccountType.PHYSICAL_WALLET))
        val categories = listOf(Category(id = "food", name = "Food", type = CategoryType.EXPENSE))
        val original = Transaction(
            id = "refund",
            journalEntryId = "refund",
            timestamp = 1_000L,
            transactionType = TransactionType.REFUND,
            amount = Money(1_000L),
            accountId = "wallet",
            categoryId = "food",
            description = "Returned purchase"
        )

        composeRule.activity.setContent {
            MaterialTheme {
                RecordTransactionDialog(
                    accounts = accounts,
                    categories = categories,
                    initialTransaction = original,
                    template = null,
                    isCorrection = false,
                    onDismiss = {},
                    onIncome = { _, _, _, _, _, _, _ -> error("Refund duplicate must not post as income") },
                    onExpense = { _, _, _, _, _, _, _ -> error("Refund duplicate must not post as expense") },
                    onTransfer = { _, _, _, _, _, _, _, _ -> error("Refund duplicate must not post as transfer") },
                    onRefund = { accountId, categoryId, amount, description, _, _, _ ->
                        savedRefund = listOf(accountId, categoryId, amount, description)
                    },
                    onCorrect = { error("Duplicate must not correct") }
                )
            }
        }

        composeRule.onNodeWithText("Refund").assertIsDisplayed()
        composeRule.onNodeWithText("Create duplicate").performClick()

        composeRule.runOnIdle {
            assertEquals("wallet", savedRefund?.get(0))
            assertEquals("food", savedRefund?.get(1))
            assertEquals(Money(1_000L), savedRefund?.get(2))
        }
    }

    @Test
    fun moreActionsRoutesRefundToTheTransactionFlow() {
        var selectedType: String? = null

        composeRule.activity.setContent {
            MaterialTheme {
                QuickActionHubSheet(
                    onDismiss = {},
                    onTransactionSelected = { selectedType = it },
                    onPlanningActionSelected = { _: PlanningQuickAction -> error("Refund should open transaction entry") }
                )
            }
        }

        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Refund").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("REFUND", selectedType) }
    }
}
