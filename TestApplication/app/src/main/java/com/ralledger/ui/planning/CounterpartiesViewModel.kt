package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.CounterpartyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CounterpartiesViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val counterparties: StateFlow<List<Counterparty>> = repository.counterpartiesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val receivables = repository.receivablesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expectedIncome = repository.expectedIncomeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(name: String, kind: CounterpartyKind, notes: String) = runAction("Counterparty saved") {
        repository.createCounterparty(name, kind, notes)
    }

    fun update(item: Counterparty, name: String, kind: CounterpartyKind, notes: String) = runAction("Changes saved") {
        repository.updateCounterparty(item.id, name, kind, notes)
    }

    fun archive(item: Counterparty, archived: Boolean) = runAction(
        if (archived) "Counterparty archived" else "Counterparty restored"
    ) { repository.setCounterpartyArchived(item.id, archived) }

    fun delete(item: Counterparty) = runAction("Counterparty deleted") {
        repository.deleteCounterparty(item.id)
    }

    fun merge(source: Counterparty, target: Counterparty) = runAction("Counterparty merged; linked records and history were retained") {
        repository.mergeCounterparties(source.id, target.id)
    }

    fun clearMessage() { _message.value = null }

    private fun runAction(successMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            _message.value = null
            runCatching { action() }
                .onSuccess { _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Could not save this counterparty" }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CounterpartiesViewModel(repository) as T
    }
}
