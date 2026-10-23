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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsGoalManagementInstrumentedTest {
    @Test
    fun savingsGoalLifecycleReservesExistingCashAndEncryptedBackupPreservesHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(
                AccountEntity("cash", "Everyday account", "", AccountType.E_WALLET.name, "PHP", 0L,
                    "account_balance_wallet", "#1565C0", 1L, false, true, null)
            )
            source.categoryDao().insertCategory(
                CategoryEntity("income", "Pay", CategoryType.INCOME.name, "payments", "#2E7D32", false)
            )
            val repository = RalLedgerRepository(source)
            repository.recordIncome("cash", "income", Money(100_000L), "Synthetic opening cash")
            val originalBalance = repository.getAccountById("cash")!!.currentLedgerBalance
            val originalJournal = source.journalDao().getAllJournalEntries()

            val goalId = repository.createSavingsGoal("Laptop", Money(80_000L), null, "Work equipment")
            repository.allocateToSavingsGoal(goalId, Money(40_000L), date = 100L, note = "First reserve")
            assertTrue(runCatching { repository.allocateToSavingsGoal(goalId, Money(60_001L)) }.isFailure)
            assertEquals(originalBalance, repository.getAccountById("cash")!!.currentLedgerBalance)
            assertEquals(originalJournal, source.journalDao().getAllJournalEntries())

            var summary = repository.dashboardSummaryFlow.first()
            assertEquals(Money(100_000L), summary.liquidCashAndBankBalances)
            assertEquals(Money(40_000L), summary.reservedSavings)
            assertEquals(Money(60_000L), summary.availableToSpendFunds)
            assertEquals(Money(100_000L), summary.totalAssets)
            assertEquals(Money(100_000L), summary.netWorth)

            repository.updateSavingsGoal(goalId, "Laptop fund", Money(90_000L), 1_800_000_000_000L, "Updated target")
            repository.releaseFromSavingsGoal(goalId, Money(10_000L), date = 200L, note = "Changed plans")
            repository.setSavingsGoalArchived(goalId, true)
            assertTrue(runCatching { repository.allocateToSavingsGoal(goalId, Money(1_000L)) }.isFailure)
            summary = repository.dashboardSummaryFlow.first()
            assertEquals(Money(30_000L), summary.reservedSavings)
            assertEquals(Money(70_000L), summary.availableToSpendFunds)
            assertTrue(runCatching { repository.deleteUnusedSavingsGoal(goalId) }.isFailure)
            repository.setSavingsGoalArchived(goalId, false)
            val goal = repository.savingsGoalsFlow.first().single()
            assertEquals("Laptop fund", goal.name)
            assertEquals(Money(30_000L), goal.allocatedAmount)
            assertEquals(2, goal.events.size)

            val unusedId = repository.createSavingsGoal("Unused goal", Money(50_000L), null, "")
            repository.deleteUnusedSavingsGoal(unusedId)
            assertTrue(repository.savingsGoalsFlow.first().none { it.id == unusedId })

            val password = "savings-goal-restore-password"
            backup = BackupRepository(source).createEncryptedBackup(password)
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(1, preview.savingsGoals)
            assertEquals(2, preview.savingsGoalEvents)
            restore.restorePreparedBackup()

            assertEquals(source.savingsGoalDao().getAllGoals(), destination.savingsGoalDao().getAllGoals())
            assertEquals(source.savingsGoalDao().getAllEvents(), destination.savingsGoalDao().getAllEvents())
            assertEquals(originalBalance, RalLedgerRepository(destination).getAccountById("cash")!!.currentLedgerBalance)
            assertEquals(originalJournal, destination.journalDao().getAllJournalEntries())
            assertEquals(Money(70_000L), RalLedgerRepository(destination).dashboardSummaryFlow.first().availableToSpendFunds)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }
}
