package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.InterestCredit
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.Money

@Entity(
    tableName = "interest_products",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["accountId"], unique = true), Index("type"), Index("maturityDate"), Index("isArchived")]
)
data class InterestProductEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val annualRateBasisPoints: Int,
    val taxRateBasisPoints: Int,
    val compounding: String,
    val openedAt: Long,
    val maturityDate: Long?,
    val principalCentavos: Long?,
    val notes: String,
    val isArchived: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "interest_credits",
    foreignKeys = [
        ForeignKey(entity = InterestProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["taxExpenseCategoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("productId"), Index(value = ["journalEntryId"], unique = true), Index("creditedAt"), Index("taxExpenseCategoryId")]
)
data class InterestCreditEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val journalEntryId: String,
    val grossCentavos: Long,
    val taxCentavos: Long,
    val annualRateBasisPoints: Int,
    val taxRateBasisPoints: Int,
    val taxExpenseCategoryId: String?,
    val creditedAt: Long,
    val notes: String,
    val reversedAt: Long?
)

fun InterestCreditEntity.toDomainModel() = InterestCredit(
    id = id,
    productId = productId,
    journalEntryId = journalEntryId,
    gross = Money(grossCentavos),
    tax = Money(taxCentavos),
    net = Money(Math.subtractExact(grossCentavos, taxCentavos)),
    annualRateBasisPoints = annualRateBasisPoints,
    taxRateBasisPoints = taxRateBasisPoints,
    taxExpenseCategoryId = taxExpenseCategoryId,
    creditedAt = creditedAt,
    notes = notes,
    reversedAt = reversedAt
)

fun InterestProductEntity.toDomainModel(
    accountName: String,
    accountBalance: Money,
    credits: List<InterestCredit>
) = InterestProduct(
    id = id,
    accountId = accountId,
    accountName = accountName,
    accountBalance = accountBalance,
    type = InterestProductType.valueOf(type),
    annualRateBasisPoints = annualRateBasisPoints,
    taxRateBasisPoints = taxRateBasisPoints,
    compounding = InterestCompounding.valueOf(compounding),
    openedAt = openedAt,
    maturityDate = maturityDate,
    principal = principalCentavos?.let(::Money),
    notes = notes,
    isArchived = isArchived,
    createdAt = createdAt,
    credits = credits
)
