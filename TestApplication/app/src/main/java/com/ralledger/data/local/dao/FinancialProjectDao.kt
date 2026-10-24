package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.FinancialProjectEntity
import com.ralledger.data.local.entity.JournalEntryProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialProjectDao {
    @Query("SELECT * FROM financial_projects ORDER BY isArchived ASC, name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<FinancialProjectEntity>>

    @Query("SELECT * FROM financial_projects ORDER BY id ASC")
    suspend fun getAll(): List<FinancialProjectEntity>

    @Query("SELECT * FROM financial_projects WHERE id = :id")
    suspend fun getById(id: String): FinancialProjectEntity?

    @Query("SELECT * FROM financial_projects WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): FinancialProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: FinancialProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(projects: List<FinancialProjectEntity>)

    @Update
    suspend fun update(project: FinancialProjectEntity)

    @Query("DELETE FROM financial_projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM financial_projects")
    suspend fun clear()

    @Query("SELECT * FROM journal_entry_projects")
    fun getAssignmentsFlow(): Flow<List<JournalEntryProjectEntity>>

    @Query("SELECT * FROM journal_entry_projects ORDER BY journalEntryId ASC")
    suspend fun getAssignments(): List<JournalEntryProjectEntity>

    @Query("SELECT projectId FROM journal_entry_projects WHERE journalEntryId = :entryId")
    suspend fun getProjectIdForEntry(entryId: String): String?

    @Query("SELECT COUNT(*) FROM journal_entry_projects WHERE projectId = :projectId")
    suspend fun countAssignments(projectId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun assign(assignment: JournalEntryProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun assignAll(assignments: List<JournalEntryProjectEntity>)

    @Query("DELETE FROM journal_entry_projects WHERE journalEntryId = :entryId")
    suspend fun clearAssignment(entryId: String)

    @Query("DELETE FROM journal_entry_projects")
    suspend fun clearAssignments()

    @Query("UPDATE journal_entry_projects SET projectId = :targetId WHERE projectId = :sourceId")
    suspend fun reassignProject(sourceId: String, targetId: String)
}
