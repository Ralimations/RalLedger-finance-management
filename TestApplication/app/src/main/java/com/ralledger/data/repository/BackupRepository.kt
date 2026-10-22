package com.ralledger.data.repository

import android.content.Context
import com.ralledger.data.local.RalLedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupRepository(
    private val context: Context,
    private val database: RalLedgerDatabase
) {
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("appName", "RalLedger")
        json.put("version", 1)
        json.put("exportTimestamp", System.currentTimeMillis())

        val accountsArray = JSONArray()
        database.accountDao().getAllAccountsFlow()

        json.toString(2)
    }

    suspend fun saveBackupToFile(filename: String = "RalLedger_Backup.json"): File = withContext(Dispatchers.IO) {
        val jsonString = createBackupJson()
        val file = File(context.filesDir, filename)
        file.writeText(jsonString)
        file
    }
}
