package com.ralledger.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        repository.transactionsFlow,
        _searchQuery
    ) { txs, query ->
        if (query.isBlank()) {
            txs
        } else {
            txs.filter {
                it.description.contains(query, ignoreCase = true) ||
                it.counterparty.contains(query, ignoreCase = true) ||
                it.accountName.contains(query, ignoreCase = true) ||
                (it.categoryName?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun recordIncome(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = ""
    ) {
        runOperation {
            repository.recordIncome(accountId, categoryId, amount, description, counterparty, notes)
        }
    }

    fun recordExpense(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = ""
    ) {
        runOperation {
            repository.recordExpense(accountId, categoryId, amount, description, counterparty, notes)
        }
    }

    fun recordTransfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: Money,
        fee: Money,
        feeCategoryId: String?,
        description: String,
        notes: String = ""
    ) {
        runOperation {
            repository.recordTransfer(sourceAccountId, destinationAccountId, amount, fee, feeCategoryId, description, notes)
        }
    }

    fun reverseTransaction(journalEntryId: String) {
        runOperation { repository.reverseTransaction(journalEntryId) }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun runOperation(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _operationMessage.value = null
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "The transaction could not be saved."
            }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionsViewModel(repository) as T
        }
    }
}
