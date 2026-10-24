package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.PlannedPaymentEntity
import com.ralledger.data.local.entity.PlannedPaymentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPaymentDao {
    @Query("SELECT * FROM planned_payments ORDER BY dueDate ASC, createdAt DESC")
    fun getAllFlow(): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_payments ORDER BY id ASC")
    suspend fun getAll(): List<PlannedPaymentEntity>

    @Query("SELECT * FROM planned_payments WHERE id = :id")
    suspend fun getById(id: String): PlannedPaymentEntity?

    @Query("SELECT * FROM planned_payment_records ORDER BY paymentDate DESC, id ASC")
    fun getAllRecordsFlow(): Flow<List<PlannedPaymentRecordEntity>>

    @Query("SELECT * FROM planned_payment_records ORDER BY paymentDate ASC, id ASC")
    suspend fun getAllRecords(): List<PlannedPaymentRecordEntity>

    @Query("SELECT * FROM planned_payment_records WHERE plannedPaymentId = :plannedPaymentId ORDER BY paymentDate DESC, id ASC")
    suspend fun getRecordsForPayment(plannedPaymentId: String): List<PlannedPaymentRecordEntity>

    @Query("SELECT * FROM planned_payment_records WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getRecordByJournalEntry(journalEntryId: String): PlannedPaymentRecordEntity?

    @Query("SELECT * FROM planned_payment_records WHERE id = :id")
    suspend fun getRecordById(id: String): PlannedPaymentRecordEntity?

    @Query("SELECT COUNT(*) FROM planned_payment_records WHERE plannedPaymentId = :plannedPaymentId")
    suspend fun countRecords(plannedPaymentId: String): Long

    @Query("SELECT COUNT(*) FROM planned_payment_records WHERE plannedPaymentId = :plannedPaymentId AND reversedAt IS NULL")
    suspend fun countActiveRecords(plannedPaymentId: String): Long

    @Query("SELECT COUNT(*) FROM planned_payments WHERE expenseCategoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("SELECT COUNT(*) FROM planned_payments WHERE counterpartyId = :counterpartyId")
    suspend fun countCounterpartyReferences(counterpartyId: String): Long

    @Query("UPDATE planned_payments SET expenseCategoryId = :targetId WHERE expenseCategoryId = :sourceId")
    suspend fun reassignCategory(sourceId: String, targetId: String)

    @Query("UPDATE planned_payments SET counterpartyId = :targetId WHERE counterpartyId = :sourceId")
    suspend fun reassignCounterparty(sourceId: String, targetId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: PlannedPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(items: List<PlannedPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecords(items: List<PlannedPaymentRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(item: PlannedPaymentRecordEntity)

    @Update
    suspend fun update(item: PlannedPaymentEntity)

    @Update
    suspend fun updateRecord(item: PlannedPaymentRecordEntity)

    @Query("DELETE FROM planned_payments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM planned_payment_records")
    suspend fun clearRecords()

    @Query("DELETE FROM planned_payments")
    suspend fun clear()
}
