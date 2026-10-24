package com.ralledger.domain.model

data class CustomReport(
    val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val accountId: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
