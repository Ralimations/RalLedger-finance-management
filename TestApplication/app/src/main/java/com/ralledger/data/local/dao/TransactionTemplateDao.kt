package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.TransactionTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTemplateDao {
    @Query("SELECT * FROM transaction_templates ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<TransactionTemplateEntity>>

    @Query("SELECT * FROM transaction_templates ORDER BY id ASC")
    suspend fun getAll(): List<TransactionTemplateEntity>

    @Query("SELECT * FROM transaction_templates WHERE id = :id")
    suspend fun getById(id: String): TransactionTemplateEntity?

    @Query("SELECT * FROM transaction_templates WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): TransactionTemplateEntity?

    @Query("SELECT COUNT(*) FROM transaction_templates WHERE accountId = :accountId OR destinationAccountId = :accountId")
    suspend fun countAccountReferences(accountId: String): Long

    @Query("SELECT COUNT(*) FROM transaction_templates WHERE categoryId = :categoryId OR feeCategoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("UPDATE transaction_templates SET categoryId = :targetId, updatedAt = :updatedAt WHERE categoryId = :sourceId")
    suspend fun reassignPrimaryCategory(sourceId: String, targetId: String, updatedAt: Long)

    @Query("UPDATE transaction_templates SET feeCategoryId = :targetId, updatedAt = :updatedAt WHERE feeCategoryId = :sourceId")
    suspend fun reassignFeeCategory(sourceId: String, targetId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: TransactionTemplateEntity)

    @Update
    suspend fun update(template: TransactionTemplateEntity)

    @Query("UPDATE transaction_templates SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("DELETE FROM transaction_templates WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(templates: List<TransactionTemplateEntity>)

    @Query("DELETE FROM transaction_templates")
    suspend fun clear()
}
