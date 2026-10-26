package com.ralledger.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.StartupState
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.CounterpartyKind
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.TransactionType
import com.ralledger.testing.markWelcomeCompleteForMainActivityTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.util.UUID

class QuickAddTransactionComposeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val application: RalLedgerApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RalLedgerApplication
    private val sourceId = "quick-add-compose-source-${UUID.randomUUID()}"
    private val destinationId = "quick-add-compose-destination-${UUID.randomUUID()}"
    private val sourceName = "QA source ${sourceId.takeLast(6)}"
    private val destinationName = "QA destination ${destinationId.takeLast(6)}"
    private val tagName = "QA label ${sourceId.takeLast(6)}"
    private val counterpartyName = "QA merchant ${sourceId.takeLast(6)}"
    private val templateName = "QA template ${sourceId.takeLast(6)}"
    private val expenseDescription = "Coffee from saved template"
    private val expenseNotes = "Review before posting"
    private val transferDescription = "QA transfer ${sourceId.takeLast(6)}"
    private val transferNotes = "Fee and notes save check"

    private lateinit var expenseCategory: com.ralledger.domain.model.Category
    private lateinit var tagId: String
    private lateinit var templateId: String
    private var previousAppLockEnabled = false

    private val prepareApp = object : ExternalResource() {
        override fun before() {
            markWelcomeCompleteForMainActivityTest()
            runBlocking {
                val startup = application.startupState.first { it !is StartupState.Recovering }
                assertTrue("Application startup recovery must succeed", startup is StartupState.Ready)
                val repository = application.container.repository
                assumeTrue(
                    "Run write coverage only against an empty test profile",
                    repository.accountsFlow.first().isEmpty() && repository.transactionsFlow.first().isEmpty()
                )
                previousAppLockEnabled = application.container.appLockPreferences.enabled.first()
                application.container.appLockPreferences.setEnabled(false)
                repository.createAccount(
                    Account(
                        id = sourceId,
                        name = sourceName,
                        accountType = AccountType.PHYSICAL_WALLET,
                        openingBalance = Money.parse("500.00")!!
                    )
                )
                repository.createAccount(
                    Account(id = destinationId, name = destinationName, accountType = AccountType.E_WALLET)
                )
                expenseCategory = repository.categoriesFlow.first { categories ->
                    categories.any { it.type == CategoryType.EXPENSE && !it.isArchived }
                }.first { it.type == CategoryType.EXPENSE && !it.isArchived }
                tagId = repository.createTransactionTag(tagName, "#1565C0")
                repository.createCounterparty(counterpartyName, CounterpartyKind.BOTH)
                templateId = repository.createTransactionTemplate(
                    TransactionTemplate(
                        id = "ignored-template-id",
                        name = templateName,
                        transactionType = TransactionType.EXPENSE,
                        amount = Money.parse("5.00")!!,
                        accountId = sourceId,
                        categoryId = expenseCategory.id,
                        counterparty = counterpartyName,
                        description = expenseDescription,
                        notes = expenseNotes,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        override fun after() {
            runBlocking {
                application.container.appLockPreferences.setEnabled(previousAppLockEnabled)
                if (::templateId.isInitialized) {
                    application.container.repository.deleteTransactionTemplate(templateId)
                }
            }
        }
    }

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(prepareApp).around(composeRule)

    @Test
    fun templatesTagsAndTransferDetailsPersistThroughQuickAdd() {
        val repository = application.container.repository
        val entryIdsBeforeExpense = runBlocking { repository.transactionsFlow.first().map { it.journalEntryId }.toSet() }
        val expenseStartedAt = System.currentTimeMillis()

        composeRule.onNodeWithContentDescription("Quick actions").performClick()
        composeRule.onNodeWithText("Expense").performClick()
        composeRule.onNodeWithText("Templates").performClick()
        composeRule.onNodeWithText(templateName).assertIsDisplayed()
        composeRule.onNodeWithText("Use").performClick()

        composeRule.onNodeWithTag("quick-add-amount").assertTextContains("5.00")
        composeRule.onNodeWithTag("quick-add-description").performScrollTo().assertTextContains(expenseDescription)
        composeRule.onNodeWithTag("quick-add-notes").performScrollTo().assertTextContains(expenseNotes)
        assertEquals(
            "Selecting a saved template must only prefill the form, not post a transaction",
            entryIdsBeforeExpense,
            runBlocking { repository.transactionsFlow.first().map { it.journalEntryId }.toSet() }
        )

        composeRule.onNodeWithText(tagName).performScrollTo().performClick()
        composeRule.onNodeWithText("Save transaction").performScrollTo().performClick()
        composeRule.waitForIdle()

        val savedExpense = runBlocking {
            withTimeout(10_000L) {
                repository.transactionsFlow.first { rows ->
                    rows.any {
                        it.transactionType == TransactionType.EXPENSE &&
                            it.amount == Money.parse("5.00") &&
                            it.description == expenseDescription &&
                            it.notes == expenseNotes &&
                            it.tagIds == setOf(tagId) &&
                            it.timestamp >= expenseStartedAt
                    }
                }.first {
                    it.transactionType == TransactionType.EXPENSE &&
                        it.amount == Money.parse("5.00") &&
                        it.description == expenseDescription &&
                        it.notes == expenseNotes &&
                        it.tagIds == setOf(tagId) &&
                        it.timestamp >= expenseStartedAt
                }
            }
        }
        assertEquals(listOf(tagName), savedExpense.tagNames)
        assertEquals(counterpartyName, savedExpense.counterparty)
        assertEquals(sourceId, savedExpense.accountId)
        assertEquals(expenseCategory.id, savedExpense.categoryId)

        val entriesBeforeExpenseRetry = runBlocking { repository.transactionsFlow.first().size }
        runBlocking {
            repository.recordExpense(
                sourceId,
                expenseCategory.id,
                Money.parse("5.00")!!,
                description = expenseDescription,
                counterparty = counterpartyName,
                notes = expenseNotes,
                timestamp = savedExpense.timestamp,
                tagIds = setOf(tagId),
                submissionId = savedExpense.journalEntryId
            )
        }
        assertEquals(entriesBeforeExpenseRetry, runBlocking { repository.transactionsFlow.first().size })
        assertEquals(
            Money.parse("495.00"),
            runBlocking { repository.accountsFlow.first().first { it.id == sourceId }.currentLedgerBalance }
        )
        assertTrue(
            "Reusing a saved submission ID for different data must be rejected",
            runCatching {
                runBlocking {
                    repository.recordExpense(
                        sourceId,
                        expenseCategory.id,
                        Money.parse("6.00")!!,
                        description = expenseDescription,
                        counterparty = counterpartyName,
                        notes = expenseNotes,
                        timestamp = savedExpense.timestamp,
                        tagIds = setOf(tagId),
                        submissionId = savedExpense.journalEntryId
                    )
                }
            }.isFailure
        )

        val transferStartedAt = System.currentTimeMillis()
        composeRule.onNodeWithContentDescription("Quick actions").performClick()
        composeRule.onNodeWithText("Transfer").performClick()
        composeRule.onNodeWithTag("quick-add-account").performClick()
        composeRule.onNodeWithTag("quick-add-account-option-$sourceId").performClick()
        composeRule.onNodeWithTag("quick-add-destination-account").performClick()
        composeRule.onNodeWithTag("quick-add-destination-option-$destinationId").performClick()
        composeRule.onNodeWithTag("quick-add-amount").performScrollTo().performTextInput("100.00")
        composeRule.onNodeWithText("Add details (optional)").performClick()
        composeRule.onNodeWithTag("quick-add-transfer-fee").performScrollTo().performTextInput("10.00")
        composeRule.onNodeWithText(expenseCategory.name).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("quick-add-description")
            .performScrollTo()
            .performTextInput(transferDescription)
        composeRule.onNodeWithTag("quick-add-notes").performScrollTo().performTextInput(transferNotes)
        composeRule.onNodeWithTag("quick-add-description").assertTextContains(transferDescription)
        composeRule.onNodeWithTag("quick-add-notes").assertTextContains(transferNotes)
        composeRule.onNodeWithText("Save transaction").performScrollTo().performClick()
        composeRule.waitForIdle()

        val savedTransfer = runBlocking {
            withTimeout(10_000L) {
                repository.transactionsFlow.first { rows ->
                    rows.any { it.transactionType == TransactionType.TRANSFER && it.description == transferDescription }
                }.first { it.transactionType == TransactionType.TRANSFER && it.description == transferDescription }
            }
        }
        assertEquals(Money.parse("100.00"), savedTransfer.amount)
        assertEquals(Money.parse("10.00"), savedTransfer.fee)
        assertEquals(sourceId, savedTransfer.accountId)
        assertEquals(destinationId, savedTransfer.destinationAccountId)
        assertEquals(expenseCategory.id, savedTransfer.categoryId)
        assertEquals(transferNotes, savedTransfer.notes)
        assertTrue(savedTransfer.timestamp >= transferStartedAt)

        val entriesBeforeTransferRetry = runBlocking { repository.transactionsFlow.first().size }
        runBlocking {
            repository.recordTransfer(
                sourceId,
                destinationId,
                Money.parse("100.00")!!,
                fee = Money.parse("10.00")!!,
                feeCategoryId = expenseCategory.id,
                description = transferDescription,
                notes = transferNotes,
                timestamp = savedTransfer.timestamp,
                submissionId = savedTransfer.journalEntryId
            )
        }
        assertEquals(entriesBeforeTransferRetry, runBlocking { repository.transactionsFlow.first().size })

        val balances = runBlocking {
            withTimeout(10_000L) {
                repository.accountsFlow.first { accounts ->
                    accounts.any { it.id == sourceId && it.currentLedgerBalance == Money.parse("385.00") } &&
                        accounts.any { it.id == destinationId && it.currentLedgerBalance == Money.parse("100.00") }
                }
            }
        }
        assertEquals(Money.parse("385.00"), balances.first { it.id == sourceId }.currentLedgerBalance)
        assertEquals(Money.parse("100.00"), balances.first { it.id == destinationId }.currentLedgerBalance)
    }
}
