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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceivablesViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

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
        runOperation("Receivable created.") { repository.createReceivable(clientName, amount, dueDate, incomeCategoryId, description) }
    }

    fun collectReceivable(
        receivableId: String,
        destinationAccountId: String,
        amount: Money,
        notes: String
    ) {
        runOperation("Payment recorded.") { repository.collectReceivable(receivableId, destinationAccountId, amount, notes) }
    }

    fun updateReceivable(id: String, clientName: String, dueDate: Long, description: String) {
        runOperation("Receivable details updated.") {
            repository.updateReceivableDetails(id, clientName, dueDate, description)
        }
    }

    fun cancelReceivable(receivable: Receivable) {
        runOperation("Receivable cancelled with a reversing journal entry.") {
            repository.cancelReceivable(receivable.id)
        }
    }

    fun writeOffReceivable(id: String, amount: Money, expenseCategoryId: String, notes: String) {
        runOperation("Write-off recorded.") {
            repository.writeOffReceivable(id, amount, expenseCategoryId, notes)
        }
    }

    fun reverseEntry(journalEntryId: String) {
        runOperation("Reversal recorded.") { repository.reverseTransaction(journalEntryId) }
    }

    fun setArchived(receivable: Receivable, isArchived: Boolean) {
        runOperation(if (isArchived) "Receivable archived." else "Receivable restored.") {
            repository.setReceivableArchived(receivable.id, isArchived)
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun runOperation(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _operationMessage.value = successMessage
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "The receivable could not be saved."
            }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceivablesViewModel(repository) as T
        }
    }
}
