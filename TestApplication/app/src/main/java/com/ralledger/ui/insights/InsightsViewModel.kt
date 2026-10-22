package com.ralledger.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.Subscription
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
    val isLoading: Boolean = false
)

class InsightsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = combine(
        repository.transactionsFlow,
        repository.accountsFlow,
        repository.receivablesFlow,
        repository.subscriptionsFlow,
        repository.categoriesFlow
    ) { transactions, accounts, receivables, subscriptions, categories ->
        
        // Exclude opening balances and internal transfers from income/expense stats
        val validTransactions = transactions.filter { 
            it.transactionType != TransactionType.OPENING_BALANCE && 
            it.transactionType != TransactionType.TRANSFER &&
            it.transactionType != TransactionType.BALANCE_ADJUSTMENT
        }

        val totalIncome = validTransactions
            .filter { it.transactionType == TransactionType.INCOME }
            .fold(Money.ZERO) { acc, t -> acc + t.amount }

        val totalExpenses = validTransactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .fold(Money.ZERO) { acc, t -> acc + t.amount }

        val netSavings = totalIncome - totalExpenses

        val categoryMap = categories.associateBy { it.id }
        val spendingByCat = mutableMapOf<String, Long>()
        validTransactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .forEach { t ->
                val catName = t.categoryId?.let { categoryMap[it]?.name } ?: t.categoryName ?: "Uncategorized"
                val current = spendingByCat.getOrDefault(catName, 0L)
                spendingByCat[catName] = current + t.amount.centavos
            }
        val spendingByCategory = spendingByCat.mapValues { Money(it.value) }

        val accountDistribution = accounts
            .filter { !it.isArchived }
            .map { it.name to it.currentLedgerBalance }

        val receivablesAging = mutableMapOf<String, Long>()
        receivables.forEach { r ->
            val status = r.status.name
            val current = receivablesAging.getOrDefault(status, 0L)
            receivablesAging[status] = current + r.outstandingAmount.centavos
        }
        val totalReceivablesAging = receivablesAging.mapValues { Money(it.value) }

        val activeSubTotal = subscriptions
            .filter { it.status.name == "ACTIVE" }
            .fold(Money.ZERO) { acc, s -> acc + s.billingAmount }

        InsightsUiState(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netSavings = netSavings,
            spendingByCategory = spendingByCategory,
            accountDistribution = accountDistribution,
            totalReceivablesAging = totalReceivablesAging,
            activeSubscriptionTotal = activeSubTotal,
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
