package com.ralledger.ui.planning

enum class PlanningQuickAction(
    val tabIndex: Int,
    val opensEditor: Boolean
) {
    ADD_BUDGET(tabIndex = 0, opensEditor = true),
    OPEN_BUDGETS(tabIndex = 0, opensEditor = false),
    ADD_RECEIVABLE(tabIndex = 1, opensEditor = true),
    OPEN_RECEIVABLES(tabIndex = 1, opensEditor = false),
    OPEN_PLANNED_PAYMENTS(tabIndex = 3, opensEditor = false),
    OPEN_BILLS_AND_LOANS(tabIndex = 4, opensEditor = false),
    ADD_EXPECTED_INCOME(tabIndex = 2, opensEditor = true),
    ADD_BILL(tabIndex = 4, opensEditor = true),
    ADD_LOAN(tabIndex = 4, opensEditor = true),
    ADD_SUBSCRIPTION(tabIndex = 5, opensEditor = true),
    OPEN_SUBSCRIPTIONS(tabIndex = 5, opensEditor = false),
    ADD_SAVINGS_GOAL(tabIndex = 6, opensEditor = true),
    OPEN_INTEREST_PRODUCTS(tabIndex = 7, opensEditor = false),
    OPEN_RECURRING_TRANSACTIONS(tabIndex = 11, opensEditor = false)
}
