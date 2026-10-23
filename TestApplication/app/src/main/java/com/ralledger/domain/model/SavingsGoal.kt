package com.ralledger.domain.model

enum class SavingsGoalEventType { ALLOCATE, RELEASE }

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Money,
    val targetDate: Long?,
    val notes: String,
    val allocatedAmount: Money,
    val isArchived: Boolean,
    val createdAt: Long,
    val events: List<SavingsGoalEvent> = emptyList()
) {
    val progress: Float get() = if (!targetAmount.isPositive) 0f else
        (allocatedAmount.centavos.toDouble() / targetAmount.centavos).coerceIn(0.0, 1.0).toFloat()
}

data class SavingsGoalEvent(
    val id: String,
    val goalId: String,
    val type: SavingsGoalEventType,
    val amount: Money,
    val date: Long,
    val note: String
)
