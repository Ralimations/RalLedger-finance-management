package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryManagementInstrumentedTest {
    @Test
    fun categoryLifecycle_preservesHistoryAndRestrictsUnsafeDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "wallet_category_test",
                    name = "Test wallet",
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
            val repository = RalLedgerRepository(database)
            repository.createCategory(
                Category("category_home", "Home", CategoryType.EXPENSE, colorHex = "#00695C")
            )
            repository.createCategory(
                Category("category_repairs", "Repairs", CategoryType.EXPENSE, parentCategoryId = "category_home")
            )
            repository.createCategory(Category("category_unused", "Gift income", CategoryType.INCOME))
            repository.recordExpense("wallet_category_test", "category_repairs", Money(1000L), "Kitchen repair")

            val originalEntry = database.journalDao().getAllJournalEntries().single()
            repository.updateCategoryMetadata(
                id = "category_repairs",
                name = "Home repairs",
                iconName = "home",
                colorHex = "#EF6C00",
                parentCategoryId = "category_home"
            )
            val renamed = database.categoryDao().getCategoryById("category_repairs")!!
            assertEquals("Home repairs", renamed.name)
            assertEquals("category_home", renamed.parentCategoryId)
            assertEquals("#EF6C00", renamed.colorHex)
            assertEquals(originalEntry, database.journalDao().getAllJournalEntries().single())

            assertTrue(runCatching { repository.setCategoryArchived("category_home", true) }.isFailure)
            repository.setCategoryArchived("category_repairs", true)
            assertTrue(runCatching {
                repository.recordExpense("wallet_category_test", "category_repairs", Money(100L), "Blocked archived category")
            }.isFailure)
            repository.setCategoryArchived("category_home", true)
            assertTrue(database.categoryDao().getCategoryById("category_home")!!.isArchived)
            repository.setCategoryArchived("category_home", false)
            repository.setCategoryArchived("category_repairs", false)

            assertTrue(runCatching { repository.deleteUnusedCategory("category_repairs") }.isFailure)
            assertTrue(runCatching { repository.deleteUnusedCategory("category_home") }.isFailure)
            repository.deleteUnusedCategory("category_unused")
            assertNull(database.categoryDao().getCategoryById("category_unused"))
            assertEquals(originalEntry, database.journalDao().getAllJournalEntries().single())
            assertFalse(database.categoryDao().getCategoryById("category_repairs")!!.isArchived)

            repository.createCategory(Category("income_work", "Work", CategoryType.INCOME))
            repository.createCategory(Category("income_video", "Video income", CategoryType.INCOME))
            repository.recordIncome("wallet_category_test", "income_video", Money(2000L), "Synthetic creator payment")
            val incomeEntryBefore = database.journalDao().getAllJournalEntries().single { it.description == "Synthetic creator payment" }
            val incomeLinesBefore = database.journalDao().getLinesForJournalEntry(incomeEntryBefore.id)
            repository.mergeCategory("income_video", "income_work")
            val incomeEntryAfter = database.journalDao().getJournalEntryById(incomeEntryBefore.id)!!
            val incomeLinesAfter = database.journalDao().getLinesForJournalEntry(incomeEntryBefore.id)
            assertEquals("income_work", incomeEntryAfter.categoryId)
            assertEquals(incomeEntryBefore.copy(categoryId = "income_work"), incomeEntryAfter)
            assertEquals(
                incomeLinesBefore.map { line -> if (line.accountId == "income_video") line.copy(accountId = "income_work") else line },
                incomeLinesAfter
            )
            assertNull(database.categoryDao().getCategoryById("income_video"))
        } finally {
            database.close()
        }
    }
}
