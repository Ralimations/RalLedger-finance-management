package com.ralledger.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.RalLedgerRepository
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.SavingsGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavingsGoalsViewModel(private val repository: RalLedgerRepository) : ViewModel() {
    val goals: StateFlow<List<SavingsGoal>> = repository.savingsGoalsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun create(name: String, target: Money, targetDate: Long?, notes: String) =
        runAction("Savings goal created") { repository.createSavingsGoal(name, target, targetDate, notes) }

    fun update(id: String, name: String, target: Money, targetDate: Long?, notes: String) =
        runAction("Savings goal updated") { repository.updateSavingsGoal(id, name, target, targetDate, notes) }

    fun allocate(goalId: String, amount: Money, note: String) =
        runAction("Money reserved for this goal") { repository.allocateToSavingsGoal(goalId, amount, note = note) }

    fun release(goalId: String, amount: Money, note: String) =
        runAction("Reserved money released") { repository.releaseFromSavingsGoal(goalId, amount, note = note) }

    fun archive(goalId: String, archived: Boolean) = runAction(
        if (archived) "Savings goal archived" else "Savings goal restored"
    ) { repository.setSavingsGoalArchived(goalId, archived) }

    fun deleteUnused(goalId: String) = runAction("Unused savings goal deleted") {
        repository.deleteUnusedSavingsGoal(goalId)
    }

    fun clearMessage() { _message.value = null }

    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _message.value = try {
                action()
                successMessage
            } catch (error: Exception) {
                error.localizedMessage ?: "The savings goal could not be updated"
            }
        }
    }

    class Factory(private val repository: RalLedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SavingsGoalsViewModel(repository) as T
    }
}
