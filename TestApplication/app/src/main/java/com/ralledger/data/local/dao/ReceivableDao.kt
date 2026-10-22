package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.ReceivableEntity
import com.ralledger.data.local.entity.ReceivablePaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceivableDao {
    @Query("SELECT * FROM receivables ORDER BY recognitionDate DESC")
    fun getAllReceivablesFlow(): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE id = :id")
    suspend fun getReceivableById(id: String): ReceivableEntity?

    @Query("SELECT * FROM receivable_payments WHERE receivableId = :receivableId ORDER BY paymentDate DESC")
    suspend fun getPaymentsForReceivable(receivableId: String): List<ReceivablePaymentEntity>

    @Query("SELECT * FROM receivable_payments ORDER BY paymentDate DESC")
    fun getAllPaymentsFlow(): Flow<List<ReceivablePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivable(receivable: ReceivableEntity)

    @Update
    suspend fun updateReceivable(receivable: ReceivableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: ReceivablePaymentEntity)
}
