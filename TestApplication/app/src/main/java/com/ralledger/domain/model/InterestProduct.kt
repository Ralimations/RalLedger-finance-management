package com.ralledger.domain.model

enum class InterestProductType(val label: String) {
    SAVINGS_ACCOUNT("Interest-bearing savings"),
    TIME_DEPOSIT("Time deposit")
}

enum class InterestCompounding(val label: String, val periodsPerYear: Int) {
    SIMPLE("Simple", 1),
    DAILY("Daily", 365),
    MONTHLY("Monthly", 12),
    QUARTERLY("Quarterly", 4),
    ANNUALLY("Annually", 1)
}

data class InterestEstimate(
    val gross: Money,
    val tax: Money,
    val net: Money
)

data class InterestCredit(
    val id: String,
    val productId: String,
    val journalEntryId: String,
    val gross: Money,
    val tax: Money,
    val net: Money,
    val annualRateBasisPoints: Int,
    val taxRateBasisPoints: Int,
    val taxExpenseCategoryId: String?,
    val creditedAt: Long,
    val notes: String,
    val reversedAt: Long?
)

data class InterestProduct(
    val id: String,
    val accountId: String,
    val accountName: String,
    val accountBalance: Money,
    val type: InterestProductType,
    val annualRateBasisPoints: Int,
    val taxRateBasisPoints: Int,
    val compounding: InterestCompounding,
    val openedAt: Long,
    val maturityDate: Long?,
    val principal: Money?,
    val notes: String,
    val isArchived: Boolean,
    val createdAt: Long,
    val credits: List<InterestCredit> = emptyList()
)
