package com.ralledger.ui.receivables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceivablesViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    val receivables: StateFlow<List<Receivable>> = repository.receivablesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createReceivable(
        clientName: String,
        amount: Money,
        dueDate: Long,
        incomeCategoryId: String?,
        description: String
    ) {
        viewModelScope.launch {
            repository.createReceivable(clientName, amount, dueDate, incomeCategoryId, description)
        }
    }

    fun collectReceivable(
        receivableId: String,
        destinationAccountId: String,
        amount: Money,
        notes: String
    ) {
        viewModelScope.launch {
            repository.collectReceivable(receivableId, destinationAccountId, amount, notes)
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceivablesViewModel(repository) as T
        }
    }
}
