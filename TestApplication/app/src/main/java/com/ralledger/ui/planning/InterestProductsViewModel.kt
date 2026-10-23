package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InterestProductsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val products: StateFlow<List<InterestProduct>> = repository.interestProductsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val accounts = repository.accountsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun create(
        accountId: String, type: InterestProductType, rateBps: Int, taxBps: Int,
        compounding: InterestCompounding, openedAt: Long, maturityDate: Long?, principal: Money?, notes: String
    ) = runAction("Interest profile created") {
        repository.createInterestProduct(accountId, type, rateBps, taxBps, compounding, openedAt, maturityDate, principal, notes)
    }

    fun update(
        product: InterestProduct, rateBps: Int, taxBps: Int, compounding: InterestCompounding,
        openedAt: Long, maturityDate: Long?, principal: Money?, notes: String
    ) = runAction("Interest profile updated") {
        repository.updateInterestProduct(product.id, rateBps, taxBps, compounding, openedAt, maturityDate, principal, notes)
    }

    fun credit(
        productId: String, gross: Money, tax: Money, incomeCategoryId: String,
        taxCategoryId: String?, creditedAt: Long, notes: String
    ) = runAction("Interest credit recorded") {
        repository.recordInterestCredit(productId, gross, tax, incomeCategoryId, taxCategoryId, creditedAt, notes)
    }

    fun archive(productId: String, archived: Boolean) = runAction(
        if (archived) "Interest profile archived" else "Interest profile restored"
    ) { repository.setInterestProductArchived(productId, archived) }

    fun deleteUnused(productId: String) = runAction("Unused interest profile deleted") {
        repository.deleteUnusedInterestProduct(productId)
    }

    fun reverse(journalEntryId: String) = runAction("Interest credit reversed") {
        repository.reverseTransaction(journalEntryId)
    }

    fun clearMessage() { _message.value = null }

    private fun runAction(successMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            _message.value = try {
                action()
                successMessage
            } catch (error: Exception) {
                error.localizedMessage ?: "The interest record could not be updated"
            }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InterestProductsViewModel(repository) as T
    }
}
