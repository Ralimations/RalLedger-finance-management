package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.ReceivableEntity
import com.ralledger.data.local.entity.ReceivablePaymentEntity
import com.ralledger.data.local.entity.ReceivableWriteOffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceivableDao {
    @Query("SELECT * FROM receivables ORDER BY recognitionDate DESC")
    fun getAllReceivablesFlow(): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE id = :id")
    suspend fun getReceivableById(id: String): ReceivableEntity?

    @Query("SELECT * FROM receivables ORDER BY id ASC")
    suspend fun getAllReceivables(): List<ReceivableEntity>

    @Query("SELECT * FROM receivable_payments ORDER BY id ASC")
    suspend fun getAllPayments(): List<ReceivablePaymentEntity>

    @Query("SELECT COUNT(*) FROM receivable_payments WHERE destinationAccountId = :accountId")
    suspend fun countPaymentsForAccount(accountId: String): Long

    @Query("SELECT * FROM receivable_payments WHERE receivableId = :receivableId ORDER BY paymentDate DESC")
    suspend fun getPaymentsForReceivable(receivableId: String): List<ReceivablePaymentEntity>

    @Query("SELECT * FROM receivable_payments WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getPaymentByJournalEntryId(journalEntryId: String): ReceivablePaymentEntity?

    @Query("SELECT * FROM receivable_payments ORDER BY paymentDate DESC")
    fun getAllPaymentsFlow(): Flow<List<ReceivablePaymentEntity>>

    @Query("SELECT * FROM receivable_write_offs ORDER BY date DESC")
    fun getAllWriteOffsFlow(): Flow<List<ReceivableWriteOffEntity>>

    @Query("SELECT * FROM receivable_write_offs ORDER BY id ASC")
    suspend fun getAllWriteOffs(): List<ReceivableWriteOffEntity>

    @Query("SELECT * FROM receivable_write_offs WHERE receivableId = :receivableId ORDER BY date DESC")
    suspend fun getWriteOffsForReceivable(receivableId: String): List<ReceivableWriteOffEntity>

    @Query("SELECT * FROM receivable_write_offs WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getWriteOffByJournalEntryId(journalEntryId: String): ReceivableWriteOffEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceivable(receivable: ReceivableEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceivables(receivables: List<ReceivableEntity>)

    @Update
    suspend fun updateReceivable(receivable: ReceivableEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: ReceivablePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayments(payments: List<ReceivablePaymentEntity>)

    @Update
    suspend fun updatePayment(payment: ReceivablePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWriteOff(writeOff: ReceivableWriteOffEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWriteOffs(writeOffs: List<ReceivableWriteOffEntity>)

    @Update
    suspend fun updateWriteOff(writeOff: ReceivableWriteOffEntity)

    @Query("DELETE FROM receivable_payments")
    suspend fun clearPayments()

    @Query("DELETE FROM receivable_write_offs")
    suspend fun clearWriteOffs()

    @Query("DELETE FROM receivables")
    suspend fun clearReceivables()
}
