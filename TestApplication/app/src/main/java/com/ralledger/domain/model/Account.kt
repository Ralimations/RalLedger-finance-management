package com.ralledger.domain.model

data class Account(
    val id: String,
    val name: String,
    val institution: String = "",
    val accountType: AccountType,
    val currency: String = "PHP",
    val openingBalance: Money = Money.ZERO,
    val currentLedgerBalance: Money = Money.ZERO,
    val iconName: String = "account_balance",
    val colorHex: String = "#1E88E5",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val includeInDashboardTotals: Boolean = true,
    val lastReconciledAt: Long? = null
)
