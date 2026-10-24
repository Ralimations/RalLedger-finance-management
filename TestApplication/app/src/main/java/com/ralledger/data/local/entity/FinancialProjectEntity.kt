package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.FinancialProject
import com.ralledger.domain.model.FinancialProjectKind
import com.ralledger.domain.model.normalizeFinancialProjectName

@Entity(
    tableName = "financial_projects",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class FinancialProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val kind: String,
    val description: String,
    val colorHex: String,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel() = FinancialProject(
        id = id,
        name = name,
        kind = FinancialProjectKind.valueOf(kind),
        description = description,
        colorHex = colorHex,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomainModel(project: FinancialProject) = FinancialProjectEntity(
            id = project.id,
            name = project.name,
            normalizedName = normalizeFinancialProjectName(project.name),
            kind = project.kind.name,
            description = project.description,
            colorHex = project.colorHex,
            isArchived = project.isArchived,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt
        )
    }
}

@Entity(
    tableName = "journal_entry_projects",
    primaryKeys = ["journalEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FinancialProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class JournalEntryProjectEntity(
    val journalEntryId: String,
    val projectId: String
)
