package com.ralledger.domain.model

import java.util.Locale

enum class CounterpartyKind(val displayName: String) {
    CLIENT("Client"),
    SUPPLIER("Supplier"),
    BOTH("Client and supplier"),
    OTHER("Other")
}

data class Counterparty(
    val id: String,
    val name: String,
    val kind: CounterpartyKind,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long
)

fun normalizeCounterpartyName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
