package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val debts: StateFlow<List<Debt>> = repository.debtsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = repository.accountsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(kind: DebtKind, title: String, counterparty: String, amount: Money, dueDate: Long, categoryId: String?, accountId: String?) =
        runAction(if (kind == DebtKind.BILL) "Bill recorded" else "Loan recorded") {
            if (kind == DebtKind.BILL) repository.createBill(title, counterparty, amount, dueDate, categoryId.orEmpty())
            else repository.createLoan(title, counterparty, amount, dueDate, accountId.orEmpty())
        }

    fun update(item: Debt, title: String, counterparty: String, dueDate: Long) = runAction("Changes saved") {
        repository.updateDebtMetadata(item.id, title, counterparty, dueDate)
    }

    fun pay(item: Debt, accountId: String, principal: Money, interest: Money, categoryId: String?, date: Long, notes: String) =
        runAction("Payment recorded") { repository.recordDebtPayment(item.id, accountId, principal, interest, categoryId, date, notes) }

    fun cancel(item: Debt) = runAction("Bill or loan cancelled") { repository.cancelDebt(item.id) }
    fun archive(item: Debt, archived: Boolean) = runAction(if (archived) "Record archived" else "Record restored") {
        repository.setDebtArchived(item.id, archived)
    }
    fun reversePayment(paymentJournalEntryId: String) = runAction("Payment reversed") {
        repository.reverseTransaction(paymentJournalEntryId)
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
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DebtsViewModel(repository) as T
    }
}
