package com.ralledger.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Subscription
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

    fun createSubscription(
        name: String,
        merchant: String,
        amount: Money,
        frequency: BillingFrequency,
        assignedAccountId: String,
        nextBillingDate: Long,
        notes: String
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
                    notes = notes
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
