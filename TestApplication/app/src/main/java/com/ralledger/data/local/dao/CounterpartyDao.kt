package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.CounterpartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyDao {
    @Query("SELECT * FROM counterparties ORDER BY isArchived ASC, normalizedName ASC")
    fun getAllFlow(): Flow<List<CounterpartyEntity>>

    @Query("SELECT * FROM counterparties WHERE id = :id")
    suspend fun getById(id: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties ORDER BY id ASC")
    suspend fun getAll(): List<CounterpartyEntity>

    @Query("SELECT COUNT(*) FROM receivables WHERE counterpartyId = :id")
    suspend fun countReceivableReferences(id: String): Long

    @Query("SELECT COUNT(*) FROM expected_income WHERE counterpartyId = :id")
    suspend fun countExpectedIncomeReferences(id: String): Long

    @Query("SELECT COUNT(*) FROM debts WHERE counterpartyId = :id")
    suspend fun countDebtReferences(id: String): Long

    @Query("SELECT COUNT(*) FROM subscriptions WHERE counterpartyId = :id")
    suspend fun countSubscriptionReferences(id: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CounterpartyEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<CounterpartyEntity>)

    @Update
    suspend fun update(item: CounterpartyEntity)

    @Query("DELETE FROM counterparties WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM counterparties")
    suspend fun clear()
}
