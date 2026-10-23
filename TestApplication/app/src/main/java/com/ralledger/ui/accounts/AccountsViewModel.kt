package com.ralledger.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AccountsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    val accounts: StateFlow<List<Account>> = repository.accountsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactions: StateFlow<List<Transaction>> = repository.transactionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createAccount(
        name: String,
        institution: String,
        accountType: AccountType,
        openingBalance: Money,
        iconName: String,
        colorHex: String,
        description: String,
        includeInDashboardTotals: Boolean
    ) {
        viewModelScope.launch {
            try {
                val account = Account(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    institution = institution,
                    accountType = accountType,
                    openingBalance = openingBalance,
                    iconName = iconName,
                    colorHex = colorHex,
                    description = description,
                    includeInDashboardTotals = includeInDashboardTotals
                )
                repository.createAccount(account)
                _operationMessage.value = "Account created."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Account could not be created."
            }
        }
    }

    fun updateAccountMetadata(
        id: String,
        name: String,
        institution: String,
        description: String,
        iconName: String,
        colorHex: String,
        includeInDashboardTotals: Boolean
    ) {
        viewModelScope.launch {
            try {
                repository.updateAccountMetadata(
                    id, name, institution, description, iconName, colorHex, includeInDashboardTotals
                )
                _operationMessage.value = "Account updated."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Account could not be updated."
            }
        }
    }

    fun setArchived(id: String, isArchived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setAccountArchived(id, isArchived)
                _operationMessage.value = if (isArchived) "Account archived." else "Account restored."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Account status could not be changed."
            }
        }
    }

    fun deleteUnusedAccount(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteUnusedAccount(id)
                _operationMessage.value = "Account permanently deleted."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Account could not be deleted."
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccountsViewModel(repository) as T
        }
    }
}
