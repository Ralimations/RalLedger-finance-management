package com.ralledger.domain.model

import java.util.Locale

enum class FinancialProjectKind(val displayName: String) {
    PERSONAL("Personal"),
    BUSINESS("Business")
}

data class FinancialProject(
    val id: String,
    val name: String,
    val kind: FinancialProjectKind,
    val description: String = "",
    val colorHex: String = "#008577",
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizeFinancialProjectName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

fun displayFinancialProjectName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ")
