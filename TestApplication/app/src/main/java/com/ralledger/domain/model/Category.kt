package com.ralledger.domain.model

enum class CategoryType {
    INCOME,
    EXPENSE,
    TRANSFER,
    SYSTEM
}

data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val iconName: String = "label",
    val colorHex: String = "#757575",
    val isDefault: Boolean = false
)
