package com.ralledger.domain.usecase

import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.Budget
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.PlannedPayment
import com.ralledger.domain.model.PlannedPaymentStatus
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAttentionCalculatorTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun includesActionableDueItemsAndSortsOverdueFirst() {
        val overdueDebt = debt("late", "Internet", today.minusDays(2), Money(12_000))
        val dueReceivable = receivable("invoice", "A Client", today.plusDays(7), Money(80_000))
        val duePlan = plannedPayment("rent", today.plusDays(1), Money(100_000))
        val upcomingSubscription = subscription("music", today.plusDays(3), Money(1_000))
        val outsideWindow = subscription("later", today.plusDays(4), Money(1_000))

        val items = DashboardAttentionCalculator.calculate(
            debts = listOf(overdueDebt, overdueDebt.copy(id = "archived", isArchived = true)),
            receivables = listOf(dueReceivable, dueReceivable.copy(id = "paid", status = ReceivableStatus.PAID)),
            plannedPayments = listOf(duePlan, duePlan.copy(id = "cancelled", status = PlannedPaymentStatus.CANCELLED)),
            subscriptions = listOf(upcomingSubscription, outsideWindow),
            budgets = emptyList(),
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(listOf("debt:late", "planned-payment:rent", "receivable:invoice", "subscription:music"), items.map { it.id })
        assertEquals(DashboardAttentionKind.OVERDUE, items.first().kind)
        assertEquals(DashboardAttentionKind.DUE_SOON, items[1].kind)
        assertEquals(DashboardAttentionKind.UPCOMING, items.last().kind)
    }

    @Test
    fun includesOnlyActiveCurrentMonthOverspending() {
        val current = budget("current", year = 2026, month = 10, spent = 12_000, limit = 10_000)
        val under = budget("under", year = 2026, month = 10, spent = 9_000, limit = 10_000)
        val prior = budget("prior", year = 2026, month = 9, spent = 20_000, limit = 10_000)

        val items = DashboardAttentionCalculator.calculate(
            debts = emptyList(), receivables = emptyList(), plannedPayments = emptyList(), subscriptions = emptyList(),
            budgets = listOf(current, under, prior, current.copy(id = "archived", isArchived = true)),
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(listOf("budget:current"), items.map { it.id })
        assertEquals(Money(2_000), items.single().amount)
        assertEquals(DashboardAttentionKind.OVER_BUDGET, items.single().kind)
    }

    private fun debt(id: String, title: String, due: LocalDate, amount: Money) = Debt(
        id = id,
        title = title,
        counterparty = "Provider",
        kind = DebtKind.BILL,
        originalAmount = amount,
        outstandingAmount = amount,
        dueDate = due.epochMillis(),
        categoryId = null,
        categoryName = null,
        fundingAccountId = null,
        recognitionJournalEntryId = "entry-$id",
        status = DebtStatus.OPEN,
        isArchived = false,
        createdAt = 1L
    )

    private fun receivable(id: String, client: String, due: LocalDate, amount: Money) = Receivable(
        id = id,
        clientName = client,
        originalAmount = amount,
        outstandingAmount = amount,
        dueDate = due.epochMillis(),
        status = ReceivableStatus.CONFIRMED
    )

    private fun plannedPayment(id: String, due: LocalDate, amount: Money) = PlannedPayment(
        id = id,
        description = "Planned expense",
        counterparty = "Landlord",
        amount = amount,
        dueDate = due.epochMillis(),
        expenseCategoryId = "housing",
        categoryName = "Housing",
        status = PlannedPaymentStatus.PLANNED,
        isArchived = false,
        createdAt = 1L
    )

    private fun subscription(id: String, due: LocalDate, amount: Money) = Subscription(
        id = id,
        name = "Subscription $id",
        billingAmount = amount,
        billingFrequency = BillingFrequency.MONTHLY,
        assignedAccountId = "cash",
        nextBillingDate = due.epochMillis(),
        status = SubscriptionStatus.ACTIVE
    )

    private fun budget(id: String, year: Int, month: Int, spent: Long, limit: Long) = Budget(
        id = id,
        categoryId = "category-$id",
        categoryName = "Category $id",
        year = year,
        month = month,
        limit = Money(limit),
        spent = Money(spent),
        isArchived = false
    )

    private fun LocalDate.epochMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
