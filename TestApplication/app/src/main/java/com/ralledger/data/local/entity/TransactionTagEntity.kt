package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.TransactionTag

@Entity(
    tableName = "transaction_tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class TransactionTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val colorHex: String,
    val createdAt: Long,
    val isArchived: Boolean
) {
    fun toDomainModel() = TransactionTag(
        id = id,
        name = name,
        colorHex = colorHex,
        createdAt = createdAt,
        isArchived = isArchived
    )

    companion object {
        fun fromDomainModel(tag: TransactionTag) = TransactionTagEntity(
            id = tag.id,
            name = tag.name,
            normalizedName = tag.name.trim().lowercase(java.util.Locale.ROOT),
            colorHex = tag.colorHex,
            createdAt = tag.createdAt,
            isArchived = tag.isArchived
        )
    }
}

@Entity(
    tableName = "journal_entry_tags",
    primaryKeys = ["journalEntryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class JournalEntryTagEntity(
    val journalEntryId: String,
    val tagId: String
)
