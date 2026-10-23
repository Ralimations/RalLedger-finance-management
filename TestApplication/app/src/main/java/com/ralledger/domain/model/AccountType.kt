package com.ralledger.domain.model

enum class AccountCategory {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    EXPENSE
}

enum class AccountType(
    val displayName: String,
    val category: AccountCategory,
    val isLiquid: Boolean = false,
    val isReceivable: Boolean = false,
    val isPayable: Boolean = false
) {
    TRADITIONAL_BANK("Traditional Bank", AccountCategory.ASSET, isLiquid = true),
    DIGITAL_BANK("Digital Bank", AccountCategory.ASSET, isLiquid = true),
    E_WALLET("E-Wallet", AccountCategory.ASSET, isLiquid = true),
    PHYSICAL_WALLET("Physical Cash Wallet", AccountCategory.ASSET, isLiquid = true),
    PHYSICAL_SAVINGS("Physical Savings", AccountCategory.ASSET, isLiquid = false),
    TIME_DEPOSIT("Time Deposit", AccountCategory.ASSET, isLiquid = false),
    ACCOUNTS_RECEIVABLE("Accounts Receivable", AccountCategory.ASSET, isReceivable = true),

    ACCOUNTS_PAYABLE("Accounts Payable", AccountCategory.LIABILITY, isPayable = true),
    LIABILITY_ACCOUNT("Liability / Loan", AccountCategory.LIABILITY, isPayable = true),

    EQUITY("Equity / Opening Balance", AccountCategory.EQUITY),
    INCOME("Income Category", AccountCategory.INCOME),
    EXPENSE("Expense Category", AccountCategory.EXPENSE);

    val isAsset: Boolean get() = category == AccountCategory.ASSET
    val isLiability: Boolean get() = category == AccountCategory.LIABILITY
}
