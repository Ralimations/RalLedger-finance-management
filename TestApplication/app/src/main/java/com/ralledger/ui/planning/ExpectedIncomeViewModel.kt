package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.ExpectedIncome
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpectedIncomeViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {
    val expectedIncome: StateFlow<List<ExpectedIncome>> = repository.expectedIncomeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(description: String, counterparty: String, amount: Money, date: Long, categoryId: String) = runAction("Expected income saved") {
        repository.createExpectedIncome(description, counterparty, amount, date, categoryId)
    }

    fun update(item: ExpectedIncome, description: String, counterparty: String, amount: Money, date: Long, categoryId: String) = runAction("Changes saved") {
        repository.updateExpectedIncome(item.id, description, counterparty, amount, date, categoryId)
    }

    fun cancel(item: ExpectedIncome) = runAction("Expected income cancelled") { repository.cancelExpectedIncome(item.id) }
    fun reactivate(item: ExpectedIncome) = runAction("Expected income reactivated") { repository.reactivateExpectedIncome(item.id) }
    fun archive(item: ExpectedIncome, archived: Boolean) = runAction(if (archived) "Expected income archived" else "Expected income restored") {
        repository.setExpectedIncomeArchived(item.id, archived)
    }
    fun delete(item: ExpectedIncome) = runAction("Expected income deleted") { repository.deleteExpectedIncome(item.id) }
    fun convert(item: ExpectedIncome) = runAction("Converted to a receivable") { repository.convertExpectedIncomeToReceivable(item.id) }

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
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpectedIncomeViewModel(repository) as T
    }
}
