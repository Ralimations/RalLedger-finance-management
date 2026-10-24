package com.ralledger.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionFilter
import com.ralledger.domain.model.TransactionTypeFilter
import com.ralledger.domain.model.TransactionTag
import com.ralledger.domain.model.SavedTransactionFilter
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.FinancialProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TransactionTag>> = repository.transactionTagsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<FinancialProject>> = repository.financialProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedFilters: StateFlow<List<SavedTransactionFilter>> = repository.savedTransactionFiltersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionTemplates: StateFlow<List<TransactionTemplate>> = repository.transactionTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _typeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    val typeFilter: StateFlow<TransactionTypeFilter> = _typeFilter
    private val _accountFilterId = MutableStateFlow<String?>(null)
    val accountFilterId: StateFlow<String?> = _accountFilterId
    private val _categoryFilterId = MutableStateFlow<String?>(null)
    val categoryFilterId: StateFlow<String?> = _categoryFilterId
    private val _tagFilterId = MutableStateFlow<String?>(null)
    val tagFilterId: StateFlow<String?> = _tagFilterId
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val dateRange: StateFlow<Pair<Long?, Long?>> = _dateRange
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage
    private val _tagMessage = MutableStateFlow<String?>(null)
    val tagMessage: StateFlow<String?> = _tagMessage
    private val _savedFilterMessage = MutableStateFlow<String?>(null)
    val savedFilterMessage: StateFlow<String?> = _savedFilterMessage
    private val _templateMessage = MutableStateFlow<String?>(null)
    val templateMessage: StateFlow<String?> = _templateMessage

    val activeFilter: StateFlow<TransactionFilter> = combine(_searchQuery, _typeFilter, _accountFilterId, _categoryFilterId, _dateRange) {
            query, type, accountId, categoryId, range ->
        TransactionFilter(query, type, accountId, categoryId, range.first, range.second)
    }.combine(_tagFilterId) { filter, tagId -> filter.copy(tagId = tagId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionFilter())

    val canSaveActiveFilter: StateFlow<Boolean> = activeFilter.map { filter ->
        filter.query.isNotBlank() || filter.type != TransactionTypeFilter.ALL || filter.accountId != null ||
            filter.categoryId != null || filter.startInclusive != null || filter.endExclusive != null || filter.tagId != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        repository.transactionsFlow,
        activeFilter
    ) { txs, activeFilters ->
        txs.filter(activeFilters::matches)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(filter: TransactionTypeFilter) {
        _typeFilter.value = filter
    }

    fun setAccountFilter(accountId: String?) {
        _accountFilterId.value = accountId
    }

    fun setCategoryFilter(categoryId: String?) {
        _categoryFilterId.value = categoryId
    }

    fun setTagFilter(tagId: String?) {
        _tagFilterId.value = tagId
    }

    fun setDateRange(startInclusive: Long?, endExclusive: Long?) {
        _dateRange.value = when {
            startInclusive != null && endExclusive != null && startInclusive >= endExclusive -> endExclusive to startInclusive
            else -> startInclusive to endExclusive
        }
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _typeFilter.value = TransactionTypeFilter.ALL
        _accountFilterId.value = null
        _categoryFilterId.value = null
        _tagFilterId.value = null
        _dateRange.value = null to null
    }

    fun applySavedFilter(savedFilter: SavedTransactionFilter) {
        val filter = savedFilter.filter
        _searchQuery.value = filter.query
        _typeFilter.value = filter.type
        _accountFilterId.value = filter.accountId
        _categoryFilterId.value = filter.categoryId
        _tagFilterId.value = filter.tagId
        _dateRange.value = filter.startInclusive to filter.endExclusive
    }

    fun saveActiveFilter(name: String) = runSavedFilterOperation("Saved filter created.") {
        repository.createSavedTransactionFilter(name, activeFilter.value)
    }

    fun updateSavedFilter(id: String) = runSavedFilterOperation("Saved filter updated from current filters.") {
        repository.updateSavedTransactionFilterCriteria(id, activeFilter.value)
    }

    fun renameSavedFilter(id: String, name: String) = runSavedFilterOperation("Saved filter renamed.") {
        repository.renameSavedTransactionFilter(id, name)
    }

    fun deleteSavedFilter(id: String) = runSavedFilterOperation("Saved filter deleted.") {
        repository.deleteSavedTransactionFilter(id)
    }

    fun createTransactionTemplate(template: TransactionTemplate) = runTemplateOperation("Template saved.") {
        repository.createTransactionTemplate(template)
    }

    fun updateTransactionTemplate(template: TransactionTemplate) = runTemplateOperation("Template updated.") {
        repository.updateTransactionTemplate(template)
    }

    fun setTransactionTemplateArchived(id: String, isArchived: Boolean) = runTemplateOperation(
        if (isArchived) "Template archived." else "Template restored."
    ) { repository.setTransactionTemplateArchived(id, isArchived) }

    fun deleteTransactionTemplate(id: String) = runTemplateOperation("Template deleted.") {
        repository.deleteTransactionTemplate(id)
    }

    fun clearTemplateMessage() { _templateMessage.value = null }

    fun clearSavedFilterMessage() {
        _savedFilterMessage.value = null
    }

    fun createTag(name: String, colorHex: String) = runTagOperation("Label created.") {
        repository.createTransactionTag(name, colorHex)
    }

    fun updateTag(id: String, name: String, colorHex: String) = runTagOperation("Label updated.") {
        repository.updateTransactionTag(id, name, colorHex)
    }

    fun setTagArchived(id: String, isArchived: Boolean) = runTagOperation(
        if (isArchived) "Label archived. Existing transaction history is preserved." else "Label restored."
    ) {
        repository.setTransactionTagArchived(id, isArchived)
    }

    fun deleteUnusedTag(id: String) = runTagOperation("Unused label deleted.") {
        repository.deleteUnusedTransactionTag(id)
    }

    fun mergeTags(sourceId: String, targetId: String) = runTagOperation("Labels merged. Transaction history is preserved.") {
        repository.mergeTransactionTag(sourceId, targetId)
    }

    fun setTransactionTags(journalEntryId: String, tagIds: Set<String>) = runTagOperation("Transaction labels updated.") {
        repository.setJournalEntryTags(journalEntryId, tagIds)
    }

    fun setTransactionProject(journalEntryId: String, projectId: String?) = runTagOperation("Transaction project updated.") {
        repository.setJournalEntryProject(journalEntryId, projectId)
    }

    fun clearTagMessage() {
        _tagMessage.value = null
    }

    fun recordIncome(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        runOperation {
            repository.recordIncome(accountId, categoryId, amount, description, counterparty, notes, timestamp)
        }
    }

    fun recordExpense(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        runOperation {
            repository.recordExpense(accountId, categoryId, amount, description, counterparty, notes, timestamp)
        }
    }

    fun recordTransfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: Money,
        fee: Money,
        feeCategoryId: String?,
        description: String,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        runOperation {
            repository.recordTransfer(sourceAccountId, destinationAccountId, amount, fee, feeCategoryId, description, notes, timestamp)
        }
    }

    fun correctTransaction(journalEntryId: String, correction: TransactionCorrection) {
        runOperation { repository.correctTransaction(journalEntryId, correction) }
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

    private fun runTagOperation(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _tagMessage.value = null
            try {
                action()
                _tagMessage.value = successMessage
            } catch (error: Exception) {
                _tagMessage.value = error.localizedMessage ?: "The label could not be saved."
            }
        }
    }

    private fun runSavedFilterOperation(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _savedFilterMessage.value = null
            try {
                action()
                _savedFilterMessage.value = successMessage
            } catch (error: Exception) {
                _savedFilterMessage.value = error.localizedMessage ?: "The saved filter could not be changed."
            }
        }
    }

    private fun runTemplateOperation(successMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            _templateMessage.value = null
            runCatching { action() }
                .onSuccess { _templateMessage.value = successMessage }
                .onFailure { _templateMessage.value = it.message ?: "The transaction template could not be changed." }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionsViewModel(repository) as T
        }
    }
}
