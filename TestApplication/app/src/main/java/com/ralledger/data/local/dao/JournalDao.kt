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

    @Query("SELECT * FROM journal_lines ORDER BY journalEntryId ASC, id ASC")
    fun getAllJournalLinesFlow(): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalEntryById(id: String): JournalEntryEntity?

    @Query("""
        SELECT COUNT(*) FROM journal_entries
        WHERE transactionType = :transactionType
          AND primaryAccountId = :primaryAccountId
          AND destinationAccountId IS :destinationAccountId
          AND categoryId IS :categoryId
          AND amountCentavos = :amountCentavos
          AND feeCentavos = :feeCentavos
          AND LOWER(TRIM(description)) = LOWER(TRIM(:description))
          AND LOWER(TRIM(counterparty)) = LOWER(TRIM(:counterparty))
          AND timestamp >= :dayStartInclusive
          AND timestamp < :nextDayExclusive
    """)
    suspend fun countMatchingImportDuplicate(
        transactionType: String,
        primaryAccountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        amountCentavos: Long,
        feeCentavos: Long,
        description: String,
        counterparty: String,
        dayStartInclusive: Long,
        nextDayExclusive: Long
    ): Long

    @Query("SELECT * FROM journal_entries WHERE transactionType = :transactionType AND primaryAccountId = :primaryAccountId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstEntryByTypeAndPrimaryAccount(transactionType: String, primaryAccountId: String): JournalEntryEntity?

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

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM journal_entries WHERE primaryAccountId = :accountId OR destinationAccountId = :accountId) +
            (SELECT COUNT(*) FROM journal_lines WHERE accountId = :accountId)
    """)
    suspend fun countAccountReferences(accountId: String): Long

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM journal_entries WHERE categoryId = :categoryId) +
            (SELECT COUNT(*) FROM journal_lines WHERE accountId = :categoryId)
    """)
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("UPDATE journal_entries SET categoryId = :targetCategoryId WHERE categoryId = :sourceCategoryId")
    suspend fun reassignEntryCategory(sourceCategoryId: String, targetCategoryId: String)

    @Query("UPDATE journal_lines SET accountId = :targetCategoryId WHERE accountId = :sourceCategoryId")
    suspend fun reassignCategoryLedgerLines(sourceCategoryId: String, targetCategoryId: String)

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteJournalEntry(entryId: String)
}
