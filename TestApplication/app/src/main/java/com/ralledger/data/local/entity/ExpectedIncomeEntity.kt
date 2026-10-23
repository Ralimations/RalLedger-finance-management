package com.ralledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.ExpectedIncome
import com.ralledger.domain.model.ExpectedIncomeStatus
import com.ralledger.domain.model.Money

@Entity(
    tableName = "expected_income",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["incomeCategoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ReceivableEntity::class,
            parentColumns = ["id"],
            childColumns = ["convertedReceivableId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterpartyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["incomeCategoryId"]),
        Index(value = ["convertedReceivableId"], unique = true),
        Index(value = ["expectedDate"]),
        Index(value = ["counterpartyId"])
    ]
)
data class ExpectedIncomeEntity(
    @PrimaryKey val id: String,
    val description: String,
    val counterparty: String,
    val amountCentavos: Long,
    val expectedDate: Long,
    val incomeCategoryId: String,
    val status: String,
    val convertedReceivableId: String? = null,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val counterpartyId: String? = null
) {
    fun toDomainModel(incomeSourceName: String, currentCounterpartyName: String? = null) = ExpectedIncome(
        id = id,
        description = description,
        counterparty = currentCounterpartyName ?: counterparty,
        counterpartyId = counterpartyId,
        amount = Money(amountCentavos),
        expectedDate = expectedDate,
        incomeCategoryId = incomeCategoryId,
        incomeSourceName = incomeSourceName,
        status = ExpectedIncomeStatus.valueOf(status),
        convertedReceivableId = convertedReceivableId,
        isArchived = isArchived,
        createdAt = createdAt
    )
}
