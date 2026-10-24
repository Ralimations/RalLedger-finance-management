package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CustomReport
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.TransactionTag
import com.ralledger.domain.usecase.CustomReportCalculator
import com.ralledger.domain.usecase.InsightsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomReportsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val reports: StateFlow<List<CustomReport>> = repository.customReportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<TransactionTag>> = repository.transactionTagsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects: StateFlow<List<FinancialProject>> = repository.financialProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val results: StateFlow<Map<String, InsightsResult>> = combine(
        repository.customReportsFlow,
        repository.transactionsFlow,
        repository.categoriesFlow
    ) { savedReports, transactions, categories ->
        savedReports.associate { report ->
            report.id to CustomReportCalculator.calculate(report, transactions, categories)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(name: String, startDate: Long, endDate: Long, accountId: String?, categoryId: String?, tagId: String?, projectId: String?) =
        runAction("Report saved") { repository.createCustomReport(name, startDate, endDate, accountId, categoryId, tagId, projectId) }

    fun update(report: CustomReport, name: String, startDate: Long, endDate: Long, accountId: String?, categoryId: String?, tagId: String?, projectId: String?) =
        runAction("Changes saved") { repository.updateCustomReport(report.id, name, startDate, endDate, accountId, categoryId, tagId, projectId) }

    fun archive(report: CustomReport, archived: Boolean) =
        runAction(if (archived) "Report archived" else "Report restored") { repository.setCustomReportArchived(report.id, archived) }

    fun delete(report: CustomReport) = runAction("Report deleted") { repository.deleteCustomReport(report.id) }

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
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomReportsViewModel(repository) as T
    }
}
