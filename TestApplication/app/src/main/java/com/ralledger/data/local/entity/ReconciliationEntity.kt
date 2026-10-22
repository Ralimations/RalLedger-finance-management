package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Reconciliation

@Entity(tableName = "reconciliations")
data class ReconciliationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val timestamp: Long,
    val recordedBalanceCentavos: Long,
    val actualBalanceCentavos: Long,
    val differenceCentavos: Long,
    val adjustmentReason: String,
    val journalEntryId: String?
) {
    fun toDomainModel(): Reconciliation {
        return Reconciliation(
            id = id,
            accountId = accountId,
            timestamp = timestamp,
            recordedBalance = Money(recordedBalanceCentavos),
            actualBalance = Money(actualBalanceCentavos),
            difference = Money(differenceCentavos),
            adjustmentReason = adjustmentReason,
            journalEntryId = journalEntryId
        )
    }

    companion object {
        fun fromDomainModel(rec: Reconciliation): ReconciliationEntity {
            return ReconciliationEntity(
                id = rec.id,
                accountId = rec.accountId,
                timestamp = rec.timestamp,
                recordedBalanceCentavos = rec.recordedBalance.centavos,
                actualBalanceCentavos = rec.actualBalance.centavos,
                differenceCentavos = rec.difference.centavos,
                adjustmentReason = rec.adjustmentReason,
                journalEntryId = rec.journalEntryId
            )
        }
    }
}
