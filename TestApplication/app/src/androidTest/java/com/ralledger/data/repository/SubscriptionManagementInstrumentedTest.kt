package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionManagementInstrumentedTest {
    @Test
    fun subscriptionMetadataAndLifecyclePreservePaymentStateAndGuardDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        try {
            database.accountDao().insertAccount(
                AccountEntity(
                    id = "subscription_wallet",
                    name = "Wallet",
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
            )
            val repository = RalLedgerRepository(database)
            val initial = Subscription(
                id = "subscription_test",
                name = "Old name",
                merchant = "Old merchant",
                billingAmount = Money(1_500L),
                billingFrequency = BillingFrequency.MONTHLY,
                assignedAccountId = "subscription_wallet",
                nextBillingDate = 100L,
                notes = "Old note"
            )
            repository.createSubscription(initial)

            val stored = database.subscriptionDao().getSubscriptionById(initial.id)!!
            database.subscriptionDao().updateSubscription(stored.copy(lastPaymentDate = 50L))
            repository.updateSubscription(
                initial.copy(
                    name = "Updated name",
                    merchant = "Updated merchant",
                    billingAmount = Money(2_500L),
                    billingFrequency = BillingFrequency.QUARTERLY,
                    nextBillingDate = 300L,
                    notes = "Updated note"
                )
            )
            var updated = database.subscriptionDao().getSubscriptionById(initial.id)!!
            assertEquals("Updated name", updated.name)
            assertEquals("Updated merchant", updated.merchant)
            assertEquals(2_500L, updated.billingAmountCentavos)
            assertEquals("QUARTERLY", updated.billingFrequency)
            assertEquals(300L, updated.nextBillingDate)
            assertEquals("Updated note", updated.notes)
            assertEquals(50L, updated.lastPaymentDate)

            repository.setSubscriptionStatus(initial.id, SubscriptionStatus.PAUSED)
            assertEquals(SubscriptionStatus.PAUSED.name, database.subscriptionDao().getSubscriptionById(initial.id)?.status)
            repository.setSubscriptionStatus(initial.id, SubscriptionStatus.ACTIVE)
            assertEquals(SubscriptionStatus.ACTIVE.name, database.subscriptionDao().getSubscriptionById(initial.id)?.status)
            repository.setSubscriptionStatus(initial.id, SubscriptionStatus.CANCELLED)
            updated = database.subscriptionDao().getSubscriptionById(initial.id)!!
            assertEquals(SubscriptionStatus.CANCELLED.name, updated.status)
            assertNotNull(updated.cancellationDate)
            repository.setSubscriptionStatus(initial.id, SubscriptionStatus.ACTIVE)
            assertNull(database.subscriptionDao().getSubscriptionById(initial.id)?.cancellationDate)

            repository.setSubscriptionArchived(initial.id, true)
            assertTrue(database.subscriptionDao().getSubscriptionById(initial.id)!!.isArchived)
            assertTrue(runCatching { repository.setSubscriptionStatus(initial.id, SubscriptionStatus.PAUSED) }.isFailure)
            repository.setSubscriptionArchived(initial.id, false)
            assertFalse(database.subscriptionDao().getSubscriptionById(initial.id)!!.isArchived)
            assertTrue(runCatching { repository.deleteSubscription(initial.copy(id = initial.id)) }.isFailure)
            assertTrue(database.subscriptionDao().getSubscriptionById(initial.id) != null)

            val unused = initial.copy(id = "unused_subscription", name = "Unused")
            repository.createSubscription(unused)
            repository.deleteSubscription(unused)
            assertNull(database.subscriptionDao().getSubscriptionById(unused.id))
        } finally {
            database.close()
        }
    }
}
