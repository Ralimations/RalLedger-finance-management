package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.PlannedPayment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlannedPaymentsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val payments: StateFlow<List<PlannedPayment>> = repository.plannedPaymentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val counterparties: StateFlow<List<Counterparty>> = repository.counterpartiesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(description: String, counterparty: String, amount: Money, dueDate: Long, categoryId: String, counterpartyId: String?, notes: String) =
        runAction("Planned payment saved") {
            repository.createPlannedPayment(description, counterparty, amount, dueDate, categoryId, counterpartyId, notes)
        }

    fun update(item: PlannedPayment, description: String, counterparty: String, amount: Money, dueDate: Long, categoryId: String, counterpartyId: String?, notes: String) =
        runAction("Changes saved") {
            repository.updatePlannedPayment(item.id, description, counterparty, amount, dueDate, categoryId, counterpartyId, notes)
        }

    fun cancel(item: PlannedPayment) = runAction("Plan cancelled") { repository.cancelPlannedPayment(item.id) }
    fun reactivate(item: PlannedPayment) = runAction("Plan reactivated") { repository.reactivatePlannedPayment(item.id) }
    fun archive(item: PlannedPayment, archived: Boolean) = runAction(if (archived) "Plan archived" else "Plan restored") {
        repository.setPlannedPaymentArchived(item.id, archived)
    }
    fun delete(item: PlannedPayment) = runAction("Plan deleted") { repository.deletePlannedPayment(item.id) }
    fun record(item: PlannedPayment, accountId: String, amount: Money, date: Long, notes: String) =
        runAction("Payment recorded") { repository.recordPlannedPayment(item.id, accountId, amount, date, notes) }
    fun reverse(recordId: String) = runAction("Payment reversed; the plan is available again") {
        repository.reversePlannedPaymentRecord(recordId)
    }

    fun clearMessage() { _message.value = null }

    private fun runAction(successMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            _message.value = null
            runCatching { action() }
                .onSuccess { _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Could not complete this action" }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlannedPaymentsViewModel(repository) as T
    }
}
