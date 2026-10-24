package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.CustomReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomReportDao {
    @Query("SELECT * FROM custom_reports ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<CustomReportEntity>>

    @Query("SELECT * FROM custom_reports ORDER BY id ASC")
    suspend fun getAll(): List<CustomReportEntity>

    @Query("SELECT * FROM custom_reports WHERE id = :id")
    suspend fun getById(id: String): CustomReportEntity?

    @Query("SELECT * FROM custom_reports WHERE normalizedName = :name LIMIT 1")
    suspend fun getByNormalizedName(name: String): CustomReportEntity?

    @Query("SELECT COUNT(*) FROM custom_reports WHERE accountId = :accountId")
    suspend fun countAccountReferences(accountId: String): Long

    @Query("SELECT COUNT(*) FROM custom_reports WHERE categoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("SELECT COUNT(*) FROM custom_reports WHERE tagId = :tagId")
    suspend fun countTagReferences(tagId: String): Long

    @Query("UPDATE custom_reports SET categoryId = :targetId, updatedAt = :updatedAt WHERE categoryId = :sourceId")
    suspend fun reassignCategory(sourceId: String, targetId: String, updatedAt: Long)

    @Query("UPDATE custom_reports SET tagId = :targetId, updatedAt = :updatedAt WHERE tagId = :sourceId")
    suspend fun reassignTag(sourceId: String, targetId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: CustomReportEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reports: List<CustomReportEntity>)

    @Update
    suspend fun update(report: CustomReportEntity)

    @Query("DELETE FROM custom_reports WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_reports")
    suspend fun clear()
}
