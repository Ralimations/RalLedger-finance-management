package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Budget
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BudgetsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {
    val budgets: StateFlow<List<Budget>> = repository.budgetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createBudget(categoryId: String, year: Int, month: Int, limit: Money) =
        repository.createBudget(categoryId, year, month, limit)

    suspend fun updateBudget(id: String, categoryId: String, year: Int, month: Int, limit: Money) =
        repository.updateBudget(id, categoryId, year, month, limit)

    suspend fun setArchived(id: String, archived: Boolean) = repository.setBudgetArchived(id, archived)

    suspend fun deleteBudget(id: String) = repository.deleteBudget(id)

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BudgetsViewModel(repository) as T
    }
}
