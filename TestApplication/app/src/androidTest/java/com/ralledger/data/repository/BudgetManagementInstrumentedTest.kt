package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class BudgetManagementInstrumentedTest {
    @Test
    fun monthlyBudgetTracksExpensesAndSupportsEditArchiveRestoreAndSafeRemoval() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.accountDao().insertAccounts(
                listOf(testAccount("budget_wallet"), testAccount("budget_destination"))
            )
            database.categoryDao().insertCategories(
                listOf(
                    expenseCategory("budget_food", "Food"),
                    expenseCategory("budget_unused", "Unused")
                )
            )
            val repository = RalLedgerRepository(database)
            val period = YearMonth.now()
            val timestamp = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 15)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val budgetId = repository.createBudget("budget_food", period.year, period.monthValue, Money(10_000L))
            assertTrue(runCatching {
                repository.createBudget("budget_food", period.year, period.monthValue, Money(2_000L))
            }.isFailure)
            assertTrue(runCatching {
                repository.createBudget("missing_category", period.year, period.monthValue, Money(2_000L))
            }.isFailure)

            repository.recordExpense("budget_wallet", "budget_food", Money(6_000L), "Budgeted meal", timestamp = timestamp)
            repository.recordTransfer(
                sourceAccountId = "budget_wallet",
                destinationAccountId = "budget_destination",
                amount = Money(3_000L),
                fee = Money(500L),
                feeCategoryId = "budget_food",
                description = "Transfer with fee",
                timestamp = timestamp
            )
            var budget = repository.budgetsFlow.first().single { it.id == budgetId }
            assertEquals(Money(6_500L), budget.spent)
            assertEquals(Money(3_500L), budget.remaining)
            assertFalse(budget.isOverBudget)

            val expense = database.journalDao().getAllJournalEntries().single { it.description == "Budgeted meal" }
            repository.reverseTransaction(expense.id)
            budget = repository.budgetsFlow.first { rows -> rows.single { it.id == budgetId }.spent == Money(500L) }
                .single { it.id == budgetId }
            assertEquals(Money(500L), budget.spent)

            repository.updateBudget(budgetId, "budget_food", period.year, period.monthValue, Money(400L))
            budget = repository.budgetsFlow.first { rows -> rows.single { it.id == budgetId }.limit == Money(400L) }
                .single { it.id == budgetId }
            assertTrue(budget.isOverBudget)
            assertEquals(Money(100L), budget.overspent)

            repository.setBudgetArchived(budgetId, true)
            assertTrue(repository.budgetsFlow.first().single { it.id == budgetId }.isArchived)
            repository.setCategoryArchived("budget_food", true)
            assertTrue(runCatching { repository.setBudgetArchived(budgetId, false) }.isFailure)
            repository.setCategoryArchived("budget_food", false)
            repository.setBudgetArchived(budgetId, false)
            assertFalse(repository.budgetsFlow.first().single { it.id == budgetId }.isArchived)

            assertTrue(runCatching { repository.deleteUnusedCategory("budget_food") }.isFailure)
            repository.deleteBudget(budgetId)
            assertNull(database.budgetDao().getBudgetById(budgetId))
            repository.deleteUnusedCategory("budget_unused")
            assertNull(database.categoryDao().getCategoryById("budget_unused"))
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryMergeReassignsBudgetsAndRejectsPeriodCollisionsAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.categoryDao().insertCategories(
                listOf(
                    expenseCategory("budget_source", "Source"),
                    expenseCategory("budget_target", "Target")
                )
            )
            val repository = RalLedgerRepository(database)
            val period = YearMonth.now()
            val laterPeriod = period.plusMonths(1)
            val sourceBudgetId = repository.createBudget("budget_source", period.year, period.monthValue, Money(5_000L))
            repository.mergeCategory("budget_source", "budget_target")
            assertNull(database.categoryDao().getCategoryById("budget_source"))
            assertEquals("budget_target", database.budgetDao().getBudgetById(sourceBudgetId)?.categoryId)

            database.categoryDao().insertCategory(expenseCategory("budget_source_again", "Source again"))
            repository.createBudget("budget_target", laterPeriod.year, laterPeriod.monthValue, Money(4_000L))
            val collidingSource = repository.createBudget(
                "budget_source_again", laterPeriod.year, laterPeriod.monthValue, Money(6_000L)
            )
            assertTrue(runCatching { repository.mergeCategory("budget_source_again", "budget_target") }.isFailure)
            assertTrue(database.categoryDao().getCategoryById("budget_source_again") != null)
            assertEquals("budget_source_again", database.budgetDao().getBudgetById(collidingSource)?.categoryId)
        } finally {
            database.close()
        }
    }

    private fun testAccount(id: String) = AccountEntity(
        id = id,
        name = id,
        institution = "Synthetic",
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

    private fun expenseCategory(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = CategoryType.EXPENSE.name,
        iconName = "receipt",
        colorHex = "#C62828",
        isDefault = false
    )
}
