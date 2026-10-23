package com.ralledger.domain.usecase

import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceivablesAgingCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val asOf = LocalDate.of(2026, 10, 23)

    @Test
    fun groupsOpenBalancesAcrossDueDateBandsAndKeepsCounts() {
        val results = ReceivablesAgingCalculator.calculate(
            listOf(
                receivable("future", asOf.plusDays(1), 10_000),
                receivable("today", asOf, 20_000),
                receivable("day30", asOf.minusDays(30), 30_000, ReceivableStatus.OVERDUE),
                receivable("day31", asOf.minusDays(31), 40_000, ReceivableStatus.OVERDUE),
                receivable("day60", asOf.minusDays(60), 50_000, ReceivableStatus.OVERDUE),
                receivable("day61", asOf.minusDays(61), 60_000, ReceivableStatus.OVERDUE),
                receivable("day90", asOf.minusDays(90), 70_000, ReceivableStatus.OVERDUE),
                receivable("day91", asOf.minusDays(91), 80_000, ReceivableStatus.OVERDUE)
            ),
            asOf,
            zone
        ).associateBy { it.band }

        assertEquals(Money(10_000), results.getValue(ReceivablesAgingBand.NOT_YET_DUE).amount)
        assertEquals(Money(20_000), results.getValue(ReceivablesAgingBand.DUE_TODAY).amount)
        assertEquals(Money(30_000), results.getValue(ReceivablesAgingBand.DAYS_1_TO_30).amount)
        assertEquals(2, results.getValue(ReceivablesAgingBand.DAYS_31_TO_60).receivableCount)
        assertEquals(Money(90_000), results.getValue(ReceivablesAgingBand.DAYS_31_TO_60).amount)
        assertEquals(Money(130_000), results.getValue(ReceivablesAgingBand.DAYS_61_TO_90).amount)
        assertEquals(Money(80_000), results.getValue(ReceivablesAgingBand.DAYS_91_PLUS).amount)
    }

    @Test
    fun excludesClosedArchivedAndZeroBalanceReceivables() {
        val results = ReceivablesAgingCalculator.calculate(
            listOf(
                receivable("paid", asOf.minusDays(10), 1_000, ReceivableStatus.PAID),
                receivable("cancelled", asOf.minusDays(10), 1_000, ReceivableStatus.CANCELLED),
                receivable("written-off", asOf.minusDays(10), 1_000, ReceivableStatus.WRITTEN_OFF),
                receivable("draft", asOf.minusDays(10), 1_000, ReceivableStatus.DRAFT),
                receivable("archived", asOf.minusDays(10), 1_000, ReceivableStatus.OVERDUE).copy(isArchived = true),
                receivable("zero", asOf.minusDays(10), 0, ReceivableStatus.OVERDUE)
            ),
            asOf,
            zone
        ).associateBy { it.band }

        assertEquals(Money.ZERO, results.getValue(ReceivablesAgingBand.DAYS_1_TO_30).amount)
        assertEquals(0, results.getValue(ReceivablesAgingBand.DAYS_1_TO_30).receivableCount)
    }

    private fun receivable(
        id: String,
        dueDate: LocalDate,
        amount: Long,
        status: ReceivableStatus = ReceivableStatus.CONFIRMED
    ) = Receivable(
        id = id,
        clientName = "Client",
        originalAmount = Money(amount),
        outstandingAmount = Money(amount),
        dueDate = dueDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        status = status
    )
}
