package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ralledger.data.local.entity.JournalEntryEntity
import com.ralledger.data.local.entity.JournalLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalLines(lines: List<JournalLineEntity>)

    @Transaction
    suspend fun insertJournalEntryWithLines(entry: JournalEntryEntity, lines: List<JournalLineEntity>) {
        insertJournalEntry(entry)
        insertJournalLines(lines)
    }

    @Query("SELECT * FROM journal_entries ORDER BY id ASC")
    suspend fun getAllJournalEntries(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_lines ORDER BY id ASC")
    suspend fun getAllJournalLines(): List<JournalLineEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalEntries(entries: List<JournalEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalLinesForRestore(lines: List<JournalLineEntity>)

    @Query("DELETE FROM journal_lines")
    suspend fun clearJournalLines()

    @Query("DELETE FROM journal_entries")
    suspend fun clearJournalEntries()

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllJournalEntriesFlow(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalEntryById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE reversesEntryId = :entryId LIMIT 1")
    suspend fun getReversalForEntry(entryId: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :entryId")
    suspend fun getLinesForJournalEntry(entryId: String): List<JournalLineEntity>

    @Query("""
        SELECT accountId, lineType, SUM(amountCentavos) as totalCentavos 
        FROM journal_lines 
        GROUP BY accountId, lineType
    """)
    fun getAccountBalanceAggregatesFlow(): Flow<List<AccountBalanceAggregate>>

    @Query("""
        SELECT accountId, lineType, SUM(amountCentavos) as totalCentavos 
        FROM journal_lines 
        WHERE accountId = :accountId 
        GROUP BY lineType
    """)
    suspend fun getBalanceAggregateForAccount(accountId: String): List<AccountBalanceAggregate>

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteJournalEntry(entryId: String)
}
