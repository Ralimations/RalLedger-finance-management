package com.ralledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.model.normalizeTransactionTemplateName

@Entity(
    tableName = "transaction_templates",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["feeCategoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["destinationAccountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["feeCategoryId"])
    ]
)
data class TransactionTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val transactionType: String,
    val amountCentavos: Long,
    val feeCentavos: Long,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val feeCategoryId: String?,
    val counterparty: String,
    val description: String,
    val notes: String,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel() = TransactionTemplate(
        id = id,
        name = name,
        transactionType = TransactionType.valueOf(transactionType),
        amount = Money(amountCentavos),
        fee = Money(feeCentavos),
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        feeCategoryId = feeCategoryId,
        counterparty = counterparty,
        description = description,
        notes = notes,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomainModel(template: TransactionTemplate) = TransactionTemplateEntity(
            id = template.id,
            name = template.name.trim(),
            normalizedName = normalizeTransactionTemplateName(template.name),
            transactionType = template.transactionType.name,
            amountCentavos = template.amount.centavos,
            feeCentavos = template.fee.centavos,
            accountId = template.accountId,
            destinationAccountId = template.destinationAccountId,
            categoryId = template.categoryId,
            feeCategoryId = template.feeCategoryId,
            counterparty = template.counterparty.trim(),
            description = template.description.trim(),
            notes = template.notes.trim(),
            isArchived = template.isArchived,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt
        )
    }
}
