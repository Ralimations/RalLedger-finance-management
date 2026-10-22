package com.ralledger.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
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

    fun createAccount(
        name: String,
        institution: String,
        accountType: AccountType,
        openingBalance: Money,
        iconName: String,
        colorHex: String
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
                    colorHex = colorHex
                )
                repository.createAccount(account)
                _operationMessage.value = "Account created."
            } catch (error: Exception) {
                _operationMessage.value = error.localizedMessage ?: "Account could not be created."
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
