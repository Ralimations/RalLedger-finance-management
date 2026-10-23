package com.ralledger.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SubscriptionsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    val subscriptions: StateFlow<List<Subscription>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<Category>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val counterparties: StateFlow<List<Counterparty>> = repository.counterpartiesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSubscription(
        name: String,
        merchant: String,
        amount: Money,
        frequency: BillingFrequency,
        assignedAccountId: String,
        nextBillingDate: Long,
        notes: String,
        counterpartyId: String? = null
    ) {
        viewModelScope.launch {
            try {
                require(name.isNotBlank()) { "Enter a subscription name" }
                require(amount.isPositive) { "Subscription amount must be greater than zero" }
                val sub = Subscription(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    merchant = merchant,
                    billingAmount = amount,
                    billingFrequency = frequency,
                    assignedAccountId = assignedAccountId,
                    nextBillingDate = nextBillingDate,
                    notes = notes,
                    counterpartyId = counterpartyId
                )
                repository.createSubscription(sub)
                _operationMessage.value = "Subscription saved."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription could not be saved."
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                repository.deleteSubscription(subscription)
                _operationMessage.value = "Subscription removed."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription could not be removed."
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                repository.updateSubscription(subscription)
                _operationMessage.value = "Subscription updated. Previous payment records are unchanged."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription could not be updated."
            }
        }
    }

    fun setStatus(subscription: Subscription, status: SubscriptionStatus) {
        viewModelScope.launch {
            try {
                repository.setSubscriptionStatus(subscription.id, status)
                _operationMessage.value = when (status) {
                    SubscriptionStatus.ACTIVE -> "Subscription resumed."
                    SubscriptionStatus.PAUSED -> "Subscription paused."
                    SubscriptionStatus.CANCELLED -> "Subscription cancelled."
                }
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription status could not be updated."
            }
        }
    }

    fun setArchived(subscription: Subscription, isArchived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setSubscriptionArchived(subscription.id, isArchived)
                _operationMessage.value = if (isArchived) "Subscription archived." else "Subscription restored."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription could not be archived."
            }
        }
    }

    fun recordPayment(subscriptionId: String, amount: Money, expenseCategoryId: String, paymentDate: Long, notes: String) {
        viewModelScope.launch {
            try {
                repository.recordSubscriptionPayment(subscriptionId, amount, expenseCategoryId, paymentDate, notes)
                _operationMessage.value = "Payment recorded in the ledger."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription payment could not be recorded."
            }
        }
    }

    fun reversePayment(journalEntryId: String) {
        viewModelScope.launch {
            try {
                repository.reverseTransaction(journalEntryId)
                _operationMessage.value = "Subscription payment reversed; its history remains saved."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Subscription payment could not be reversed."
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SubscriptionsViewModel(repository) as T
        }
    }
}
