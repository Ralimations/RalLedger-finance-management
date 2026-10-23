package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.SubscriptionPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionPaymentDao {
    @Query("SELECT * FROM subscription_payments ORDER BY paymentDate DESC, id ASC")
    fun getPaymentsFlow(): Flow<List<SubscriptionPaymentEntity>>

    @Query("SELECT * FROM subscription_payments ORDER BY id ASC")
    suspend fun getAllPayments(): List<SubscriptionPaymentEntity>

    @Query("SELECT * FROM subscription_payments WHERE id = :id")
    suspend fun getPaymentById(id: String): SubscriptionPaymentEntity?

    @Query("SELECT * FROM subscription_payments WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getPaymentByJournalEntry(journalEntryId: String): SubscriptionPaymentEntity?

    @Query("SELECT * FROM subscription_payments WHERE subscriptionId = :subscriptionId AND reversedAt IS NULL ORDER BY paymentDate DESC, id DESC LIMIT 1")
    suspend fun getLatestActivePayment(subscriptionId: String): SubscriptionPaymentEntity?

    @Query("SELECT COUNT(*) FROM subscription_payments WHERE subscriptionId = :subscriptionId")
    suspend fun countPayments(subscriptionId: String): Long

    @Query("SELECT COUNT(*) FROM subscription_payments WHERE expenseCategoryId = :categoryId")
    suspend fun countPaymentsForCategory(categoryId: String): Long

    @Query("UPDATE subscription_payments SET expenseCategoryId = :targetId WHERE expenseCategoryId = :sourceId")
    suspend fun reassignCategory(sourceId: String, targetId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: SubscriptionPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayments(payments: List<SubscriptionPaymentEntity>)

    @Update
    suspend fun updatePayment(payment: SubscriptionPaymentEntity)

    @Query("DELETE FROM subscription_payments")
    suspend fun clearPayments()
}
