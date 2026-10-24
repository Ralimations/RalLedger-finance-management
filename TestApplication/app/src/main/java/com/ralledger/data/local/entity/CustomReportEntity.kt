package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.CustomReport

@Entity(
    tableName = "custom_reports",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionTagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["tagId"])
    ]
)
data class CustomReportEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val startDate: Long,
    val endDate: Long,
    val accountId: String?,
    val categoryId: String?,
    val tagId: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel() = CustomReport(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        accountId = accountId,
        categoryId = categoryId,
        tagId = tagId,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomainModel(report: CustomReport) = CustomReportEntity(
            id = report.id,
            name = report.name,
            normalizedName = normalizeCustomReportName(report.name),
            startDate = report.startDate,
            endDate = report.endDate,
            accountId = report.accountId,
            categoryId = report.categoryId,
            tagId = report.tagId,
            isArchived = report.isArchived,
            createdAt = report.createdAt,
            updatedAt = report.updatedAt
        )
    }
}

fun normalizeCustomReportName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
