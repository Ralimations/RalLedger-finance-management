package com.ralledger.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AccountsViewModel(
    private val repository: RalLedgerRepository
) : ViewModel() {

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
        }
    }

    fun setArchived(id: String, isArchived: Boolean) {
        viewModelScope.launch {
            repository.setAccountArchived(id, isArchived)
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccountsViewModel(repository) as T
        }
    }
}
