package com.ralledger.ui.components

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ralledger.MainActivity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.ui.theme.RalLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyAmountFieldInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun calculatorResultUpdatesTheMoneyField() {
        val amount = mutableStateOf("")
        composeRule.activity.setContent {
            RalLedgerTheme {
                Surface {
                    MoneyAmountField(
                        value = amount.value,
                        onValueChange = { amount.value = it },
                        label = "Amount"
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open calculator").performClick()
        composeRule.onNodeWithText("Calculator").assertIsDisplayed()
        listOf("1", "2", "+", "3", "=").forEach { composeRule.tapCalculatorKey(it) }
        composeRule.onNodeWithText("Use amount").performClick()

        composeRule.runOnIdle { assertEquals("15.00", amount.value) }
        composeRule.onNodeWithText("15.00").assertIsDisplayed()
    }

    @Test
    fun accountBalanceSummaryShowsActiveBalancesAndHidesArchivedAccounts() {
        val accounts = listOf(
            Account(
                id = "wallet",
                name = "Daily wallet",
                accountType = AccountType.PHYSICAL_WALLET,
                currentLedgerBalance = Money.parse("1250.00")!!
            ),
            Account(
                id = "bank",
                name = "Savings account",
                accountType = AccountType.TRADITIONAL_BANK,
                currentLedgerBalance = Money.parse("20450.75")!!
            ),
            Account(
                id = "archived",
                name = "Archived account",
                accountType = AccountType.E_WALLET,
                currentLedgerBalance = Money.parse("99.00")!!,
                isArchived = true
            )
        )

        composeRule.activity.setContent {
            RalLedgerTheme {
                Surface { AccountBalancesSummary(accounts) }
            }
        }

        composeRule.onNodeWithText("Account balances").assertIsDisplayed()
        composeRule.onNodeWithText("Daily wallet").assertIsDisplayed()
        composeRule.onNodeWithText(Money.parse("1250.00")!!.format("PHP")).assertIsDisplayed()
        composeRule.onNodeWithText("Savings account").assertIsDisplayed()
        composeRule.onNodeWithText(Money.parse("20450.75")!!.format("PHP")).assertIsDisplayed()
        composeRule.onAllNodesWithText("Archived account").assertCountEquals(0)
    }
}

private fun ComposeContentTestRule.tapCalculatorKey(label: String) {
    onNode(hasText(label) and hasClickAction()).performClick()
}
