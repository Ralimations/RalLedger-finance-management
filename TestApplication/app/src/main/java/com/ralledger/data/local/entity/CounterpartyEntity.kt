package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.CounterpartyKind

@Entity(
    tableName = "counterparties",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["isArchived"])
    ]
)
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val kind: String,
    val notes: String,
    val isArchived: Boolean = false,
    val createdAt: Long
) {
    fun toDomainModel() = Counterparty(
        id = id,
        name = name,
        kind = CounterpartyKind.valueOf(kind),
        notes = notes,
        isArchived = isArchived,
        createdAt = createdAt
    )
}
