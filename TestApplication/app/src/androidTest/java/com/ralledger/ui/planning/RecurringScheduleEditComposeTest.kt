package com.ralledger.ui.planning

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionRepeatFrequency
import com.ralledger.ui.theme.RalLedgerTheme
import com.ralledger.testing.markWelcomeCompleteForMainActivityTest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class RecurringScheduleEditComposeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private var database: RalLedgerDatabase? = null
    private lateinit var repository: RalLedgerRepository
    private var scheduleId: String = ""

    private val prepareApp = object : ExternalResource() {
        override fun before() {
            markWelcomeCompleteForMainActivityTest()
            val application = InstrumentationRegistry.getInstrumentation().targetContext
                .applicationContext as RalLedgerApplication
            runBlocking { application.container.appLockPreferences.setEnabled(false) }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
            val testDatabase = requireNotNull(database)
            repository = RalLedgerRepository(testDatabase)
            runBlocking {
                seed(testDatabase)
                repository.recordExpense(
                    accountId = ACCOUNT_ID,
                    categoryId = FOOD_CATEGORY_ID,
                    amount = Money(15_000L),
                    description = "Coffee",
                    timestamp = Instant.parse("2026-10-01T01:30:00Z").toEpochMilli(),
                    submissionId = "recurring-edit-ui-original",
                    repeatFrequency = TransactionRepeatFrequency.MONTHLY
                )
                scheduleId = testDatabase.recurringTransactionDao().getAll().single().id
            }
        }

        override fun after() {
            database?.close()
            database = null
            val application = InstrumentationRegistry.getInstrumentation().targetContext
                .applicationContext as RalLedgerApplication
            runBlocking { application.container.appLockPreferences.setEnabled(false) }
        }
    }

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(prepareApp).around(composeRule)

    @Test
    fun editDialogUpdatesFutureTemplateWithoutRewritingPostedTransaction() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                RalLedgerTheme {
                    RecurringTransactionsScreen(
                        viewModel = RecurringTransactionsViewModel(repository),
                        onRecordNext = {}
                    )
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Coffee").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Edit recurring transaction").assertIsDisplayed()
        composeRule.onNodeWithText("Amount (PHP)").performTextReplacement("185.00")
        composeRule.onNodeWithText("Description").performScrollTo().performTextReplacement("Weekly groceries")
        composeRule.onNodeWithText("Notes").performScrollTo().performTextReplacement("Use the household card")
        composeRule.onNodeWithText("Save changes").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Weekly groceries").fetchSemanticsNodes().isNotEmpty()
        }
        val testDatabase = requireNotNull(database)
        val updated = runBlocking { testDatabase.recurringTransactionDao().getById(scheduleId)!! }
        val posted = runBlocking { testDatabase.journalDao().getJournalEntryById("recurring-edit-ui-original")!! }

        assertEquals(18_500L, updated.amountCentavos)
        assertEquals("Weekly groceries", updated.description)
        assertEquals("Use the household card", updated.notes)
        assertEquals(15_000L, posted.amountCentavos)
        assertEquals("Coffee", posted.description)
    }

    private suspend fun seed(database: RalLedgerDatabase) {
        database.accountDao().insertAccount(
            AccountEntity(
                id = ACCOUNT_ID,
                name = "Wallet",
                institution = "",
                accountType = AccountType.E_WALLET.name,
                currency = "PHP",
                openingBalanceCentavos = 0L,
                iconName = "account_balance_wallet",
                colorHex = "#1565C0",
                createdAt = 1L,
                isArchived = false,
                includeInDashboardTotals = true,
                lastReconciledAt = null
            )
        )
        database.categoryDao().insertCategory(
            CategoryEntity(FOOD_CATEGORY_ID, "Food", CategoryType.EXPENSE.name, "restaurant", "#FF5722", false)
        )
    }

    private companion object {
        const val ACCOUNT_ID = "recurring-edit-test-wallet"
        const val FOOD_CATEGORY_ID = "recurring-edit-test-food"
    }
}
