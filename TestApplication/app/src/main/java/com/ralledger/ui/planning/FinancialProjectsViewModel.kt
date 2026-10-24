package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.usecase.FinancialProjectCalculator
import com.ralledger.domain.usecase.InsightsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinancialProjectsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val projects: StateFlow<List<FinancialProject>> = repository.financialProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val results: StateFlow<Map<String, InsightsResult>> = combine(
        repository.financialProjectsFlow,
        repository.transactionsFlow,
        repository.categoriesFlow
    ) { projects, transactions, categories ->
        projects.associate { project ->
            project.id to FinancialProjectCalculator.calculate(project, transactions, categories)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(name: String, kind: FinancialProjectKind, description: String, colorHex: String) =
        runAction("Project created") { repository.createFinancialProject(name, kind, description, colorHex) }

    fun update(project: FinancialProject, name: String, kind: FinancialProjectKind, description: String, colorHex: String) =
        runAction("Project updated") { repository.updateFinancialProject(project.id, name, kind, description, colorHex) }

    fun archive(project: FinancialProject, archived: Boolean) = runAction(
        if (archived) "Project archived. Transaction history is preserved." else "Project restored"
    ) { repository.setFinancialProjectArchived(project.id, archived) }

    fun delete(project: FinancialProject) = runAction("Unused project deleted") {
        repository.deleteUnusedFinancialProject(project.id)
    }

    fun merge(source: FinancialProject, targetId: String) = runAction("Project references moved; history is preserved") {
        repository.mergeFinancialProjects(source.id, targetId)
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
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FinancialProjectsViewModel(repository) as T
    }
}
