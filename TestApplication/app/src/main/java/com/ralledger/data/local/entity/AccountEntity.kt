package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val institution: String,
    val accountType: String, // AccountType.name
    val currency: String,
    val openingBalanceCentavos: Long,
    val iconName: String,
    val colorHex: String,
    val createdAt: Long,
    val isArchived: Boolean,
    val includeInDashboardTotals: Boolean,
    val lastReconciledAt: Long?,
    @ColumnInfo(defaultValue = "''") val description: String = ""
) {
    fun toDomainModel(calculatedLedgerBalance: Money): Account {
        return Account(
            id = id,
            name = name,
            institution = institution,
            accountType = AccountType.valueOf(accountType),
            currency = currency,
            openingBalance = Money(openingBalanceCentavos),
            currentLedgerBalance = calculatedLedgerBalance,
            iconName = iconName,
            colorHex = colorHex,
            createdAt = createdAt,
            isArchived = isArchived,
            includeInDashboardTotals = includeInDashboardTotals,
            lastReconciledAt = lastReconciledAt,
            description = description
        )
    }

    companion object {
        fun fromDomainModel(account: Account): AccountEntity {
            return AccountEntity(
                id = account.id,
                name = account.name,
                institution = account.institution,
                accountType = account.accountType.name,
                currency = account.currency,
                openingBalanceCentavos = account.openingBalance.centavos,
                iconName = account.iconName,
                colorHex = account.colorHex,
                createdAt = account.createdAt,
                isArchived = account.isArchived,
                includeInDashboardTotals = account.includeInDashboardTotals,
                lastReconciledAt = account.lastReconciledAt,
                description = account.description
            )
        }
    }
}
