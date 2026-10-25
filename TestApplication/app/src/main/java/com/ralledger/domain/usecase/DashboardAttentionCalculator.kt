package com.ralledger.domain.usecase

import com.ralledger.domain.model.Budget
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.PlannedPayment
import com.ralledger.domain.model.PlannedPaymentStatus
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

enum class DashboardAttentionKind(val priority: Int) {
    OVERDUE(0),
    OVER_BUDGET(1),
    DUE_SOON(2),
    UPCOMING(3)
}

enum class DashboardAttentionDestination {
    BUDGETS, RECEIVABLES, PLANNED_PAYMENTS, BILLS_AND_LOANS, SUBSCRIPTIONS
}

data class DashboardAttentionItem(
    val id: String,
    val title: String,
    val description: String,
    val amount: Money,
    val kind: DashboardAttentionKind,
    val destination: DashboardAttentionDestination,
    val dueDate: LocalDate? = null
)

/** Builds Home alerts only from current, actionable planning records. */
object DashboardAttentionCalculator {
    fun calculate(
        debts: List<Debt>,
        receivables: List<Receivable>,
        plannedPayments: List<PlannedPayment>,
        subscriptions: List<Subscription>,
        budgets: List<Budget>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        dueSoonDays: Long = 7,
        subscriptionDays: Long = 3
    ): List<DashboardAttentionItem> {
        require(dueSoonDays >= 0) { "Due-soon window cannot be negative" }
        require(subscriptionDays >= 0) { "Subscription window cannot be negative" }
        val endOfDueSoon = today.plusDays(dueSoonDays)
        val endOfSubscriptionWindow = today.plusDays(subscriptionDays)
        val currentMonth = YearMonth.from(today)
        val items = mutableListOf<DashboardAttentionItem>()

        debts.asSequence()
            .filter { !it.isArchived && it.outstandingAmount.isPositive && it.status in setOf(DebtStatus.OPEN, DebtStatus.PARTIALLY_PAID) }
            .forEach { debt ->
                val dueDate = debt.dueDate.toLocalDate(zoneId)
                dueDate.attentionKind(today, endOfDueSoon)?.let { kind ->
                    items += DashboardAttentionItem(
                        id = "debt:${debt.id}",
                        title = debt.title,
                        description = debt.counterparty.ifBlank { debt.kind.displayName },
                        amount = debt.outstandingAmount,
                        kind = kind,
                        destination = DashboardAttentionDestination.BILLS_AND_LOANS,
                        dueDate = dueDate
                    )
                }
            }

        receivables.asSequence()
            .filter {
                !it.isArchived && it.outstandingAmount.isPositive &&
                    it.status in setOf(ReceivableStatus.CONFIRMED, ReceivableStatus.PARTIALLY_PAID, ReceivableStatus.OVERDUE)
            }
            .forEach { receivable ->
                val dueDate = receivable.dueDate.toLocalDate(zoneId)
                dueDate.attentionKind(today, endOfDueSoon)?.let { kind ->
                    items += DashboardAttentionItem(
                        id = "receivable:${receivable.id}",
                        title = receivable.clientName,
                        description = receivable.description.ifBlank { "Money owed to you" },
                        amount = receivable.outstandingAmount,
                        kind = kind,
                        destination = DashboardAttentionDestination.RECEIVABLES,
                        dueDate = dueDate
                    )
                }
            }

        plannedPayments.asSequence()
            .filter { !it.isArchived && it.status == PlannedPaymentStatus.PLANNED && !it.isPaid && it.amount.isPositive }
            .forEach { payment ->
                val dueDate = payment.dueDate.toLocalDate(zoneId)
                dueDate.attentionKind(today, endOfDueSoon)?.let { kind ->
                    items += DashboardAttentionItem(
                        id = "planned-payment:${payment.id}",
                        title = payment.description,
                        description = payment.counterparty.ifBlank { payment.categoryName },
                        amount = payment.amount,
                        kind = kind,
                        destination = DashboardAttentionDestination.PLANNED_PAYMENTS,
                        dueDate = dueDate
                    )
                }
            }

        subscriptions.asSequence()
            .filter { !it.isArchived && it.status == SubscriptionStatus.ACTIVE && it.billingAmount.isPositive }
            .forEach { subscription ->
                val dueDate = subscription.nextBillingDate.toLocalDate(zoneId)
                if (!dueDate.isBefore(today) && !dueDate.isAfter(endOfSubscriptionWindow)) {
                    items += DashboardAttentionItem(
                        id = "subscription:${subscription.id}",
                        title = subscription.name,
                        description = "${subscription.billingFrequency.displayName} subscription",
                        amount = subscription.billingAmount,
                        kind = DashboardAttentionKind.UPCOMING,
                        destination = DashboardAttentionDestination.SUBSCRIPTIONS,
                        dueDate = dueDate
                    )
                }
            }

        budgets.asSequence()
            .filter { !it.isArchived && it.period == currentMonth && it.isOverBudget }
            .forEach { budget ->
                items += DashboardAttentionItem(
                    id = "budget:${budget.id}",
                    title = budget.categoryName,
                    description = "Monthly budget",
                    amount = budget.overspent,
                    kind = DashboardAttentionKind.OVER_BUDGET,
                    destination = DashboardAttentionDestination.BUDGETS
                )
            }

        return items.sortedWith(
            compareBy<DashboardAttentionItem> { it.kind.priority }
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun LocalDate.attentionKind(today: LocalDate, dueSoonEnd: LocalDate): DashboardAttentionKind? = when {
        isBefore(today) -> DashboardAttentionKind.OVERDUE
        !isAfter(dueSoonEnd) -> DashboardAttentionKind.DUE_SOON
        else -> null
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}
