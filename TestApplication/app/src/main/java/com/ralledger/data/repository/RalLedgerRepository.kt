package com.ralledger.data.repository

import androidx.room.withTransaction
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
import com.ralledger.domain.model.CategoryType
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
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.usecase.AccountingEngine
import com.ralledger.domain.usecase.DashboardSummaryCalculator
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
        require(account.openingBalance.isZero || account.openingBalance.isPositive) { "Opening balance cannot be negative" }
        require(account.accountType.isAsset || account.accountType.isLiability) { "A financial account must be an asset or liability" }
        database.withTransaction {
            accountDao.insertAccount(entity)

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
    }

    suspend fun updateAccountMetadata(
        id: String,
        name: String,
        institution: String,
        description: String,
        iconName: String,
        colorHex: String,
        includeInDashboardTotals: Boolean
    ) {
        require(name.isNotBlank()) { "Account name is required" }
        require(colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Choose a valid account color" }
        database.withTransaction {
            val current = accountDao.getAccountById(id)
                ?: throw IllegalArgumentException("This account no longer exists")
            require(!isSystemAccount(id)) { "System accounts cannot be edited" }
            accountDao.updateAccount(
                current.copy(
                    name = name.trim(),
                    institution = institution.trim(),
                    description = description.trim(),
                    iconName = iconName,
                    colorHex = colorHex.uppercase(),
                    includeInDashboardTotals = includeInDashboardTotals
                )
            )
        }
    }

    suspend fun setAccountArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            require(!isSystemAccount(id)) { "System accounts cannot be changed" }
            require(accountDao.getAccountById(id) != null) { "This account no longer exists" }
            accountDao.setArchived(id, isArchived)
        }
    }

    suspend fun deleteUnusedAccount(id: String) {
        database.withTransaction {
            require(!isSystemAccount(id)) { "System accounts cannot be deleted" }
            val account = accountDao.getAccountById(id)
                ?: throw IllegalArgumentException("This account no longer exists")
            val referenceCount = journalDao.countAccountReferences(id) +
                reconciliationDao.countAccountReferences(id) +
                receivableDao.countPaymentsForAccount(id) +
                subscriptionDao.countAccountReferences(id)
            require(referenceCount == 0L && account.openingBalanceCentavos == 0L) {
                "This account has financial history or linked records and cannot be permanently deleted. Archive it to keep its history safe."
            }
            accountDao.deleteAccount(id)
        }
    }

    private fun isSystemAccount(id: String): Boolean =
        id == RalLedgerDatabase.SYSTEM_OPENING_EQUITY_ACCOUNT_ID ||
            id == RalLedgerDatabase.SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID

    fun getAccountTransactionsFlow(accountId: String): Flow<List<Transaction>> = transactionsFlow.map { transactions ->
        transactions.filter { it.accountId == accountId || it.destinationAccountId == accountId }
    }

    // Categories
    val categoriesFlow: Flow<List<Category>> = categoryDao.getAllCategoriesFlow().map { list ->
        list.map { it.toDomainModel() }
    }

    suspend fun createCategory(category: Category) {
        validateCategoryType(category.type)
        validateCategoryMetadata(category.name, category.colorHex)
        database.withTransaction {
            validateCategoryName(category.type, category.name, category.id)
            validateCategoryParent(category.parentCategoryId, category.type)
            categoryDao.insertCategory(CategoryEntity.fromDomainModel(category.copy(isDefault = false)))
        }
    }

    suspend fun updateCategoryMetadata(
        id: String,
        name: String,
        iconName: String,
        colorHex: String,
        parentCategoryId: String?
    ) {
        validateCategoryMetadata(name, colorHex)
        database.withTransaction {
            val current = categoryDao.getCategoryById(id)
                ?: throw IllegalArgumentException("This category no longer exists")
            requireEditableCategory(current)
            validateCategoryName(CategoryType.valueOf(current.type), name, id)
            if (parentCategoryId != current.parentCategoryId) {
                require(categoryDao.countChildren(id) == 0L || parentCategoryId == null) {
                    "Move or archive this category's subcategories before making it a subcategory"
                }
            }
            validateCategoryParent(parentCategoryId, CategoryType.valueOf(current.type), id)
            categoryDao.updateCategory(
                current.copy(
                    name = name.trim(),
                    iconName = iconName,
                    colorHex = colorHex.uppercase(),
                    parentCategoryId = parentCategoryId
                )
            )
        }
    }

    suspend fun setCategoryArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val category = categoryDao.getCategoryById(id)
                ?: throw IllegalArgumentException("This category no longer exists")
            requireEditableCategory(category)
            if (isArchived && categoryDao.countActiveChildren(id) > 0L) {
                throw IllegalArgumentException("Archive this category's subcategories first")
            }
            categoryDao.setArchived(id, isArchived)
        }
    }

    suspend fun deleteUnusedCategory(id: String) {
        database.withTransaction {
            val category = categoryDao.getCategoryById(id)
                ?: throw IllegalArgumentException("This category no longer exists")
            requireEditableCategory(category)
            require(journalDao.countCategoryReferences(id) == 0L && categoryDao.countChildren(id) == 0L) {
                "This category has transaction history or subcategories. Archive it to preserve its history."
            }
            categoryDao.deleteCategory(id)
        }
    }

    suspend fun mergeCategory(sourceId: String, targetId: String) {
        database.withTransaction {
            require(sourceId != targetId) { "Choose a different category to merge into" }
            val source = categoryDao.getCategoryById(sourceId)
                ?: throw IllegalArgumentException("The category to merge no longer exists")
            val target = categoryDao.getCategoryById(targetId)
                ?: throw IllegalArgumentException("Choose an existing destination category")
            requireEditableCategory(source)
            requireEditableCategory(target)
            require(source.type == target.type && !target.isArchived && target.parentCategoryId == null) {
                "Choose an active top-level category of the same type"
            }
            journalDao.reassignEntryCategory(sourceId, targetId)
            journalDao.reassignCategoryLedgerLines(sourceId, targetId)
            categoryDao.moveChildren(sourceId, targetId)
            categoryDao.deleteCategory(sourceId)
        }
    }

    private fun validateCategoryType(type: CategoryType) {
        require(type == CategoryType.INCOME || type == CategoryType.EXPENSE) {
            "Choose an income or expense category"
        }
    }

    private fun validateCategoryMetadata(name: String, colorHex: String) {
        require(name.isNotBlank()) { "Category name is required" }
        require(colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Choose a valid category color" }
    }

    private suspend fun validateCategoryName(type: CategoryType, name: String, excludeId: String) {
        require(categoryDao.countNameConflicts(type.name, name.trim(), excludeId) == 0L) {
            "A ${type.name.lowercase()} category with this name already exists"
        }
    }

    private suspend fun validateCategoryParent(parentId: String?, type: CategoryType, currentId: String? = null) {
        if (parentId == null) return
        require(type == CategoryType.INCOME || type == CategoryType.EXPENSE) {
            "Only income and expense categories can have subcategories"
        }
        require(parentId != currentId) { "A category cannot be its own parent" }
        val parent = categoryDao.getCategoryById(parentId)
            ?: throw IllegalArgumentException("Choose an existing parent category")
        require(parent.type == type.name && !parent.isArchived && parent.parentCategoryId == null) {
            "Choose an active top-level category of the same type"
        }
    }

    private fun requireEditableCategory(category: CategoryEntity) {
        require(category.type != CategoryType.SYSTEM.name && category.type != CategoryType.TRANSFER.name) {
            "System categories cannot be changed"
        }
        require(category.id != "cat_system" && category.id != "cat_transfer") {
            "System categories cannot be changed"
        }
    }

    fun getCategoryTransactionsFlow(categoryId: String): Flow<List<Transaction>> = transactionsFlow.map { transactions ->
        transactions.filter { it.categoryId == categoryId }
    }

    // Transactions
    val transactionsFlow: Flow<List<Transaction>> = combine(
        journalDao.getAllJournalEntriesFlow(),
        accountDao.getAllAccountsFlow(),
        categoryDao.getAllCategoriesFlow()
    ) { entries, accounts, categories ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val reversedEntryIds = entries.mapNotNull { it.reversesEntryId }.toSet()

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
                notes = entry.notes,
                reversesEntryId = entry.reversesEntryId,
                isReversed = entry.id in reversedEntryIds
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
        notes: String = "",
        reversesEntryId: String? = null
    ) {
        require(entry.isBalanced()) { "Journal entry is unbalanced and cannot be posted" }
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
            notes = notes,
            reversesEntryId = reversesEntryId
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

        journalDao.insertJournalEntryWithLines(entryEntity, lineEntities)
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
        database.withTransaction {
            validateFinancialAccount(accountId)
            validateCategory(categoryId, CategoryType.INCOME)
            val entry = AccountingEngine.createIncomeEntry(accountId, categoryId, amount, description, timestamp)
            postJournalEntry(entry, TransactionType.INCOME, amount, Money.ZERO, accountId, null, categoryId, counterparty, notes)
        }
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
        database.withTransaction {
            validateFinancialAccount(accountId)
            validateCategory(categoryId, CategoryType.EXPENSE)
            val entry = AccountingEngine.createExpenseEntry(accountId, categoryId, amount, description, timestamp)
            postJournalEntry(entry, TransactionType.EXPENSE, amount, Money.ZERO, accountId, null, categoryId, counterparty, notes)
        }
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
        database.withTransaction {
            validateFinancialAccount(sourceAccountId)
            validateFinancialAccount(destinationAccountId)
            if (fee.isPositive) validateCategory(feeCategoryId ?: "", CategoryType.EXPENSE)
            val entry = AccountingEngine.createTransferEntry(sourceAccountId, destinationAccountId, amount, fee, feeCategoryId, description, timestamp)
            postJournalEntry(entry, TransactionType.TRANSFER, amount, fee, sourceAccountId, destinationAccountId, feeCategoryId, "", notes)
        }
    }

    private suspend fun validateFinancialAccount(accountId: String) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Select a valid financial account")
        val type = AccountType.valueOf(account.accountType)
        require(type.isAsset && !type.isReceivable && !account.isArchived) {
            "Choose an active asset account for this transaction"
        }
    }

    private suspend fun validateCategory(categoryId: String, expected: CategoryType) {
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Select a valid ${expected.name.lowercase()} category")
        require(category.type == expected.name) { "Selected category must be ${expected.name.lowercase()}" }
        require(!category.isArchived) { "Restore this category before using it" }
    }

    suspend fun reverseTransaction(journalEntryId: String) {
        database.withTransaction { reverseTransactionInCurrentTransaction(journalEntryId) }
    }

    suspend fun correctTransaction(journalEntryId: String, correction: TransactionCorrection) {
        database.withTransaction {
            val originalEntity = journalDao.getJournalEntryById(journalEntryId)
                ?: throw IllegalArgumentException("Transaction no longer exists")
            val originalType = TransactionType.valueOf(originalEntity.transactionType)
            require(originalType in setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)) {
                "This linked financial record must be corrected from its own screen"
            }
            require(journalDao.getReversalForEntry(journalEntryId) == null) {
                "This transaction has already been corrected or voided"
            }
            require(correction.transactionType == originalType) { "A correction must keep the original transaction type" }
            require(correction.amount.isPositive) { "Enter an amount greater than zero" }
            require(!correction.fee.isNegative) { "Transfer fee cannot be negative" }

            validateCorrectionAccount(correction.accountId, originalEntity.primaryAccountId)
            when (originalType) {
                TransactionType.INCOME -> {
                    require(correction.destinationAccountId == null && correction.fee.isZero) {
                        "Income corrections cannot include a destination account or transfer fee"
                    }
                    validateCorrectionCategory(correction.categoryId, originalEntity.categoryId, CategoryType.INCOME)
                }
                TransactionType.EXPENSE -> {
                    require(correction.destinationAccountId == null && correction.fee.isZero) {
                        "Expense corrections cannot include a destination account or transfer fee"
                    }
                    validateCorrectionCategory(correction.categoryId, originalEntity.categoryId, CategoryType.EXPENSE)
                }
                TransactionType.TRANSFER -> {
                    val destinationId = correction.destinationAccountId
                        ?: throw IllegalArgumentException("Choose a destination account")
                    require(destinationId != correction.accountId) { "Choose a different destination account" }
                    validateCorrectionAccount(destinationId, originalEntity.destinationAccountId)
                    if (correction.fee.isPositive) {
                        validateCorrectionCategory(correction.categoryId, originalEntity.categoryId, CategoryType.EXPENSE)
                    } else if (correction.categoryId != null) {
                        validateCorrectionCategory(correction.categoryId, originalEntity.categoryId, CategoryType.EXPENSE)
                    }
                }
                else -> error("Unreachable transaction type")
            }

            reverseTransactionInCurrentTransaction(journalEntryId)
            val replacement = when (originalType) {
                TransactionType.INCOME -> AccountingEngine.createIncomeEntry(
                    correction.accountId,
                    correction.categoryId!!,
                    correction.amount,
                    correction.description,
                    correction.timestamp
                )
                TransactionType.EXPENSE -> AccountingEngine.createExpenseEntry(
                    correction.accountId,
                    correction.categoryId!!,
                    correction.amount,
                    correction.description,
                    correction.timestamp
                )
                TransactionType.TRANSFER -> AccountingEngine.createTransferEntry(
                    correction.accountId,
                    correction.destinationAccountId!!,
                    correction.amount,
                    correction.fee,
                    correction.categoryId,
                    correction.description,
                    correction.timestamp
                )
                else -> error("Unreachable transaction type")
            }
            postJournalEntry(
                entry = replacement,
                transactionType = originalType,
                amount = correction.amount,
                fee = correction.fee,
                primaryAccountId = correction.accountId,
                destinationAccountId = correction.destinationAccountId,
                categoryId = correction.categoryId,
                counterparty = correction.counterparty.trim(),
                notes = correction.notes.trim()
            )
        }
    }

    private suspend fun validateCorrectionAccount(accountId: String, originalAccountId: String?) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Select a valid financial account")
        val type = AccountType.valueOf(account.accountType)
        require(type.isAsset && !type.isReceivable) { "Choose an asset account for this transaction" }
        require(!account.isArchived || account.id == originalAccountId) {
            "Restore an archived account before moving this transaction to it"
        }
    }

    private suspend fun validateCorrectionCategory(categoryId: String?, originalCategoryId: String?, expected: CategoryType) {
        val id = categoryId ?: throw IllegalArgumentException("Choose a ${expected.name.lowercase()} category")
        val category = categoryDao.getCategoryById(id)
            ?: throw IllegalArgumentException("Select a valid ${expected.name.lowercase()} category")
        require(category.type == expected.name) { "Selected category must be ${expected.name.lowercase()}" }
        require(!category.isArchived || category.id == originalCategoryId) {
            "Restore an archived category before assigning it to this transaction"
        }
    }

    private suspend fun reverseTransactionInCurrentTransaction(journalEntryId: String) {
            val originalEntity = journalDao.getJournalEntryById(journalEntryId)
                ?: throw IllegalArgumentException("Transaction no longer exists")
            require(originalEntity.transactionType != TransactionType.REVERSAL.name) {
                "A reversal cannot itself be reversed"
            }
            require(journalDao.getReversalForEntry(journalEntryId) == null) {
                "This transaction has already been reversed"
            }

            val originalLines = journalDao.getLinesForJournalEntry(journalEntryId).map { it.toDomainModel() }
            val originalEntry = JournalEntry(
                id = originalEntity.id,
                timestamp = originalEntity.timestamp,
                description = originalEntity.description,
                lines = originalLines
            )
            val reversal = AccountingEngine.createReversalEntry(originalEntry, journalEntryId)
            val originalType = TransactionType.valueOf(originalEntity.transactionType)

            when (originalType) {
                TransactionType.RECEIVABLE_COLLECTION -> {
                    val payment = receivableDao.getPaymentByJournalEntryId(journalEntryId)
                        ?: throw IllegalStateException("Receivable payment history is missing")
                    require(payment.reversedAt == null) { "This payment has already been reversed" }
                    val receivable = receivableDao.getReceivableById(payment.receivableId)
                        ?: throw IllegalStateException("Linked receivable is missing")
                    val restoredOutstanding = Math.addExact(receivable.outstandingAmountCentavos, payment.amountCentavos)
                    require(restoredOutstanding <= receivable.originalAmountCentavos) {
                        "Reversing this payment would exceed the original receivable balance"
                    }
                    val restoredStatus = when {
                        receivable.dueDate < System.currentTimeMillis() -> ReceivableStatus.OVERDUE.name
                        restoredOutstanding == receivable.originalAmountCentavos -> ReceivableStatus.CONFIRMED.name
                        else -> ReceivableStatus.PARTIALLY_PAID.name
                    }
                    receivableDao.updatePayment(payment.copy(reversedAt = System.currentTimeMillis()))
                    receivableDao.updateReceivable(
                        receivable.copy(
                            outstandingAmountCentavos = restoredOutstanding,
                            status = restoredStatus
                        )
                    )
                }
                TransactionType.RECEIVABLE_RECOGNITION -> {
                    val receivable = receivableDao.getReceivableById(originalEntity.primaryAccountId)
                        ?: throw IllegalStateException("Linked receivable is missing")
                    val activePayments = receivableDao.getPaymentsForReceivable(receivable.id)
                        .any { it.reversedAt == null }
                    require(!activePayments) { "Reverse active collections before cancelling this receivable" }
                    receivableDao.updateReceivable(
                        receivable.copy(
                            outstandingAmountCentavos = 0L,
                            status = ReceivableStatus.CANCELLED.name
                        )
                    )
                }
                else -> Unit
            }

            postJournalEntry(
                entry = reversal,
                transactionType = TransactionType.REVERSAL,
                amount = Money(originalEntity.amountCentavos),
                fee = Money(originalEntity.feeCentavos),
                primaryAccountId = originalEntity.primaryAccountId,
                destinationAccountId = originalEntity.destinationAccountId,
                categoryId = originalEntity.categoryId,
                counterparty = originalEntity.counterparty,
                notes = "Reversal of ${originalEntity.id}. ${originalEntity.notes}".trim(),
                reversesEntryId = originalEntity.id
            )
    }

    // Receivables
    val receivablesFlow: Flow<List<Receivable>> = combine(
        receivableDao.getAllReceivablesFlow(),
        receivableDao.getAllPaymentsFlow()
    ) { receivables, allPayments ->
        val paymentGroup = allPayments.groupBy { it.receivableId }
        val now = System.currentTimeMillis()

        receivables.map { recEntity ->
            val payments = paymentGroup[recEntity.id]?.map { it.toDomainModel() } ?: emptyList()
            val receivable = recEntity.toDomainModel(payments)
            if (recEntity.dueDate < now &&
                receivable.status in setOf(ReceivableStatus.CONFIRMED, ReceivableStatus.PARTIALLY_PAID)
            ) {
                receivable.copy(status = ReceivableStatus.OVERDUE)
            } else {
                receivable
            }
        }
    }

    suspend fun createReceivable(
        clientName: String,
        amount: Money,
        dueDate: Long,
        incomeCategoryId: String?,
        description: String
    ): Receivable {
        require(clientName.isNotBlank()) { "Client name is required" }
        require(amount.isPositive) { "Receivable amount must be positive" }
        val categoryId = incomeCategoryId ?: "cat_other_income"
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Select a valid income category")
        require(category.type == com.ralledger.domain.model.CategoryType.INCOME.name) {
            "Receivable recognition requires an income category"
        }
        val id = UUID.randomUUID().toString()
        val recEntity = ReceivableEntity(
            id = id,
            clientName = clientName,
            originalAmountCentavos = amount.centavos,
            outstandingAmountCentavos = amount.centavos,
            recognitionDate = System.currentTimeMillis(),
            dueDate = dueDate,
            linkedCategoryId = categoryId,
            description = description,
            status = ReceivableStatus.CONFIRMED.name
        )
        val entry = AccountingEngine.createReceivableRecognitionEntry(
            receivableAccountId = id,
            incomeCategoryId = categoryId,
            amount = amount,
            description = description.ifBlank { "Receivable recognized: $clientName" }
        )
        database.withTransaction {
            receivableDao.insertReceivable(recEntity)
            postJournalEntry(
                entry = entry,
                transactionType = TransactionType.RECEIVABLE_RECOGNITION,
                amount = amount,
                primaryAccountId = id,
                categoryId = categoryId,
                counterparty = clientName
            )
        }
        return recEntity.toDomainModel(emptyList())
    }

    suspend fun collectReceivable(
        receivableId: String,
        destinationAccountId: String,
        paymentAmount: Money,
        notes: String
    ) {
        require(paymentAmount.isPositive) { "Collection amount must be positive" }
        database.withTransaction {
            val receivable = receivableDao.getReceivableById(receivableId)
                ?: throw IllegalArgumentException("Receivable no longer exists")
            val outstanding = Money(receivable.outstandingAmountCentavos)
            require(outstanding.isPositive) { "This receivable is already settled" }
            require(paymentAmount <= outstanding) { "Collection cannot exceed the outstanding balance" }
            require(
                receivable.status == ReceivableStatus.CONFIRMED.name ||
                    receivable.status == ReceivableStatus.PARTIALLY_PAID.name ||
                    receivable.status == ReceivableStatus.OVERDUE.name
            ) { "This receivable is not collectible" }

            val destination = accountDao.getAccountById(destinationAccountId)
                ?: throw IllegalArgumentException("Select a valid destination account")
            val destinationType = AccountType.valueOf(destination.accountType)
            require(destinationType.isLiquid && !destination.isArchived) {
                "Collections must be deposited into an active cash, bank, or e-wallet account"
            }

            val newOutstanding = outstanding - paymentAmount
            val newStatus = when {
                newOutstanding.isZero -> ReceivableStatus.PAID.name
                receivable.dueDate < System.currentTimeMillis() -> ReceivableStatus.OVERDUE.name
                else -> ReceivableStatus.PARTIALLY_PAID.name
            }

            val entry = AccountingEngine.createReceivableCollectionEntry(
                destinationAccountId = destinationAccountId,
                receivableAccountId = receivable.id,
                amount = paymentAmount,
                description = "Receivable payment from ${receivable.clientName}"
            )
            postJournalEntry(
                entry, TransactionType.RECEIVABLE_COLLECTION, paymentAmount, Money.ZERO,
                destinationAccountId, receivable.id, receivable.linkedCategoryId,
                receivable.clientName, notes
            )

            receivableDao.insertPayment(
                ReceivablePaymentEntity(
                    id = UUID.randomUUID().toString(),
                    receivableId = receivableId,
                    journalEntryId = entry.id,
                    amountCentavos = paymentAmount.centavos,
                    destinationAccountId = destinationAccountId,
                    paymentDate = System.currentTimeMillis(),
                    notes = notes
                )
            )
            receivableDao.updateReceivable(
                receivable.copy(
                    outstandingAmountCentavos = newOutstanding.centavos,
                    status = newStatus
                )
            )
        }
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
        require(subscription.name.isNotBlank()) { "Subscription name is required" }
        require(subscription.billingAmount.isPositive) { "Subscription billing amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(subscription.assignedAccountId)
                ?: throw IllegalArgumentException("Select a valid payment account")
            require(AccountType.valueOf(account.accountType).isLiquid && !account.isArchived) {
                "Choose an active cash, bank, or e-wallet account for this subscription"
            }
            subscriptionDao.insertSubscription(SubscriptionEntity.fromDomainModel(subscription))
        }
    }

    suspend fun updateSubscription(subscription: Subscription) {
        require(subscription.name.isNotBlank()) { "Subscription name is required" }
        require(subscription.billingAmount.isPositive) { "Subscription billing amount must be positive" }
        database.withTransaction {
            val account = accountDao.getAccountById(subscription.assignedAccountId)
                ?: throw IllegalArgumentException("Select a valid payment account")
            require(AccountType.valueOf(account.accountType).isLiquid && !account.isArchived) {
                "Choose an active cash, bank, or e-wallet account for this subscription"
            }
            subscriptionDao.updateSubscription(SubscriptionEntity.fromDomainModel(subscription))
        }
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        database.withTransaction {
            subscriptionDao.deleteSubscription(SubscriptionEntity.fromDomainModel(subscription))
        }
    }

    // Reconciliation
    suspend fun reconcileAccount(
        accountId: String,
        actualBalance: Money,
        reason: String
    ) {
        database.withTransaction {
            val currentAccount = getAccountById(accountId)
                ?: throw IllegalArgumentException("Select a valid account to reconcile")
            require(currentAccount.accountType.isAsset || currentAccount.accountType.isLiability) {
                "Only asset and liability accounts can be reconciled"
            }
            val recordedBalance = currentAccount.currentLedgerBalance
            val difference = actualBalance - recordedBalance
            val journalId = if (difference.isZero) {
                null
            } else {
                val entry = AccountingEngine.createReconciliationAdjustmentEntry(
                    accountId = accountId,
                    accountType = currentAccount.accountType,
                    difference = difference,
                    description = "Reconciliation adjustment: $reason"
                )
                postJournalEntry(
                    entry, TransactionType.BALANCE_ADJUSTMENT, difference, Money.ZERO,
                    accountId, null, null, "Reconciliation", reason
                )
                entry.id
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
        DashboardSummaryCalculator.calculate(
            accounts = accounts,
            receivables = receivables,
            subscriptions = subscriptions,
            transactions = transactions,
            startOfMonthTimestamp = getStartOfMonthTimestamp()
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
