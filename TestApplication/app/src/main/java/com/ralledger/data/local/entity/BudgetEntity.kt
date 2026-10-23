package com.ralledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["year", "month", "categoryId"], unique = true)
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val year: Int,
    val month: Int,
    val limitCentavos: Long,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false
)
