package com.ralledger.domain.model

enum class TransactionType(val displayName: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    TRANSFER("Transfer"),
    RECEIVABLE_RECOGNITION("Receivable Recognized"),
    RECEIVABLE_COLLECTION("Receivable Collection"),
    LOAN_DISBURSEMENT("Loan Disbursement"),
    LOAN_REPAYMENT("Loan Repayment"),
    OPENING_BALANCE("Opening Balance"),
    BALANCE_ADJUSTMENT("Reconciliation Adjustment"),
    REVERSAL("Reversal")
}
