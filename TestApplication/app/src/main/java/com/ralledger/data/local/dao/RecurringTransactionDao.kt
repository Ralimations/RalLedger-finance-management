package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.RecurringTransactionEntity
import com.ralledger.data.local.entity.RecurringTransactionOccurrenceEntity
import com.ralledger.data.local.entity.RecurringTransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY status ASC, nextOccurrenceTimestamp ASC, id ASC")
    fun getAllFlow(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY id ASC")
    suspend fun getAll(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: String): RecurringTransactionEntity?

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :accountId")
    suspend fun countAccountReferences(accountId: String): Long

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("UPDATE recurring_transactions SET categoryId = :targetId, updatedAt = :updatedAt WHERE categoryId = :sourceId")
    suspend fun reassignCategory(sourceId: String, targetId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: RecurringTransactionEntity)

    @Update
    suspend fun update(item: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTags(tags: List<RecurringTransactionTagEntity>)

    @Query("SELECT * FROM recurring_transaction_tags ORDER BY recurringTransactionId ASC, tagId ASC")
    fun getAllTagsFlow(): Flow<List<RecurringTransactionTagEntity>>

    @Query("SELECT * FROM recurring_transaction_tags ORDER BY recurringTransactionId ASC, tagId ASC")
    suspend fun getAllTags(): List<RecurringTransactionTagEntity>

    @Query("SELECT tagId FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringTransactionId")
    suspend fun getTagIds(recurringTransactionId: String): List<String>

    @Query("DELETE FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringTransactionId")
    suspend fun clearTags(recurringTransactionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOccurrence(occurrence: RecurringTransactionOccurrenceEntity)

    @Query("SELECT * FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId ORDER BY occurrenceIndex DESC")
    fun getOccurrencesFlow(recurringTransactionId: String): Flow<List<RecurringTransactionOccurrenceEntity>>

    @Query("SELECT * FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId ORDER BY occurrenceIndex ASC")
    suspend fun getOccurrences(recurringTransactionId: String): List<RecurringTransactionOccurrenceEntity>

    @Query("SELECT * FROM recurring_transaction_occurrences ORDER BY recurringTransactionId ASC, occurrenceIndex ASC")
    suspend fun getAllOccurrences(): List<RecurringTransactionOccurrenceEntity>

    @Query("SELECT * FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId AND occurrenceIndex = :occurrenceIndex")
    suspend fun getOccurrence(recurringTransactionId: String, occurrenceIndex: Long): RecurringTransactionOccurrenceEntity?

    @Query("SELECT COUNT(*) FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId")
    suspend fun countOccurrences(recurringTransactionId: String): Long

    @Query("DELETE FROM recurring_transaction_occurrences")
    suspend fun clearOccurrences()

    @Query("DELETE FROM recurring_transaction_tags")
    suspend fun clearAllTags()

    @Query("DELETE FROM recurring_transactions")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(items: List<RecurringTransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTagsForRestore(items: List<RecurringTransactionTagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOccurrencesForRestore(items: List<RecurringTransactionOccurrenceEntity>)
}
