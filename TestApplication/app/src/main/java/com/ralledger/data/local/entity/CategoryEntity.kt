package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // CategoryType.name
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean
) {
    fun toDomainModel(): Category {
        return Category(
            id = id,
            name = name,
            type = CategoryType.valueOf(type),
            iconName = iconName,
            colorHex = colorHex,
            isDefault = isDefault
        )
    }

    companion object {
        fun fromDomainModel(category: Category): CategoryEntity {
            return CategoryEntity(
                id = category.id,
                name = category.name,
                type = category.type.name,
                iconName = category.iconName,
                colorHex = category.colorHex,
                isDefault = category.isDefault
            )
        }
    }
}
