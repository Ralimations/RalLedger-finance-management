package com.ralledger.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InsightsUiState(
    val totalIncome: Money = Money.ZERO,
    val totalExpenses: Money = Money.ZERO,
    val netSavings: Money = Money.ZERO,
    val spendingByCategory: Map<String, Money> = emptyMap(),
    val accountDistribution: List<Pair<String, Money>> = emptyList(),
    val totalReceivablesAging: Map<String, Money> = emptyMap(),
    val activeSubscriptionTotal: Money = Money.ZERO,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false
)

class InsightsViewModel(
    repository: RalLedgerRepository
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = combine(
        repository.transactionsFlow,
        repository.accountsFlow,
        repository.receivablesFlow,
        repository.subscriptionsFlow,
        repository.categoriesFlow
    ) { transactions, accounts, receivables, subscriptions, categories ->
        val reversedJournalIds = transactions.mapNotNull { it.reversesEntryId }.toSet()
        val validTransactions = transactions.filter {
            it.journalEntryId !in reversedJournalIds &&
                it.transactionType in setOf(TransactionType.INCOME, TransactionType.EXPENSE)
        }

        val totalIncome = validTransactions
            .filter { it.transactionType == TransactionType.INCOME }
            .fold(Money.ZERO) { total, transaction -> total + transaction.amount }
        val totalExpenses = validTransactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .fold(Money.ZERO) { total, transaction -> total + transaction.amount }

        val categoryMap = categories.associateBy { it.id }
        val spendingByCategory = validTransactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .groupBy { transaction ->
                transaction.categoryId?.let { categoryMap[it]?.name } ?: transaction.categoryName ?: "Uncategorized"
            }
            .mapValues { (_, entries) -> entries.fold(Money.ZERO) { total, transaction -> total + transaction.amount } }

        val receivablesAging = receivables
            .groupBy { it.status.name }
            .mapValues { (_, entries) -> entries.fold(Money.ZERO) { total, receivable -> total + receivable.outstandingAmount } }

        val activeSubTotal = subscriptions
            .filter { it.status.name == "ACTIVE" }
            .fold(Money.ZERO) { total, subscription -> total + subscription.normalizedMonthlyCost() }

        InsightsUiState(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netSavings = totalIncome - totalExpenses,
            spendingByCategory = spendingByCategory,
            accountDistribution = accounts.filter { !it.isArchived }.map { it.name to it.currentLedgerBalance },
            totalReceivablesAging = receivablesAging,
            activeSubscriptionTotal = activeSubTotal,
            transactions = transactions,
            categories = categories,
            isLoading = false
        )
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
