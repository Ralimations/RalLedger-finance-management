package com.ralledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun createBackup() {
        viewModelScope.launch {
            try {
                val file = backupRepository.saveBackupToFile()
                _backupStatus.value = "Backup created successfully at ${file.name}"
            } catch (e: Exception) {
                _backupStatus.value = "Backup failed: ${e.localizedMessage}"
            }
        }
    }

    class Factory(private val backupRepository: BackupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(backupRepository) as T
        }
    }
}
