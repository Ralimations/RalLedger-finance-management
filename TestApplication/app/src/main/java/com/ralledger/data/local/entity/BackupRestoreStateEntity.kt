package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records the restore whose database replacement most recently committed. */
@Entity(tableName = "backup_restore_state")
data class BackupRestoreStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSuccessfulRestoreId: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
