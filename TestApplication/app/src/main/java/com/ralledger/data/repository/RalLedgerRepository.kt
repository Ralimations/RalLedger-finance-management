package com.ralledger.data.repository

import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.local.entity.JournalEntryEntity
import com.ralledger.data.local.entity.JournalLineEntity
import com.ralledger.data.local.entity.ReceivableEntity
import com.ralledger.data.local.entity.ReceivablePaymentEntity
import com.ralledger.data.local.entity.ReconciliationEntity
import com.ralledger.data.local.entity.SubscriptionEntity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountCategory
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.Reconciliation
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionStatus
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.usecase.AccountingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class RalLedgerRepository(
    private val database: RalLedgerDatabase
) {
    private val accountDao = database.accountDao()
    private val journalDao = database.journalDao()
    private val categoryDao = database.categoryDao()
    private val receivableDao = database.receivableDao()
    private val subscriptionDao = database.subscriptionDao()
    private val reconciliationDao = database.reconciliationDao()

    // Dynamically calculate ledger balances for all accounts from journal line aggregates
    val accountsFlow: Flow<List<Account>> = combine(
        accountDao.getAllAccountsFlow(),
        journalDao.getAccountBalanceAggregatesFlow()
    ) { accountEntities, aggregates ->
        val balanceMap = mutableMapOf<String, Long>()

        aggregates.forEach { agg ->
            val accountId = agg.accountId
            val current = balanceMap.getOrDefault(accountId, 0L)
            if (agg.lineType == LineType.DEBIT.name) {
                balanceMap[accountId] = current + agg.totalCentavos
            } else {
                balanceMap[accountId] = current - agg.totalCentavos
            }
        }

        accountEntities
            .filter { it.id != RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID && it.id != RalLedgerDatabase.SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID }
            .map { entity ->
                val accountType = AccountType.valueOf(entity.accountType)
                val rawBalance = balanceMap.getOrDefault(entity.id, 0L)

                // For Asset and Expense: Balance = Debit - Credit (rawBalance)
                // For Liability, Equity, Income: Balance = Credit - Debit (-rawBalance)
                val centavos = if (accountType.category == AccountCategory.ASSET || accountType.category == AccountCategory.EXPENSE) {
                    rawBalance
                } else {
                    -rawBalance
                }

                entity.toDomainModel(Money(centavos))
            }
    }

    suspend fun getAccountById(id: String): Account? {
        val entity = accountDao.getAccountById(id) ?: return null
        val aggregates = journalDao.getBalanceAggregateForAccount(id)
        var rawBalance = 0L
        aggregates.forEach { agg ->
            if (agg.lineType == LineType.DEBIT.name) rawBalance += agg.totalCentavos
            else rawBalance -= agg.totalCentavos
        }

        val accountType = AccountType.valueOf(entity.accountType)
        val centavos = if (accountType.category == AccountCategory.ASSET || accountType.category == AccountCategory.EXPENSE) {
            rawBalance
        } else {
            -rawBalance
        }

        return entity.toDomainModel(Money(centavos))
    }

    suspend fun createAccount(account: Account) {
        val entity = AccountEntity.fromDomainModel(account)
        accountDao.insertAccount(entity)

        // Post opening balance if positive
        if (account.openingBalance.isPositive) {
            val entry = AccountingEngine.createOpeningBalanceEntry(
                accountId = account.id,
                accountType = account.accountType,
                openingBalance = account.openingBalance,
                timestamp = account.createdAt
            )
            postJournalEntry(entry, TransactionType.OPENING_BALANCE, account.openingBalance, Money.ZERO, account.id, null, null, "", "Opening Balance")
        }
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(AccountEntity.fromDomainModel(account))
    }

    suspend fun setAccountArchived(id: String, isArchived: Boolean) {
        accountDao.setArchived(id, isArchived)
    }

    // Categories
    val categoriesFlow: Flow<List<Category>> = categoryDao.getAllCategoriesFlow().map { list ->
        list.map { it.toDomainModel() }
    }

    suspend fun createCategory(category: Category) {
        categoryDao.insertCategory(CategoryEntity.fromDomainModel(category))
    }

    // Transactions
    val transactionsFlow: Flow<List<Transaction>> = combine(
        journalDao.getAllJournalEntriesFlow(),
        accountDao.getAllAccountsFlow(),
        categoryDao.getAllCategoriesFlow()
    ) { entries, accounts, categories ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        entries.map { entry ->
            val accountName = accountMap[entry.primaryAccountId]?.name ?: "Account"
            val destAccountName = entry.destinationAccountId?.let { accountMap[it]?.name }
            val catName = entry.categoryId?.let { categoryMap[it]?.name }

            Transaction(
                id = entry.id,
                journalEntryId = entry.id,
                timestamp = entry.timestamp,
                transactionType = TransactionType.valueOf(entry.transactionType),
                amount = Money(entry.amountCentavos),
                fee = Money(entry.feeCentavos),
                accountId = entry.primaryAccountId,
                accountName = accountName,
                destinationAccountId = entry.destinationAccountId,
                destinationAccountName = destAccountName,
                categoryId = entry.categoryId,
                categoryName = catName,
                counterparty = entry.counterparty,
                description = entry.description,
                notes = entry.notes
            )
        }
    }

    private suspend fun postJournalEntry(
        entry: JournalEntry,
        transactionType: TransactionType,
        amount: Money,
        fee: Money = Money.ZERO,
        primaryAccountId: String,
        destinationAccountId: String? = null,
        categoryId: String? = null,
        counterparty: String = "",
        notes: String = ""
    ) {
        val entryEntity = JournalEntryEntity(
            id = entry.id,
            timestamp = entry.timestamp,
            description = entry.description,
            transactionType = transactionType.name,
            amountCentavos = amount.centavos,
            feeCentavos = fee.centavos,
            primaryAccountId = primaryAccountId,
            destinationAccountId = destinationAccountId,
            categoryId = categoryId,
            counterparty = counterparty,
            notes = notes
        )

        val lineEntities = entry.lines.map { line ->
            JournalLineEntity(
                id = line.id,
                journalEntryId = line.journalEntryId,
                accountId = line.accountId,
                lineType = line.lineType.name,
                amountCentavos = line.amount.centavos
            )
        }

        journalDao.insertJournalEntry(entryEntity)
        journalDao.insertJournalLines(lineEntities)
    }

    suspend fun recordIncome(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        val entry = AccountingEngine.createIncomeEntry(accountId, categoryId, amount, description, timestamp)
        postJournalEntry(entry, TransactionType.INCOME, amount, Money.ZERO, accountId, null, categoryId, counterparty, notes)
    }

    suspend fun recordExpense(
        accountId: String,
        categoryId: String,
        amount: Money,
        description: String,
        counterparty: String = "",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        val entry = AccountingEngine.createExpenseEntry(accountId, categoryId, amount, description, timestamp)
        postJournalEntry(entry, TransactionType.EXPENSE, amount, Money.ZERO, accountId, null, categoryId, counterparty, notes)
    }

    suspend fun recordTransfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: Money,
        fee: Money = Money.ZERO,
        feeCategoryId: String? = null,
        description: String,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        val entry = AccountingEngine.createTransferEntry(sourceAccountId, destinationAccountId, amount, fee, feeCategoryId, description, timestamp)
        postJournalEntry(entry, TransactionType.TRANSFER, amount, fee, sourceAccountId, destinationAccountId, feeCategoryId, "", notes)
    }

    suspend fun deleteTransaction(journalEntryId: String) {
        journalDao.deleteJournalEntry(journalEntryId)
    }

    // Receivables
    val receivablesFlow: Flow<List<Receivable>> = combine(
        receivableDao.getAllReceivablesFlow(),
        receivableDao.getAllPaymentsFlow()
    ) { receivables, allPayments ->
        val paymentGroup = allPayments.groupBy { it.receivableId }

        receivables.map { recEntity ->
            val payments = paymentGroup[recEntity.id]?.map { it.toDomainModel() } ?: emptyList()
            recEntity.toDomainModel(payments)
        }
    }

    suspend fun createReceivable(
        clientName: String,
        amount: Money,
        dueDate: Long,
        incomeCategoryId: String?,
        description: String
    ): Receivable {
        val id = UUID.randomUUID().toString()
        val recEntity = ReceivableEntity(
            id = id,
            clientName = clientName,
            originalAmountCentavos = amount.centavos,
            outstandingAmountCentavos = amount.centavos,
            recognitionDate = System.currentTimeMillis(),
            dueDate = dueDate,
            linkedCategoryId = incomeCategoryId,
            description = description,
            status = ReceivableStatus.CONFIRMED.name
        )
        receivableDao.insertReceivable(recEntity)
        return recEntity.toDomainModel(emptyList())
    }

    suspend fun collectReceivable(
        receivableId: String,
        destinationAccountId: String,
        paymentAmount: Money,
        notes: String
    ) {
        val receivable = receivableDao.getReceivableById(receivableId) ?: return
        val newOutstanding = (Money(receivable.outstandingAmountCentavos) - paymentAmount).centavos.coerceAtLeast(0L)

        val newStatus = when {
            newOutstanding == 0L -> ReceivableStatus.PAID.name
            else -> ReceivableStatus.PARTIALLY_PAID.name
        }

        // Post Collection Journal Entry
        val entry = AccountingEngine.createReceivableCollectionEntry(
            destinationAccountId = destinationAccountId,
            receivableAccountId = receivable.id,
            amount = paymentAmount,
            description = "Receivable payment from ${receivable.clientName}"
        )

        postJournalEntry(entry, TransactionType.RECEIVABLE_COLLECTION, paymentAmount, Money.ZERO, destinationAccountId, receivable.id, receivable.linkedCategoryId, receivable.clientName, notes)

        val paymentEntity = ReceivablePaymentEntity(
            id = UUID.randomUUID().toString(),
            receivableId = receivableId,
            journalEntryId = entry.id,
            amountCentavos = paymentAmount.centavos,
            destinationAccountId = destinationAccountId,
            paymentDate = System.currentTimeMillis(),
            notes = notes
        )
        receivableDao.insertPayment(paymentEntity)

        receivableDao.updateReceivable(
            receivable.copy(
                outstandingAmountCentavos = newOutstanding,
                status = newStatus
            )
        )
    }

    // Subscriptions
    val subscriptionsFlow: Flow<List<Subscription>> = combine(
        subscriptionDao.getAllSubscriptionsFlow(),
        accountDao.getAllAccountsFlow()
    ) { subs, accounts ->
        val accountMap = accounts.associateBy { it.id }
        subs.map { subEntity ->
            val accountName = accountMap[subEntity.assignedAccountId]?.name ?: "Assigned Account"
            subEntity.toDomainModel(accountName)
        }
    }

    suspend fun createSubscription(subscription: Subscription) {
        subscriptionDao.insertSubscription(SubscriptionEntity.fromDomainModel(subscription))
    }

    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(SubscriptionEntity.fromDomainModel(subscription))
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        subscriptionDao.deleteSubscription(SubscriptionEntity.fromDomainModel(subscription))
    }

    // Reconciliation
    suspend fun reconcileAccount(
        accountId: String,
        actualBalance: Money,
        reason: String
    ) {
        val currentAccount = getAccountById(accountId) ?: return
        val recordedBalance = currentAccount.currentLedgerBalance
        val difference = actualBalance - recordedBalance

        var journalId: String? = null

        if (!difference.isZero) {
            val entry = AccountingEngine.createReconciliationAdjustmentEntry(
                accountId = accountId,
                difference = difference,
                description = "Reconciliation adjustment: $reason"
            )
            postJournalEntry(entry, TransactionType.BALANCE_ADJUSTMENT, difference, Money.ZERO, accountId, null, null, "Reconciliation", reason)
            journalId = entry.id
        }

        val recEntity = ReconciliationEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            timestamp = System.currentTimeMillis(),
            recordedBalanceCentavos = recordedBalance.centavos,
            actualBalanceCentavos = actualBalance.centavos,
            differenceCentavos = difference.centavos,
            adjustmentReason = reason,
            journalEntryId = journalId
        )
        reconciliationDao.insertReconciliation(recEntity)
        accountDao.updateLastReconciledAt(accountId, System.currentTimeMillis())
    }

    fun getReconciliationsFlow(accountId: String): Flow<List<Reconciliation>> {
        return reconciliationDao.getReconciliationsForAccountFlow(accountId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    // Dashboard Summary Calculation
    val dashboardSummaryFlow: Flow<DashboardSummary> = combine(
        accountsFlow,
        receivablesFlow,
        subscriptionsFlow,
        transactionsFlow
    ) { accounts, receivables, subscriptions, transactions ->
        val dashboardAccounts = accounts.filter { it.includeInDashboardTotals && !it.isArchived }

        val liquidCash = dashboardAccounts
            .filter { it.accountType.isLiquid }
            .fold(Money.ZERO) { acc, a -> acc + a.currentLedgerBalance }

        val reservedSavings = dashboardAccounts
            .filter { it.accountType == AccountType.PHYSICAL_SAVINGS }
            .fold(Money.ZERO) { acc, a -> acc + a.currentLedgerBalance }

        val availableToSpend = liquidCash

        val totalReceivables = receivables
            .filter { it.status == ReceivableStatus.CONFIRMED || it.status == ReceivableStatus.PARTIALLY_PAID || it.status == ReceivableStatus.OVERDUE }
            .fold(Money.ZERO) { acc, r -> acc + r.outstandingAmount }

        val totalLiabilities = dashboardAccounts
            .filter { it.accountType.isLiability }
            .fold(Money.ZERO) { acc, a -> acc + a.currentLedgerBalance }

        val totalAssets = liquidCash + reservedSavings + totalReceivables

        val netWorth = totalAssets - totalLiabilities

        val startOfMonth = getStartOfMonthTimestamp()

        val monthlyIncome = transactions
            .filter { it.timestamp >= startOfMonth && it.transactionType == TransactionType.INCOME }
            .fold(Money.ZERO) { acc, t -> acc + t.amount }

        val monthlyExpenses = transactions
            .filter { it.timestamp >= startOfMonth && it.transactionType == TransactionType.EXPENSE }
            .fold(Money.ZERO) { acc, t -> acc + t.amount }

        val monthlySubs = subscriptions
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .fold(Money.ZERO) { acc, s -> acc + s.billingAmount }

        DashboardSummary(
            liquidCashAndBankBalances = liquidCash,
            availableToSpendFunds = availableToSpend,
            reservedSavings = reservedSavings,
            accountsReceivableTotal = totalReceivables,
            accountsPayableTotal = totalLiabilities,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = netWorth,
            monthlyIncomeReceived = monthlyIncome,
            monthlyExpensesPaid = monthlyExpenses,
            netCashFlow = monthlyIncome - monthlyExpenses,
            activeSubscriptionsMonthlyTotal = monthlySubs
        )
    }

    private fun getStartOfMonthTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
