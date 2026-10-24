package com.ralledger.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.InterestProduct
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InsightsUiState(
    val accountDistribution: List<Pair<String, Money>> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val receivables: List<Receivable> = emptyList(),
    val activeSubscriptionTotal: Money = Money.ZERO,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val projects: List<FinancialProject> = emptyList(),
    val interestProducts: List<InterestProduct> = emptyList(),
    val isLoading: Boolean = false
)

class InsightsViewModel(
    repository: RalLedgerRepository
) : ViewModel() {

    private val baseUiState = combine(
        repository.transactionsFlow,
        repository.accountsFlow,
        repository.receivablesFlow,
        repository.subscriptionsFlow,
        repository.categoriesFlow
    ) { transactions, accounts, receivables, subscriptions, categories ->
        val activeSubTotal = subscriptions
            .filter { it.status.name == "ACTIVE" && !it.isArchived }
            .fold(Money.ZERO) { total, subscription -> total + subscription.normalizedMonthlyCost() }

        InsightsUiState(
            accountDistribution = accounts.filter {
                !it.isArchived && it.accountType.isAsset && it.accountType != AccountType.ACCOUNTS_RECEIVABLE
            }.map { it.name to it.currentLedgerBalance },
            accounts = accounts,
            receivables = receivables,
            activeSubscriptionTotal = activeSubTotal,
            transactions = transactions,
            categories = categories,
            isLoading = false
        )
    }

    private val stateWithProjects = combine(baseUiState, repository.financialProjectsFlow) { state, projects ->
        state.copy(projects = projects)
    }
    private val stateWithSavingsProducts = combine(stateWithProjects, repository.interestProductsFlow) { state, products ->
        state.copy(interestProducts = products)
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        stateWithSavingsProducts,
        repository.journalHistoryFlow,
        repository.debtsFlow
    ) { state, journalEntries, debts ->
        state.copy(journalEntries = journalEntries, debts = debts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(isLoading = true)
    )

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InsightsViewModel::class.java)) {
                return InsightsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
