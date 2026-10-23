package com.ralledger.domain.model

enum class TransactionType(val displayName: String) {
    INCOME("Income"),
    INTEREST_CREDIT("Interest Credit"),
    EXPENSE("Expense"),
    TRANSFER("Transfer"),
    RECEIVABLE_RECOGNITION("Receivable Recognized"),
    RECEIVABLE_COLLECTION("Receivable Collection"),
    RECEIVABLE_WRITEOFF("Receivable Write-off"),
    PAYABLE_RECOGNITION("Bill Recorded"),
    PAYABLE_PAYMENT("Bill Payment"),
    LOAN_DISBURSEMENT("Loan Disbursement"),
    LOAN_REPAYMENT("Loan Repayment"),
    OPENING_BALANCE("Opening Balance"),
    BALANCE_ADJUSTMENT("Reconciliation Adjustment"),
    REVERSAL("Reversal")
}
