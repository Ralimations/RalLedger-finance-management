package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.JournalEntryTagEntity
import com.ralledger.data.local.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTagDao {
    @Query("SELECT * FROM transaction_tags ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun getAllTagsFlow(): Flow<List<TransactionTagEntity>>

    @Query("SELECT * FROM transaction_tags ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    suspend fun getAllTags(): List<TransactionTagEntity>

    @Query("SELECT * FROM transaction_tags WHERE id = :id")
    suspend fun getTagById(id: String): TransactionTagEntity?

    @Query("SELECT * FROM transaction_tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getTagByNormalizedName(normalizedName: String): TransactionTagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TransactionTagEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTags(tags: List<TransactionTagEntity>)

    @Update
    suspend fun updateTag(tag: TransactionTagEntity)

    @Query("DELETE FROM transaction_tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("DELETE FROM transaction_tags")
    suspend fun clearTags()

    @Query("SELECT * FROM journal_entry_tags")
    fun getAllAssignmentsFlow(): Flow<List<JournalEntryTagEntity>>

    @Query("SELECT * FROM journal_entry_tags")
    suspend fun getAllAssignments(): List<JournalEntryTagEntity>

    @Query("SELECT tagId FROM journal_entry_tags WHERE journalEntryId = :entryId")
    suspend fun getTagIdsForEntry(entryId: String): List<String>

    @Query("SELECT COUNT(*) FROM journal_entry_tags WHERE tagId = :tagId")
    suspend fun countAssignments(tagId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssignments(assignments: List<JournalEntryTagEntity>)

    @Query("DELETE FROM journal_entry_tags WHERE journalEntryId = :entryId")
    suspend fun clearAssignmentsForEntry(entryId: String)

    @Query("DELETE FROM journal_entry_tags WHERE tagId = :tagId")
    suspend fun clearAssignmentsForTag(tagId: String)

    @Query("DELETE FROM journal_entry_tags")
    suspend fun clearAllAssignments()

    @Query("""
        DELETE FROM journal_entry_tags
        WHERE tagId = :sourceTagId
          AND journalEntryId IN (
              SELECT journalEntryId FROM journal_entry_tags WHERE tagId = :targetTagId
          )
    """)
    suspend fun deleteDuplicateAssignmentsForMerge(sourceTagId: String, targetTagId: String)

    @Query("UPDATE journal_entry_tags SET tagId = :targetTagId WHERE tagId = :sourceTagId")
    suspend fun reassignTag(sourceTagId: String, targetTagId: String)
}
