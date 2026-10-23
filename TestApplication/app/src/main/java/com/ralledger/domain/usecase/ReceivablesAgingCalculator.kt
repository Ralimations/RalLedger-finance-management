package com.ralledger.domain.usecase

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReceivablesAgingBand(val label: String) {
    NOT_YET_DUE("Not yet due"),
    DUE_TODAY("Due today"),
    DAYS_1_TO_30("1–30 days overdue"),
    DAYS_31_TO_60("31–60 days overdue"),
    DAYS_61_TO_90("61–90 days overdue"),
    DAYS_91_PLUS("91+ days overdue")
}

data class ReceivablesAgingAmount(
    val band: ReceivablesAgingBand,
    val amount: Money = Money.ZERO,
    val receivableCount: Int = 0
)

/** Groups current open receivable balances by the age of their due date. */
object ReceivablesAgingCalculator {
    fun calculate(
        receivables: List<Receivable>,
        asOfDate: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<ReceivablesAgingAmount> {
        val totals = ReceivablesAgingBand.entries.associateWith { Money.ZERO }.toMutableMap()
        val counts = ReceivablesAgingBand.entries.associateWith { 0 }.toMutableMap()
        val closedStatuses = setOf(
            ReceivableStatus.DRAFT,
            ReceivableStatus.PAID,
            ReceivableStatus.CANCELLED,
            ReceivableStatus.WRITTEN_OFF
        )

        receivables.asSequence()
            .filter { !it.isArchived && it.outstandingAmount.isPositive && it.status !in closedStatuses }
            .forEach { receivable ->
                val dueDate = Instant.ofEpochMilli(receivable.dueDate).atZone(zone).toLocalDate()
                val daysOverdue = ChronoUnit.DAYS.between(dueDate, asOfDate)
                val band = when {
                    daysOverdue < 0 -> ReceivablesAgingBand.NOT_YET_DUE
                    daysOverdue == 0L -> ReceivablesAgingBand.DUE_TODAY
                    daysOverdue <= 30L -> ReceivablesAgingBand.DAYS_1_TO_30
                    daysOverdue <= 60L -> ReceivablesAgingBand.DAYS_31_TO_60
                    daysOverdue <= 90L -> ReceivablesAgingBand.DAYS_61_TO_90
                    else -> ReceivablesAgingBand.DAYS_91_PLUS
                }
                totals[band] = totals.getValue(band) + receivable.outstandingAmount
                counts[band] = counts.getValue(band) + 1
            }

        return ReceivablesAgingBand.entries.map { band ->
            ReceivablesAgingAmount(band, totals.getValue(band), counts.getValue(band))
        }
    }
}
