package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ralledger.data.local.entity.ReconciliationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReconciliationDao {
    @Query("SELECT * FROM reconciliations WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getReconciliationsForAccountFlow(accountId: String): Flow<List<ReconciliationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliation(reconciliation: ReconciliationEntity)
}
