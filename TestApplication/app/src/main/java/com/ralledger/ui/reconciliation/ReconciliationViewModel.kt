package com.ralledger.ui.reconciliation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReconciliationViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reconcileAccount(
        accountId: String,
        actualBalance: Money,
        reason: String
    ) {
        viewModelScope.launch {
            try {
                repository.reconcileAccount(accountId, actualBalance, reason)
                _operationMessage.value = "Reconciliation audit recorded."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Reconciliation could not be saved."
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReconciliationViewModel(repository) as T
        }
    }
}
