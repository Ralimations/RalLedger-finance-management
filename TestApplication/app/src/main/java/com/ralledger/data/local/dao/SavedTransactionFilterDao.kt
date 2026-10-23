package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.SavedTransactionFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTransactionFilterDao {
    @Query("SELECT * FROM saved_transaction_filters ORDER BY name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<SavedTransactionFilterEntity>>

    @Query("SELECT * FROM saved_transaction_filters ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<SavedTransactionFilterEntity>

    @Query("SELECT * FROM saved_transaction_filters WHERE id = :id")
    suspend fun getById(id: String): SavedTransactionFilterEntity?

    @Query("SELECT * FROM saved_transaction_filters WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): SavedTransactionFilterEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(filter: SavedTransactionFilterEntity)

    @Update
    suspend fun update(filter: SavedTransactionFilterEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(filters: List<SavedTransactionFilterEntity>)

    @Query("DELETE FROM saved_transaction_filters WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_transaction_filters")
    suspend fun clear()
}
