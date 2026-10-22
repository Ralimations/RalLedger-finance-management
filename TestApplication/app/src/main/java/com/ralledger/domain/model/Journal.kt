package com.ralledger.domain.model

enum class LineType {
    DEBIT,
    CREDIT
}

data class JournalLine(
    val id: String,
    val journalEntryId: String,
    val accountId: String,
    val lineType: LineType,
    val amount: Money
)

data class JournalEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val lines: List<JournalLine>
) {
    fun isBalanced(): Boolean {
        val totalDebits = lines.filter { it.lineType == LineType.DEBIT }.sumOf { it.amount.centavos }
        val totalCredits = lines.filter { it.lineType == LineType.CREDIT }.sumOf { it.amount.centavos }
        return totalDebits == totalCredits && totalDebits > 0
    }
}
