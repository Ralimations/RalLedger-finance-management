package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.RecurringTransactionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class RecurringTransactionsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {
    val schedules = repository.recurringTransactionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val accounts = repository.accountsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun setStatus(id: String, status: RecurringTransactionStatus) =
        repository.setRecurringTransactionStatus(id, status)

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecurringTransactionsViewModel::class.java))
            return RecurringTransactionsViewModel(repository) as T
        }
    }
}
