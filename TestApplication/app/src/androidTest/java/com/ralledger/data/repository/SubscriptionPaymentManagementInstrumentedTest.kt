package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.usecase.SubscriptionSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionPaymentManagementInstrumentedTest {
    @Test
    fun paymentsPostToLedgerAdvanceAndReverseSchedulePreserveHistoryAndBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(
                AccountEntity(
                    id = RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID,
                    name = "Opening Equity",
                    institution = "System",
                    accountType = AccountType.EQUITY.name,
                    currency = "PHP",
                    openingBalanceCentavos = 0L,
                    iconName = "account_balance",
                    colorHex = "#9E9E9E",
                    createdAt = 1L,
                    isArchived = true,
                    includeInDashboardTotals = false,
                    lastReconciledAt = null
                )
            )
            source.categoryDao().insertCategoriesForRestore(
                listOf(
                    CategoryEntity("streaming", "Streaming", CategoryType.EXPENSE.name, "subscriptions", "#C62828", false),
                    CategoryEntity("entertainment", "Entertainment", CategoryType.EXPENSE.name, "movie", "#6A1B9A", false)
                )
            )
            val repository = RalLedgerRepository(source)
            val now = System.currentTimeMillis()
            val originalDueDate = now - 60L * DAY_MILLIS
            val firstPaymentDate = now - 4L * DAY_MILLIS
            val secondPaymentDate = now - 2L * DAY_MILLIS
            repository.createAccount(
                Account(
                    id = "subscription-wallet",
                    name = "Daily wallet",
                    accountType = AccountType.E_WALLET,
                    openingBalance = Money(100_000L),
                    createdAt = now - 90L * DAY_MILLIS
                )
            )
            val subscription = Subscription(
                id = "subscription-music",
                name = "Music service",
                merchant = "Synthetic provider",
                billingAmount = Money(1_299L),
                billingFrequency = BillingFrequency.MONTHLY,
                assignedAccountId = "subscription-wallet",
                nextBillingDate = originalDueDate,
                notes = "Test plan"
            )
            repository.createSubscription(subscription)

            val firstJournalId = repository.recordSubscriptionPayment(
                subscription.id, Money(1_299L), "streaming", firstPaymentDate, "First period"
            )
            val afterFirst = source.subscriptionDao().getSubscriptionById(subscription.id)!!
            assertEquals(firstPaymentDate, afterFirst.lastPaymentDate)
            val expectedAfterFirst = SubscriptionSchedule.nextBillingDateAfterPayment(
                originalDueDate, firstPaymentDate, BillingFrequency.MONTHLY
            )
            assertEquals(expectedAfterFirst, afterFirst.nextBillingDate)

            val secondJournalId = repository.recordSubscriptionPayment(
                subscription.id, Money(1_499L), "streaming", secondPaymentDate, "Second period"
            )
            val afterSecond = source.subscriptionDao().getSubscriptionById(subscription.id)!!
            assertEquals(secondPaymentDate, afterSecond.lastPaymentDate)
            assertTrue(afterSecond.nextBillingDate > afterFirst.nextBillingDate)
            assertTrue(runCatching { repository.reverseTransaction(firstJournalId) }.isFailure)

            val posted = repository.transactionsFlow.first().filter { it.transactionType == TransactionType.SUBSCRIPTION_PAYMENT }
            assertEquals(2, posted.size)
            assertEquals(Money(97_202L), repository.getAccountById("subscription-wallet")!!.currentLedgerBalance)
            repository.updateSubscription(
                subscription.copy(
                    name = "Music service plus",
                    billingAmount = Money(1_599L),
                    nextBillingDate = afterSecond.nextBillingDate
                )
            )
            assertEquals(2, source.subscriptionPaymentDao().getAllPayments().size)

            repository.mergeCategory("streaming", "entertainment")
            assertTrue(source.subscriptionPaymentDao().getAllPayments().all { it.expenseCategoryId == "entertainment" })
            assertTrue(runCatching { repository.deleteUnusedCategory("entertainment") }.isFailure)

            repository.reverseTransaction(secondJournalId)
            var restoredSchedule = source.subscriptionDao().getSubscriptionById(subscription.id)!!
            assertEquals(firstPaymentDate, restoredSchedule.lastPaymentDate)
            assertEquals(expectedAfterFirst, restoredSchedule.nextBillingDate)
            repository.reverseTransaction(firstJournalId)
            restoredSchedule = source.subscriptionDao().getSubscriptionById(subscription.id)!!
            assertNull(restoredSchedule.lastPaymentDate)
            assertEquals(originalDueDate, restoredSchedule.nextBillingDate)
            assertEquals(Money(100_000L), repository.getAccountById("subscription-wallet")!!.currentLedgerBalance)
            assertTrue(source.subscriptionPaymentDao().getAllPayments().all { it.reversedAt != null })
            assertTrue(repository.transactionsFlow.first().filter { it.transactionType == TransactionType.SUBSCRIPTION_PAYMENT }.all { it.isReversed })

            assertTrue(runCatching { repository.deleteSubscription(subscription) }.isFailure)
            repository.setSubscriptionArchived(subscription.id, true)
            repository.setSubscriptionArchived(subscription.id, false)
            assertEquals(2, repository.subscriptionsFlow.first().single().payments.size)

            backup = BackupRepository(source).createEncryptedBackup("subscription-payment-test-password")
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, "subscription-payment-test-password")
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(2, preview.subscriptionPayments)
            restore.restorePreparedBackup()
            assertEquals(source.subscriptionPaymentDao().getAllPayments(), destination.subscriptionPaymentDao().getAllPayments())
            assertEquals(source.subscriptionDao().getAllSubscriptions(), destination.subscriptionDao().getAllSubscriptions())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertFalse(source.subscriptionPaymentDao().getAllPayments().isEmpty())
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
