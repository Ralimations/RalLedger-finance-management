package com.ralledger.domain.model

import java.math.BigInteger

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
        if (lines.size < 2 || lines.any { !it.amount.isPositive }) return false
        val totalDebits = lines.asSequence()
            .filter { it.lineType == LineType.DEBIT }
            .fold(BigInteger.ZERO) { total, line -> total + BigInteger.valueOf(line.amount.centavos) }
        val totalCredits = lines.asSequence()
            .filter { it.lineType == LineType.CREDIT }
            .fold(BigInteger.ZERO) { total, line -> total + BigInteger.valueOf(line.amount.centavos) }
        return totalDebits == totalCredits && totalDebits.signum() > 0
    }
}
