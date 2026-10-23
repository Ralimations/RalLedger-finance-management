package com.ralledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.BackupPreview
import com.ralledger.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)
    val restorePreview: StateFlow<BackupPreview?> = _restorePreview.asStateFlow()

    fun createEncryptedBackup(password: String, writeDocument: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            try {
                val preview = backupRepository.writeEncryptedBackupToDocument(password, writeDocument)
                _backupStatus.value = "Encrypted backup saved. ${preview.accounts} accounts, ${preview.expectedIncome} expected-income records, ${preview.debts} bills and loans, ${preview.journalEntries} journal entries, and ${preview.receivables} receivables included."
            } catch (e: Exception) {
                _backupStatus.value = "Backup failed: ${e.localizedMessage}"
            }
        }
    }

    fun inspectBackup(bytes: ByteArray, password: String) {
        val privateCopy = bytes.copyOf()
        viewModelScope.launch {
            try {
                val preview = backupRepository.prepareRestore(privateCopy, password)
                _restorePreview.value = preview
                _backupStatus.value = if (preview.canRestore) {
                    "Backup opened and passed integrity checks. Review its contents before restoring."
                } else {
                    "Backup opened, but integrity checks found problems. Restore is disabled."
                }
            } catch (e: Exception) {
                _backupStatus.value = "Backup could not be opened: ${e.localizedMessage}"
                _restorePreview.value = null
            } finally {
                privateCopy.fill(0)
            }
        }
    }

    fun confirmRestore() {
        viewModelScope.launch {
            try {
                val preview = backupRepository.restorePreparedBackup()
                _backupStatus.value = "Restore complete. ${preview.accounts} accounts, ${preview.expectedIncome} expected-income records, ${preview.debts} bills and loans, ${preview.journalEntries} journal entries, and ${preview.receivables} receivables restored."
                _restorePreview.value = null
            } catch (e: Exception) {
                _backupStatus.value = "Restore failed; existing data was retained: ${e.localizedMessage}"
            }
        }
    }

    fun cancelRestore() {
        _restorePreview.value = null
        backupRepository.cancelPreparedRestore()
    }

    class Factory(private val backupRepository: BackupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(backupRepository) as T
        }
    }
}
