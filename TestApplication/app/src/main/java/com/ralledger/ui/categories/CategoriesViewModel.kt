package com.ralledger.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CategoriesViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun createCategory(name: String, type: CategoryType, parentId: String?, iconName: String, colorHex: String) {
        runOperation("Category created.") {
            repository.createCategory(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    iconName = iconName,
                    colorHex = colorHex,
                    parentCategoryId = parentId
                )
            )
        }
    }

    fun updateCategory(id: String, name: String, parentId: String?, iconName: String, colorHex: String) {
        runOperation("Category updated.") {
            repository.updateCategoryMetadata(id, name, iconName, colorHex, parentId)
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        runOperation(if (archived) "Category archived." else "Category restored.") {
            repository.setCategoryArchived(id, archived)
        }
    }

    fun deleteUnused(id: String) {
        runOperation("Category permanently deleted.") {
            repository.deleteUnusedCategory(id)
        }
    }

    fun merge(sourceId: String, targetId: String) {
        runOperation("Categories merged. Transaction amounts and account balances were preserved.") {
            repository.mergeCategory(sourceId, targetId)
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun runOperation(successMessage: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
                _operationMessage.value = successMessage
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Category could not be saved."
            }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CategoriesViewModel(repository) as T
    }
}
