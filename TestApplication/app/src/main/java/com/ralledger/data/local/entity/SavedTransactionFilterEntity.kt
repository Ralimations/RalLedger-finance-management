package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.SavedTransactionFilter
import com.ralledger.domain.model.TransactionFilter
import com.ralledger.domain.model.TransactionTypeFilter
import java.util.Locale

@Entity(
    tableName = "saved_transaction_filters",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class SavedTransactionFilterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val query: String,
    val transactionType: String,
    val accountId: String?,
    val categoryId: String?,
    val startInclusive: Long?,
    val endExclusive: Long?,
    val tagId: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel() = SavedTransactionFilter(
        id = id,
        name = name,
        filter = TransactionFilter(
            query = query,
            type = TransactionTypeFilter.valueOf(transactionType),
            accountId = accountId,
            categoryId = categoryId,
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            tagId = tagId
        ),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomainModel(saved: SavedTransactionFilter) = SavedTransactionFilterEntity(
            id = saved.id,
            name = saved.name.trim(),
            normalizedName = saved.name.trim().lowercase(Locale.ROOT),
            query = saved.filter.query,
            transactionType = saved.filter.type.name,
            accountId = saved.filter.accountId,
            categoryId = saved.filter.categoryId,
            startInclusive = saved.filter.startInclusive,
            endExclusive = saved.filter.endExclusive,
            tagId = saved.filter.tagId,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt
        )
    }
}
