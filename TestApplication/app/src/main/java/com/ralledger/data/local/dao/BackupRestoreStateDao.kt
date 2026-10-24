package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ralledger.data.local.entity.BackupRestoreStateEntity

@Dao
interface BackupRestoreStateDao {
    @Query("SELECT lastSuccessfulRestoreId FROM backup_restore_state WHERE id = 1")
    suspend fun getLastSuccessfulRestoreId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordSuccessfulRestore(state: BackupRestoreStateEntity)
}
