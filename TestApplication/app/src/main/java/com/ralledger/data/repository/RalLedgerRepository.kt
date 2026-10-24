package com.ralledger.data.repository

import androidx.room.withTransaction
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.BudgetEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.local.entity.CounterpartyEntity
import com.ralledger.data.local.entity.ExpectedIncomeEntity
import com.ralledger.data.local.entity.DebtEntity
import com.ralledger.data.local.entity.DebtPaymentEntity
import com.ralledger.data.local.entity.toDomainModel
import com.ralledger.data.local.entity.JournalEntryEntity
import com.ralledger.data.local.entity.JournalLineEntity
import com.ralledger.data.local.entity.ReceivableEntity
import com.ralledger.data.local.entity.ReceivablePaymentEntity
import com.ralledger.data.local.entity.ReceivableWriteOffEntity
import com.ralledger.data.local.entity.ReconciliationEntity
import com.ralledger.data.local.entity.SavingsGoalEntity
import com.ralledger.data.local.entity.SavingsGoalEventEntity
import com.ralledger.data.local.entity.InterestProductEntity
import com.ralledger.data.local.entity.InterestCreditEntity
import com.ralledger.data.local.entity.SubscriptionPaymentEntity
import com.ralledger.data.local.entity.JournalEntryTagEntity
import com.ralledger.data.local.entity.SavedTransactionFilterEntity
import com.ralledger.data.local.entity.TransactionTagEntity
import com.ralledger.data.local.entity.TransactionTemplateEntity
import com.ralledger.data.local.entity.PlannedPaymentEntity
import com.ralledger.data.local.entity.PlannedPaymentRecordEntity
import com.ralledger.data.importing.CsvImportExecutionResult
import com.ralledger.data.importing.CsvImportedTransaction
import com.ralledger.data.local.entity.toDomainModel as toSavingsGoalDomainModel
import com.ralledger.data.local.entity.toDomainModel as toSavingsGoalEventDomainModel
import com.ralledger.data.local.entity.toDomainModel as toInterestProductDomainModel
import com.ralledger.data.local.entity.toDomainModel as toInterestCreditDomainModel
import com.ralledger.data.local.entity.toDomainModel as toSubscriptionPaymentDomainModel
import com.ralledger.data.local.entity.SubscriptionEntity
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountCategory
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Budget
import com.ralledger.domain.model.Category
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Counterparty
import com.ralledger.domain.model.CounterpartyKind
import com.ralledger.domain.model.normalizeCounterpartyName
import com.ralledger.domain.model.BillingFrequency
import com.ralledger.domain.model.DashboardSummary
import com.ralledger.domain.model.Debt
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.ExpectedIncome
import com.ralledger.domain.model.ExpectedIncomeStatus
import com.ralledger.domain.model.PlannedPayment
import com.ralledger.domain.model.PlannedPaymentRecord
import com.ralledger.domain.model.PlannedPaymentStatus
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Receivable
import com.ralledger.domain.model.ReceivableStatus
import com.ralledger.domain.model.Reconciliation
import com.ralledger.domain.model.SavingsGoal
import com.ralledger.domain.model.SavingsGoalEventType
import com.ralledger.domain.model.InterestProduct
import com.ralledger.domain.model.InterestCredit
import com.ralledger.domain.model.InterestProductType
import com.ralledger.domain.model.InterestCompounding
import com.ralledger.domain.model.Subscription
import com.ralledger.domain.model.SubscriptionPayment
import com.ralledger.domain.model.SubscriptionStatus
import com.ralledger.domain.model.Transaction
import com.ralledger.domain.model.TransactionCorrection
import com.ralledger.domain.model.TransactionType
import com.ralledger.domain.model.TransactionTag
import com.ralledger.domain.model.SavedTransactionFilter
import com.ralledger.domain.model.TransactionFilter
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.normalizeTransactionTemplateName
import com.ralledger.domain.usecase.AccountingEngine
import com.ralledger.domain.usecase.BudgetSpendingCalculator
import com.ralledger.domain.usecase.BudgetSpendingKey
import com.ralledger.domain.usecase.DashboardSummaryCalculator
import com.ralledger.domain.usecase.SubscriptionSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class RalLedgerRepository(
    private val database: RalLedgerDatabase
) {
    private val accountDao = database.accountDao()
    private val budgetDao = database.budgetDao()
    private val journalDao = database.journalDao()
    private val categoryDao = database.categoryDao()
    private val expectedIncomeDao = database.expectedIncomeDao()
    private val debtDao = database.debtDao()
    private val receivableDao = database.receivableDao()
    private val subscriptionDao = database.subscriptionDao()
    private val reconciliationDao = database.reconciliationDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val interestDao = database.interestDao()
    private val subscriptionPaymentDao = database.subscriptionPaymentDao()
    private val transactionTagDao = database.transactionTagDao()
    private val transactionTemplateDao = database.transactionTemplateDao()
    private val plannedPaymentDao = database.plannedPaymentDao()
    private val savedTransactionFilterDao = database.savedTransactionFilterDao()
    private val counterpartyDao = database.counterpartyDao()

    val counterpartiesFlow: Flow<List<Counterparty>> = counterpartyDao.getAllFlow()
        .map { items -> items.map(CounterpartyEntity::toDomainModel) }

    suspend fun createCounterparty(name: String, kind: CounterpartyKind, notes: String = ""): String {
        val normalizedName = normalizeCounterpartyName(name)
        require(normalizedName.isNotBlank()) { "Enter a counterparty name" }
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            require(counterpartyDao.getByNormalizedName(normalizedName) == null) {
                "A counterparty with this name already exists"
            }
            counterpartyDao.insert(
                CounterpartyEntity(
                    id = id,
                    name = name.trim().replace(Regex("\\s+"), " "),
                    normalizedName = normalizedName,
                    kind = kind.name,
                    notes = notes.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    suspend fun updateCounterparty(id: String, name: String, kind: CounterpartyKind, notes: String) {
        val normalizedName = normalizeCounterpartyName(name)
        require(normalizedName.isNotBlank()) { "Enter a counterparty name" }
        database.withTransaction {
            val current = counterpartyDao.getById(id)
                ?: throw IllegalArgumentException("This counterparty no longer exists")
            val duplicate = counterpartyDao.getByNormalizedName(normalizedName)
            require(duplicate == null || duplicate.id == id) { "A counterparty with this name already exists" }
            counterpartyDao.update(
                current.copy(
                    name = name.trim().replace(Regex("\\s+"), " "),
                    normalizedName = normalizedName,
                    kind = kind.name,
                    notes = notes.trim()
                )
            )
        }
    }

    suspend fun setCounterpartyArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val current = counterpartyDao.getById(id)
                ?: throw IllegalArgumentException("This counterparty no longer exists")
            counterpartyDao.update(current.copy(isArchived = isArchived))
        }
    }

    suspend fun deleteCounterparty(id: String) {
        database.withTransaction {
            val current = counterpartyDao.getById(id)
                ?: throw IllegalArgumentException("This counterparty no longer exists")
            val receivableReferences = counterpartyDao.countReceivableReferences(id)
            val expectedIncomeReferences = counterpartyDao.countExpectedIncomeReferences(id)
            val debtReferences = counterpartyDao.countDebtReferences(id)
            val subscriptionReferences = counterpartyDao.countSubscriptionReferences(id)
            val plannedPaymentReferences = plannedPaymentDao.countCounterpartyReferences(id)
            require(receivableReferences == 0L && expectedIncomeReferences == 0L && debtReferences == 0L && subscriptionReferences == 0L && plannedPaymentReferences == 0L) {
                "${current.name} is linked to financial records. Merge it or archive it to preserve history."
            }
            counterpartyDao.delete(id)
        }
    }

    suspend fun mergeCounterparties(sourceId: String, targetId: String) {
        require(sourceId != targetId) { "Choose a different counterparty to merge into" }
        database.withTransaction {
            val source = counterpartyDao.getById(sourceId)
                ?: throw IllegalArgumentException("The counterparty to merge no longer exists")
            val target = counterpartyDao.getById(targetId)
                ?: throw IllegalArgumentException("Choose an existing counterparty")
            require(!target.isArchived) { "Restore the destination counterparty before merging" }
            receivableDao.reassignCounterparty(source.id, target.id)
            expectedIncomeDao.reassignCounterparty(source.id, target.id)
            debtDao.reassignCounterparty(source.id, target.id, target.name)
            subscriptionDao.reassignCounterparty(source.id, target.id, target.name)
            plannedPaymentDao.reassignCounterparty(source.id, target.id)
            counterpartyDao.delete(source.id)
        }
    }

    suspend fun getCounterpartyReferenceCount(id: String): Long = database.withTransaction {
        counterpartyDao.countReceivableReferences(id) + counterpartyDao.countExpectedIncomeReferences(id) +
            counterpartyDao.countDebtReferences(id) + counterpartyDao.countSubscriptionReferences(id) +
            plannedPaymentDao.countCounterpartyReferences(id)
    }

    private suspend fun validateCounterparty(
        id: String?,
        allowArchivedId: String? = null
    ): CounterpartyEntity? {
        if (id == null) return null
        val item = counterpartyDao.getById(id)
            ?: throw IllegalArgumentException("Choose a saved counterparty that still exists")
        require(!item.isArchived || item.id == allowArchivedId) {
            "Restore this counterparty before assigning it to a new record"
        }
        return item
    }

    val journalHistoryFlow: Flow<List<JournalEntry>> = combine(
        journalDao.getAllJournalEntriesFlow(),
        journalDao.getAllJournalLinesFlow()
    ) { entries, lines ->
        val linesByEntryId = lines.groupBy { it.journalEntryId }
        entries.map { entry ->
            JournalEntry(
                id = entry.id,
                timestamp = entry.timestamp,
                description = entry.description,
                lines = linesByEntryId[entry.id].orEmpty().map { it.toDomainModel() }
            )
        }
    }

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

    suspend fun setAccountArchived(id: String, isArchived: Boolean): Boolean {
        return database.withTransaction {
            require(!isSystemAccount(id)) { "System accounts cannot be changed" }
            require(accountDao.getAccountById(id) != null) { "This account no longer exists" }
            val interestProduct = interestDao.getProductForAccount(id)
            accountDao.setArchived(id, isArchived)
            if (isArchived && interestProduct != null && !interestProduct.isArchived) {
                interestDao.updateProduct(interestProduct.copy(isArchived = true))
            }
            interestProduct != null
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
                debtDao.countPaymentsForAccount(id) +
                debtDao.countDebtsForFundingAccount(id) +
                subscriptionDao.countAccountReferences(id) +
                interestDao.countProductsForAccount(id) +
                transactionTemplateDao.countAccountReferences(id)
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
            require(journalDao.countCategoryReferences(id) == 0L &&
                budgetDao.countCategoryReferences(id) == 0L && expectedIncomeDao.countCategoryReferences(id) == 0L &&
                receivableDao.countReceivablesForIncomeCategory(id) == 0L &&
                debtDao.countDebtsForCategory(id) == 0L && debtDao.countPaymentsForCategory(id) == 0L &&
                interestDao.countCreditsForTaxCategory(id) == 0L &&
                subscriptionPaymentDao.countPaymentsForCategory(id) == 0L &&
                transactionTemplateDao.countCategoryReferences(id) == 0L &&
                plannedPaymentDao.countCategoryReferences(id) == 0L &&
                categoryDao.countChildren(id) == 0L
            ) {
                "This category is used by financial history, budgets, receivables, or other records. Archive it to preserve its history."
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
            val sourceBudgets = budgetDao.getBudgetsForCategory(sourceId)
            require(sourceBudgets.none { budget ->
                budgetDao.getBudgetForCategoryPeriod(targetId, budget.year, budget.month) != null
            }) {
                "This category and the destination already have budgets for the same month. Delete one before merging."
            }
            journalDao.reassignEntryCategory(sourceId, targetId)
            journalDao.reassignCategoryLedgerLines(sourceId, targetId)
            budgetDao.reassignCategory(sourceId, targetId)
            expectedIncomeDao.reassignCategory(sourceId, targetId)
            debtDao.reassignDebtCategory(sourceId, targetId)
            debtDao.reassignPaymentCategory(sourceId, targetId)
            interestDao.reassignTaxCategory(sourceId, targetId)
            subscriptionPaymentDao.reassignCategory(sourceId, targetId)
            receivableDao.reassignIncomeCategory(sourceId, targetId)
            receivableDao.reassignWriteOffCategory(sourceId, targetId)
            val updatedAt = System.currentTimeMillis()
            transactionTemplateDao.reassignPrimaryCategory(sourceId, targetId, updatedAt)
            transactionTemplateDao.reassignFeeCategory(sourceId, targetId, updatedAt)
            plannedPaymentDao.reassignCategory(sourceId, targetId)
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
        transactions.filter { it.categoryId == categoryId || it.secondaryCategoryId == categoryId }
    }

    val transactionTagsFlow: Flow<List<TransactionTag>> = transactionTagDao.getAllTagsFlow().map { tags ->
        tags.map(TransactionTagEntity::toDomainModel)
    }

    val savedTransactionFiltersFlow: Flow<List<SavedTransactionFilter>> =
        savedTransactionFilterDao.getAllFlow().map { rows -> rows.map(SavedTransactionFilterEntity::toDomainModel) }

    val transactionTemplatesFlow: Flow<List<TransactionTemplate>> = transactionTemplateDao.getAllFlow()
        .map { rows -> rows.map(TransactionTemplateEntity::toDomainModel) }

    suspend fun createTransactionTemplate(template: TransactionTemplate): String {
        val now = System.currentTimeMillis()
        val newTemplate = template.copy(
            id = UUID.randomUUID().toString(),
            name = template.name.trim(),
            counterparty = template.counterparty.trim(),
            description = template.description.trim(),
            notes = template.notes.trim(),
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        database.withTransaction {
            validateTransactionTemplate(newTemplate)
            transactionTemplateDao.insert(TransactionTemplateEntity.fromDomainModel(newTemplate))
        }
        return newTemplate.id
    }

    suspend fun updateTransactionTemplate(template: TransactionTemplate) {
        require(template.id.isNotBlank()) { "This transaction template no longer exists" }
        database.withTransaction {
            val current = transactionTemplateDao.getById(template.id)
                ?: throw IllegalArgumentException("This transaction template no longer exists")
            val updated = template.copy(
                name = template.name.trim(),
                counterparty = template.counterparty.trim(),
                description = template.description.trim(),
                notes = template.notes.trim(),
                isArchived = current.isArchived,
                createdAt = current.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            validateTransactionTemplate(
                updated,
                current = current.toDomainModel()
            )
            transactionTemplateDao.update(TransactionTemplateEntity.fromDomainModel(updated))
        }
    }

    suspend fun setTransactionTemplateArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            require(transactionTemplateDao.getById(id) != null) { "This transaction template no longer exists" }
            transactionTemplateDao.setArchived(id, isArchived, System.currentTimeMillis())
        }
    }

    suspend fun deleteTransactionTemplate(id: String) {
        database.withTransaction {
            require(transactionTemplateDao.getById(id) != null) { "This transaction template no longer exists" }
            transactionTemplateDao.delete(id)
        }
    }

    private suspend fun validateTransactionTemplate(
        template: TransactionTemplate,
        current: TransactionTemplate? = null
    ) {
        require(template.name.isNotBlank()) { "Enter a template name" }
        require(template.name.trim().length <= 60) { "Use 60 characters or fewer for a template name" }
        require(template.amount.isPositive) { "Template amount must be greater than zero" }
        require(!template.fee.isNegative) { "Transfer fee cannot be negative" }
        require(template.transactionType in setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)) {
            "Choose income, expense, or transfer for this template"
        }
        require(template.description.length <= 200) { "Keep the description to 200 characters or fewer" }
        require(template.counterparty.length <= 120) { "Keep the source or counterparty to 120 characters or fewer" }
        require(template.notes.length <= 500) { "Keep notes to 500 characters or fewer" }

        val normalizedName = normalizeTransactionTemplateName(template.name)
        require(transactionTemplateDao.getByNormalizedName(normalizedName)?.id in setOf(null, template.id)) {
            "A transaction template with this name already exists"
        }

        validateTemplateAccount(template.accountId, current?.accountId)
        when (template.transactionType) {
            TransactionType.INCOME, TransactionType.EXPENSE -> {
                require(template.destinationAccountId == null && template.fee.isZero && template.feeCategoryId == null) {
                    "Income and expense templates cannot include transfer details"
                }
                validateTemplateCategory(
                    requireNotNull(template.categoryId) { "Choose a category for this template" },
                    if (template.transactionType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE,
                    current?.categoryId
                )
            }
            TransactionType.TRANSFER -> {
                require(template.categoryId == null) { "Transfers use a fee category only" }
                val destinationId = requireNotNull(template.destinationAccountId) { "Choose a destination account" }
                require(destinationId != template.accountId) { "Choose different source and destination accounts" }
                validateTemplateAccount(destinationId, current?.destinationAccountId)
                template.feeCategoryId?.let {
                    validateTemplateCategory(it, CategoryType.EXPENSE, current?.feeCategoryId)
                }
                require(template.fee.isZero || template.feeCategoryId != null) {
                    "Choose an expense category when the transfer fee is greater than zero"
                }
            }
            else -> error("Unsupported transaction template type")
        }
    }

    private suspend fun validateTemplateAccount(id: String, allowArchivedId: String?) {
        val account = accountDao.getAccountById(id)
            ?: throw IllegalArgumentException("Choose an existing account")
        val type = AccountType.valueOf(account.accountType)
        require(type.isAsset && !type.isReceivable) { "Choose an asset account for this template" }
        require(!account.isArchived || account.id == allowArchivedId) { "Restore this account before assigning it to a template" }
    }

    private suspend fun validateTemplateCategory(id: String, expected: CategoryType, allowArchivedId: String?) {
        val category = categoryDao.getCategoryById(id)
            ?: throw IllegalArgumentException("Choose an existing ${expected.name.lowercase()} category")
        require(category.type == expected.name) { "Choose a ${expected.name.lowercase()} category" }
        require(!category.isArchived || category.id == allowArchivedId) {
            "Restore this category before assigning it to a template"
        }
    }

    suspend fun createSavedTransactionFilter(name: String, filter: TransactionFilter): String {
        validateSavedTransactionFilter(name, filter)
        val normalizedName = normalizeSavedFilterName(name)
        val now = System.currentTimeMillis()
        val saved = SavedTransactionFilter(UUID.randomUUID().toString(), name.trim(), filter, now, now)
        database.withTransaction {
            require(savedTransactionFilterDao.getByNormalizedName(normalizedName) == null) {
                "A saved filter with this name already exists"
            }
            savedTransactionFilterDao.insert(SavedTransactionFilterEntity.fromDomainModel(saved))
        }
        return saved.id
    }

    suspend fun updateSavedTransactionFilter(id: String, name: String, filter: TransactionFilter) {
        validateSavedTransactionFilter(name, filter)
        database.withTransaction { updateSavedTransactionFilterInTransaction(id, name, filter) }
    }

    suspend fun renameSavedTransactionFilter(id: String, name: String) {
        require(name.isNotBlank()) { "Enter a saved filter name" }
        require(name.trim().length <= 40) { "Use 40 characters or fewer for a saved filter name" }
        database.withTransaction {
            val current = savedTransactionFilterDao.getById(id)
                ?: throw IllegalArgumentException("This saved filter no longer exists")
            updateSavedTransactionFilterInTransaction(id, name, current.toDomainModel().filter)
        }
    }

    suspend fun updateSavedTransactionFilterCriteria(id: String, filter: TransactionFilter) {
        validateSavedTransactionFilter("Saved filter", filter)
        database.withTransaction {
            val current = savedTransactionFilterDao.getById(id)
                ?: throw IllegalArgumentException("This saved filter no longer exists")
            updateSavedTransactionFilterInTransaction(id, current.name, filter)
        }
    }

    suspend fun deleteSavedTransactionFilter(id: String) {
        database.withTransaction {
            require(savedTransactionFilterDao.getById(id) != null) { "This saved filter no longer exists" }
            savedTransactionFilterDao.delete(id)
        }
    }

    private fun validateSavedTransactionFilter(name: String, filter: TransactionFilter) {
        require(name.isNotBlank()) { "Enter a saved filter name" }
        require(name.trim().length <= 40) { "Use 40 characters or fewer for a saved filter name" }
        require(filter.query.length <= 200) { "Search text must be 200 characters or fewer to save this filter" }
        require(filter.startInclusive == null || filter.endExclusive == null || filter.startInclusive < filter.endExclusive) {
            "The saved filter start date must be before its end date"
        }
    }

    private fun normalizeSavedFilterName(name: String) = name.trim().lowercase(java.util.Locale.ROOT)

    private suspend fun updateSavedTransactionFilterInTransaction(id: String, name: String, filter: TransactionFilter) {
        val current = savedTransactionFilterDao.getById(id)
            ?: throw IllegalArgumentException("This saved filter no longer exists")
        val normalizedName = normalizeSavedFilterName(name)
        require(savedTransactionFilterDao.getByNormalizedName(normalizedName)?.id in setOf(null, id)) {
            "A saved filter with this name already exists"
        }
        savedTransactionFilterDao.update(
            SavedTransactionFilterEntity.fromDomainModel(
                SavedTransactionFilter(id, name.trim(), filter, current.createdAt, System.currentTimeMillis())
            )
        )
    }

    suspend fun createTransactionTag(name: String, colorHex: String): String {
        validateTransactionTag(name, colorHex)
        val normalizedName = normalizeTagName(name)
        val tag = TransactionTagEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            normalizedName = normalizedName,
            colorHex = colorHex.uppercase(),
            createdAt = System.currentTimeMillis(),
            isArchived = false
        )
        database.withTransaction {
            require(transactionTagDao.getTagByNormalizedName(normalizedName) == null) {
                "A transaction label with this name already exists"
            }
            transactionTagDao.insertTag(tag)
        }
        return tag.id
    }

    suspend fun updateTransactionTag(id: String, name: String, colorHex: String) {
        validateTransactionTag(name, colorHex)
        database.withTransaction {
            val current = transactionTagDao.getTagById(id)
                ?: throw IllegalArgumentException("This transaction label no longer exists")
            val normalizedName = normalizeTagName(name)
            require(transactionTagDao.getTagByNormalizedName(normalizedName)?.id in setOf(null, id)) {
                "A transaction label with this name already exists"
            }
            transactionTagDao.updateTag(
                current.copy(name = name.trim(), normalizedName = normalizedName, colorHex = colorHex.uppercase())
            )
        }
    }

    suspend fun setTransactionTagArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val tag = transactionTagDao.getTagById(id)
                ?: throw IllegalArgumentException("This transaction label no longer exists")
            transactionTagDao.updateTag(tag.copy(isArchived = isArchived))
        }
    }

    suspend fun deleteUnusedTransactionTag(id: String) {
        database.withTransaction {
            val tag = transactionTagDao.getTagById(id)
                ?: throw IllegalArgumentException("This transaction label no longer exists")
            require(transactionTagDao.countAssignments(id) == 0L) {
                "This label is attached to transaction history. Remove it from those transactions or archive it to preserve the label."
            }
            transactionTagDao.deleteTag(tag.id)
        }
    }

    suspend fun mergeTransactionTag(sourceId: String, targetId: String) {
        database.withTransaction {
            require(sourceId != targetId) { "Choose a different label to merge into" }
            val source = transactionTagDao.getTagById(sourceId)
                ?: throw IllegalArgumentException("The label to merge no longer exists")
            val target = transactionTagDao.getTagById(targetId)
                ?: throw IllegalArgumentException("Choose an existing destination label")
            require(!target.isArchived) { "Restore the destination label before merging" }
            transactionTagDao.deleteDuplicateAssignmentsForMerge(source.id, target.id)
            transactionTagDao.reassignTag(source.id, target.id)
            transactionTagDao.deleteTag(source.id)
        }
    }

    suspend fun setJournalEntryTags(journalEntryId: String, tagIds: Set<String>) {
        database.withTransaction {
            require(journalDao.getJournalEntryById(journalEntryId) != null) {
                "This transaction no longer exists"
            }
            val currentIds = transactionTagDao.getTagIdsForEntry(journalEntryId).toSet()
            val availableTags = tagIds.map { id ->
                transactionTagDao.getTagById(id)
                    ?: throw IllegalArgumentException("A selected label no longer exists")
            }
            require(availableTags.all { !it.isArchived || it.id in currentIds }) {
                "Restore an archived label before adding it to a transaction"
            }
            transactionTagDao.clearAssignmentsForEntry(journalEntryId)
            if (tagIds.isNotEmpty()) {
                transactionTagDao.insertAssignments(tagIds.map { JournalEntryTagEntity(journalEntryId, it) })
            }
        }
    }

    private fun validateTransactionTag(name: String, colorHex: String) {
        require(name.isNotBlank()) { "Enter a transaction label name" }
        require(name.trim().length <= 40) { "Keep transaction labels to 40 characters or fewer" }
        require(colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Choose a valid label color" }
    }

    private fun normalizeTagName(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)

    suspend fun createBill(
        title: String,
        counterparty: String,
        amount: Money,
        dueDate: Long,
        expenseCategoryId: String,
        counterpartyId: String? = null
    ): String {
        validateDebtFields(title, amount, dueDate)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            val savedCounterparty = validateCounterparty(counterpartyId)
            val counterpartyName = savedCounterparty?.name ?: counterparty.trim()
            validateCategory(expenseCategoryId, CategoryType.EXPENSE)
            val recordedAt = System.currentTimeMillis()
            val entry = AccountingEngine.createPayableRecognitionEntry(id, expenseCategoryId, amount, title.trim(), recordedAt)
            postJournalEntry(
                entry, TransactionType.PAYABLE_RECOGNITION, amount, primaryAccountId = id,
                categoryId = expenseCategoryId, counterparty = counterpartyName, notes = "Bill recorded"
            )
            debtDao.insertDebt(DebtEntity(
                id = id, title = title.trim(), counterparty = counterpartyName, kind = DebtKind.BILL.name,
                originalAmountCentavos = amount.centavos, outstandingAmountCentavos = amount.centavos,
                dueDate = dueDate, categoryId = expenseCategoryId, fundingAccountId = null,
                recognitionJournalEntryId = entry.id, status = DebtStatus.OPEN.name,
                createdAt = recordedAt, counterpartyId = savedCounterparty?.id
            ))
        }
        return id
    }

    suspend fun createLoan(
        title: String,
        lender: String,
        amount: Money,
        dueDate: Long,
        fundingAccountId: String,
        counterpartyId: String? = null
    ): String {
        validateDebtFields(title, amount, dueDate)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            val savedCounterparty = validateCounterparty(counterpartyId)
            val lenderName = savedCounterparty?.name ?: lender.trim()
            validateFinancialAccount(fundingAccountId)
            val receivedAt = System.currentTimeMillis()
            val entry = AccountingEngine.createLoanDisbursementEntry(fundingAccountId, id, amount, title.trim(), receivedAt)
            postJournalEntry(
                entry, TransactionType.LOAN_DISBURSEMENT, amount,
                primaryAccountId = fundingAccountId, destinationAccountId = id,
                counterparty = lenderName, notes = "Loan funds received"
            )
            debtDao.insertDebt(DebtEntity(
                id = id, title = title.trim(), counterparty = lenderName, kind = DebtKind.LOAN.name,
                originalAmountCentavos = amount.centavos, outstandingAmountCentavos = amount.centavos,
                dueDate = dueDate, categoryId = null, fundingAccountId = fundingAccountId,
                recognitionJournalEntryId = entry.id, status = DebtStatus.OPEN.name,
                createdAt = receivedAt, counterpartyId = savedCounterparty?.id
            ))
        }
        return id
    }

    suspend fun updateDebtMetadata(id: String, title: String, counterparty: String, dueDate: Long, counterpartyId: String? = null) {
        require(title.isNotBlank()) { "Enter a name or description" }
        require(dueDate > 0L) { "Choose a due date" }
        database.withTransaction {
            val debt = debtDao.getDebtById(id) ?: throw IllegalArgumentException("This bill or loan no longer exists")
            require(!debt.isArchived && debt.status !in setOf(DebtStatus.CANCELLED.name, DebtStatus.PAID.name)) {
                "Only an active bill or loan can be edited"
            }
            val savedCounterparty = validateCounterparty(counterpartyId, allowArchivedId = debt.counterpartyId)
            debtDao.updateDebt(
                debt.copy(
                    title = title.trim(),
                    counterparty = savedCounterparty?.name ?: counterparty.trim(),
                    counterpartyId = savedCounterparty?.id,
                    dueDate = dueDate
                )
            )
        }
    }

    suspend fun recordDebtPayment(
        debtId: String,
        paymentAccountId: String,
        principal: Money,
        interest: Money,
        interestCategoryId: String?,
        paymentDate: Long,
        notes: String = ""
    ) {
        require(!principal.isNegative && !interest.isNegative && (principal.isPositive || interest.isPositive)) {
            "Enter principal or interest greater than zero"
        }
        require(paymentDate > 0L) { "Choose a payment date" }
        database.withTransaction {
            val debt = debtDao.getDebtById(debtId) ?: throw IllegalArgumentException("This bill or loan no longer exists")
            require(!debt.isArchived && debt.status !in setOf(DebtStatus.PAID.name, DebtStatus.CANCELLED.name)) {
                "Restore or reopen this bill or loan before recording a payment"
            }
            require(principal.centavos <= debt.outstandingAmountCentavos) { "Principal cannot exceed the outstanding balance" }
            validateFinancialAccount(paymentAccountId)
            if (interest.isPositive) validateCategory(interestCategoryId ?: "", CategoryType.EXPENSE)
            val recognizedInterestCategory = if (interest.isPositive) interestCategoryId else null
            val entry = AccountingEngine.createDebtPaymentEntry(
                paymentAccountId, debtId, principal, interest, recognizedInterestCategory,
                "Payment · ${debt.title}", paymentDate
            )
            val type = if (debt.kind == DebtKind.LOAN.name) TransactionType.LOAN_REPAYMENT else TransactionType.PAYABLE_PAYMENT
            postJournalEntry(
                entry = entry, transactionType = type, amount = principal + interest, fee = interest,
                primaryAccountId = paymentAccountId, destinationAccountId = debtId,
                categoryId = recognizedInterestCategory, counterparty = debt.counterparty, notes = notes.trim()
            )
            val outstanding = Math.subtractExact(debt.outstandingAmountCentavos, principal.centavos)
            debtDao.insertPayment(DebtPaymentEntity(
                id = UUID.randomUUID().toString(), debtId = debtId, journalEntryId = entry.id,
                paymentAccountId = paymentAccountId, principalCentavos = principal.centavos,
                interestCentavos = interest.centavos, interestCategoryId = recognizedInterestCategory,
                paymentDate = paymentDate, notes = notes.trim()
            ))
            debtDao.updateDebt(debt.copy(
                outstandingAmountCentavos = outstanding,
                status = if (outstanding == 0L) DebtStatus.PAID.name
                    else if (outstanding < debt.originalAmountCentavos) DebtStatus.PARTIALLY_PAID.name
                    else DebtStatus.OPEN.name
            ))
        }
    }

    suspend fun cancelDebt(id: String) {
        database.withTransaction {
            val debt = debtDao.getDebtById(id) ?: throw IllegalArgumentException("This bill or loan no longer exists")
            require(!debt.isArchived && debt.status !in setOf(DebtStatus.PAID.name, DebtStatus.CANCELLED.name)) {
                "Only an open bill or loan can be cancelled"
            }
            require(debtDao.getPaymentsForDebt(id).none { it.reversedAt == null }) {
                "Reverse active payments before cancelling this bill or loan"
            }
            reverseTransactionInCurrentTransaction(debt.recognitionJournalEntryId)
        }
    }

    suspend fun setDebtArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val debt = debtDao.getDebtById(id) ?: throw IllegalArgumentException("This bill or loan no longer exists")
            require(debt.status in setOf(DebtStatus.PAID.name, DebtStatus.CANCELLED.name)) {
                "Pay or cancel this bill or loan before archiving it"
            }
            debtDao.updateDebt(debt.copy(isArchived = isArchived))
        }
    }

    private fun validateDebtFields(title: String, amount: Money, dueDate: Long) {
        require(title.isNotBlank()) { "Enter a name or description" }
        require(amount.isPositive) { "Enter an amount greater than zero" }
        require(dueDate > 0L) { "Choose a due date" }
    }

    val debtsFlow: Flow<List<Debt>> = combine(
        debtDao.getAllDebtsFlow(),
        debtDao.getAllPaymentsFlow(),
        categoryDao.getAllCategoriesFlow(),
        counterpartyDao.getAllFlow()
    ) { debts, payments, categories, counterparties ->
        val categoryNames = categories.associate { it.id to it.name }
        val counterpartiesById = counterparties.associateBy { it.id }
        val paymentsByDebt = payments.groupBy { it.debtId }
        debts.map { debt ->
            debt.toDomainModel(
                categoryName = debt.categoryId?.let(categoryNames::get),
                payments = paymentsByDebt[debt.id].orEmpty().map { it.toDomainModel() }
            ).copy(counterparty = debt.counterpartyId?.let(counterpartiesById::get)?.name ?: debt.counterparty)
        }
    }

    val savingsGoalsFlow: Flow<List<SavingsGoal>> = combine(
        savingsGoalDao.getGoalsFlow(),
        savingsGoalDao.getEventsFlow()
    ) { goals, events ->
        val eventsByGoal = events.groupBy { it.goalId }
        goals.map { goal ->
            goal.toSavingsGoalDomainModel(eventsByGoal[goal.id].orEmpty().map { it.toSavingsGoalEventDomainModel() })
        }
    }

    val interestProductsFlow: Flow<List<InterestProduct>> = combine(
        interestDao.getProductsFlow(),
        interestDao.getCreditsFlow(),
        accountsFlow
    ) { products, credits, accounts ->
        val accountMap = accounts.associateBy { it.id }
        val creditsByProduct = credits.groupBy { it.productId }
        products.mapNotNull { product ->
            val account = accountMap[product.accountId] ?: return@mapNotNull null
            product.toInterestProductDomainModel(
                accountName = account.name,
                accountBalance = account.currentLedgerBalance,
                credits = creditsByProduct[product.id].orEmpty().map { it.toInterestCreditDomainModel() }
            )
        }
    }

    suspend fun createInterestProduct(
        accountId: String,
        type: InterestProductType,
        annualRateBasisPoints: Int,
        taxRateBasisPoints: Int,
        compounding: InterestCompounding,
        openedAt: Long,
        maturityDate: Long?,
        principal: Money?,
        notes: String
    ): String {
        validateInterestTerms(annualRateBasisPoints, taxRateBasisPoints, openedAt, maturityDate, principal, type)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            validateInterestAccount(accountId, type, principal)
            require(interestDao.getProductForAccount(accountId) == null) {
                "This account already has an interest profile. Edit that profile or choose another account."
            }
            interestDao.insertProduct(
                InterestProductEntity(
                    id = id,
                    accountId = accountId,
                    type = type.name,
                    annualRateBasisPoints = annualRateBasisPoints,
                    taxRateBasisPoints = taxRateBasisPoints,
                    compounding = compounding.name,
                    openedAt = openedAt,
                    maturityDate = maturityDate,
                    principalCentavos = principal?.centavos,
                    notes = notes.trim(),
                    isArchived = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    suspend fun updateInterestProduct(
        id: String,
        annualRateBasisPoints: Int,
        taxRateBasisPoints: Int,
        compounding: InterestCompounding,
        openedAt: Long,
        maturityDate: Long?,
        principal: Money?,
        notes: String
    ) {
        database.withTransaction {
            val current = interestDao.getProductById(id)
                ?: throw IllegalArgumentException("This interest profile no longer exists")
            val type = InterestProductType.valueOf(current.type)
            validateInterestTerms(annualRateBasisPoints, taxRateBasisPoints, openedAt, maturityDate, principal, type)
            val hasCredits = interestDao.countCredits(id) > 0L
            require(!hasCredits || (openedAt == current.openedAt && principal?.centavos == current.principalCentavos)) {
                "The opening date and principal are locked after an interest credit is posted; edit the rate or maturity instead."
            }
            validateInterestAccount(current.accountId, type, principal)
            interestDao.updateProduct(
                current.copy(
                    annualRateBasisPoints = annualRateBasisPoints,
                    taxRateBasisPoints = taxRateBasisPoints,
                    compounding = compounding.name,
                    openedAt = openedAt,
                    maturityDate = maturityDate,
                    principalCentavos = principal?.centavos,
                    notes = notes.trim()
                )
            )
        }
    }

    suspend fun setInterestProductArchived(id: String, archived: Boolean) {
        database.withTransaction {
            val product = interestDao.getProductById(id)
                ?: throw IllegalArgumentException("This interest profile no longer exists")
            val account = accountDao.getAccountById(product.accountId)
                ?: throw IllegalStateException("The linked account no longer exists")
            if (!archived) require(!account.isArchived) { "Restore the linked account before restoring this interest profile" }
            interestDao.updateProduct(product.copy(isArchived = archived))
        }
    }

    suspend fun deleteUnusedInterestProduct(id: String) {
        database.withTransaction {
            require(interestDao.getProductById(id) != null) { "This interest profile no longer exists" }
            require(interestDao.countCredits(id) == 0L) {
                "This profile has posted interest history. Archive it to preserve the audit trail."
            }
            interestDao.deleteProduct(id)
        }
    }

    suspend fun recordInterestCredit(
        productId: String,
        gross: Money,
        taxWithheld: Money,
        incomeCategoryId: String,
        taxExpenseCategoryId: String?,
        creditedAt: Long,
        notes: String
    ) {
        require(gross.isPositive) { "Enter gross interest greater than zero" }
        require(!taxWithheld.isNegative && taxWithheld <= gross) { "Tax withheld must be zero or less than or equal to gross interest" }
        require(creditedAt > 0L && creditedAt <= System.currentTimeMillis()) { "Interest credits must use a past or current date" }
        database.withTransaction {
            val product = interestDao.getProductById(productId)
                ?: throw IllegalArgumentException("This interest profile no longer exists")
            require(!product.isArchived) { "Restore this profile before recording interest" }
            require(creditedAt >= product.openedAt) { "The credit date cannot be before the profile opened" }
            validateFinancialAccount(product.accountId)
            validateCategory(incomeCategoryId, CategoryType.INCOME)
            val effectiveTaxCategoryId = if (taxWithheld.isPositive) taxExpenseCategoryId else null
            if (taxWithheld.isPositive) validateCategory(effectiveTaxCategoryId ?: "", CategoryType.EXPENSE)
            val entry = AccountingEngine.createInterestCreditEntry(
                accountId = product.accountId,
                incomeCategoryId = incomeCategoryId,
                taxExpenseCategoryId = effectiveTaxCategoryId,
                gross = gross,
                taxWithheld = taxWithheld,
                description = "Interest credit: ${accountDao.getAccountById(product.accountId)?.name.orEmpty()}",
                timestamp = creditedAt
            )
            postJournalEntry(
                entry = entry,
                transactionType = TransactionType.INTEREST_CREDIT,
                amount = gross,
                fee = taxWithheld,
                primaryAccountId = product.accountId,
                categoryId = incomeCategoryId,
                notes = notes.trim()
            )
            interestDao.insertCredit(
                InterestCreditEntity(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    journalEntryId = entry.id,
                    grossCentavos = gross.centavos,
                    taxCentavos = taxWithheld.centavos,
                    annualRateBasisPoints = product.annualRateBasisPoints,
                    taxRateBasisPoints = product.taxRateBasisPoints,
                    taxExpenseCategoryId = effectiveTaxCategoryId,
                    creditedAt = creditedAt,
                    notes = notes.trim(),
                    reversedAt = null
                )
            )
        }
    }

    private suspend fun validateInterestAccount(accountId: String, type: InterestProductType, principal: Money?) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Choose an existing financial account")
        val accountType = AccountType.valueOf(account.accountType)
        require(accountType.isAsset && !accountType.isReceivable && !account.isArchived) {
            "Choose an active asset account"
        }
        when (type) {
            InterestProductType.SAVINGS_ACCOUNT -> require(accountType in setOf(
                AccountType.TRADITIONAL_BANK, AccountType.DIGITAL_BANK, AccountType.E_WALLET, AccountType.PHYSICAL_SAVINGS
            )) { "Choose a bank, e-wallet, or savings account for an interest profile" }
            InterestProductType.TIME_DEPOSIT -> {
                require(accountType == AccountType.TIME_DEPOSIT) { "Choose an account with the Time Deposit type" }
                val amount = principal ?: throw IllegalArgumentException("Enter the original deposit principal")
                require(amount.isPositive) { "Deposit principal must be greater than zero" }
                require(getAccountById(accountId)?.currentLedgerBalance?.let { it >= amount } == true) {
                    "The time-deposit account balance must cover the original principal"
                }
            }
        }
    }

    private fun validateInterestTerms(
        annualRateBasisPoints: Int,
        taxRateBasisPoints: Int,
        openedAt: Long,
        maturityDate: Long?,
        principal: Money?,
        type: InterestProductType
    ) {
        require(annualRateBasisPoints in 0..1_000_000) { "Annual rate must be from 0% to 10,000%" }
        require(taxRateBasisPoints in 0..10_000) { "Tax rate must be from 0% to 100%" }
        require(openedAt in 1L..System.currentTimeMillis()) { "Opening date cannot be in the future" }
        when (type) {
            InterestProductType.SAVINGS_ACCOUNT -> require(maturityDate == null && principal == null) {
                "Savings profiles use the current account balance and do not have a maturity date"
            }
            InterestProductType.TIME_DEPOSIT -> {
                require(maturityDate != null && maturityDate > openedAt) { "Maturity must be after the opening date" }
                require(principal?.isPositive == true) { "Deposit principal must be greater than zero" }
            }
        }
    }

    suspend fun createSavingsGoal(name: String, target: Money, targetDate: Long?, notes: String): String {
        validateSavingsGoalFields(name, target, targetDate)
        val id = UUID.randomUUID().toString()
        savingsGoalDao.insertGoal(
            SavingsGoalEntity(id, name.trim(), target.centavos, targetDate, notes.trim(), 0L, false, System.currentTimeMillis())
        )
        return id
    }

    suspend fun updateSavingsGoal(id: String, name: String, target: Money, targetDate: Long?, notes: String) {
        validateSavingsGoalFields(name, target, targetDate)
        database.withTransaction {
            val goal = savingsGoalDao.getGoalById(id) ?: throw IllegalArgumentException("This savings goal no longer exists")
            savingsGoalDao.updateGoal(
                goal.copy(name = name.trim(), targetAmountCentavos = target.centavos, targetDate = targetDate, notes = notes.trim())
            )
        }
    }

    suspend fun allocateToSavingsGoal(id: String, amount: Money, date: Long = System.currentTimeMillis(), note: String = "") =
        updateSavingsGoalAllocation(id, amount, SavingsGoalEventType.ALLOCATE, date, note)

    suspend fun releaseFromSavingsGoal(id: String, amount: Money, date: Long = System.currentTimeMillis(), note: String = "") =
        updateSavingsGoalAllocation(id, amount, SavingsGoalEventType.RELEASE, date, note)

    private suspend fun updateSavingsGoalAllocation(
        id: String,
        amount: Money,
        type: SavingsGoalEventType,
        date: Long,
        note: String
    ) {
        require(amount.isPositive) { "Enter an amount greater than zero" }
        require(date > 0L) { "Choose a valid date" }
        database.withTransaction {
            val goal = savingsGoalDao.getGoalById(id) ?: throw IllegalArgumentException("This savings goal no longer exists")
            if (type == SavingsGoalEventType.ALLOCATE) {
                require(!goal.isArchived) { "Restore this goal before adding reserved money" }
                val availableCentavos = Math.subtractExact(currentLiquidCashForGoals().centavos, savingsGoalDao.getTotalAllocatedCentavos())
                require(availableCentavos >= amount.centavos) {
                    "There is not enough available cash to reserve this amount. Release another goal allocation or add funds first."
                }
            } else {
                require(goal.allocatedAmountCentavos >= amount.centavos) { "Release amount exceeds this goal's reserved balance" }
            }
            val updatedAmount = if (type == SavingsGoalEventType.ALLOCATE) {
                Math.addExact(goal.allocatedAmountCentavos, amount.centavos)
            } else {
                Math.subtractExact(goal.allocatedAmountCentavos, amount.centavos)
            }
            savingsGoalDao.updateGoal(goal.copy(allocatedAmountCentavos = updatedAmount))
            savingsGoalDao.insertEvent(
                SavingsGoalEventEntity(UUID.randomUUID().toString(), id, type.name, amount.centavos, date, note.trim())
            )
        }
    }

    suspend fun setSavingsGoalArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val goal = savingsGoalDao.getGoalById(id) ?: throw IllegalArgumentException("This savings goal no longer exists")
            savingsGoalDao.updateGoal(goal.copy(isArchived = isArchived))
        }
    }

    suspend fun deleteUnusedSavingsGoal(id: String) {
        database.withTransaction {
            val goal = savingsGoalDao.getGoalById(id) ?: throw IllegalArgumentException("This savings goal no longer exists")
            require(goal.allocatedAmountCentavos == 0L && savingsGoalDao.countEvents(id) == 0L) {
                "This goal has allocation history. Archive it to preserve its records."
            }
            savingsGoalDao.deleteGoal(id)
        }
    }

    private fun validateSavingsGoalFields(name: String, target: Money, targetDate: Long?) {
        require(name.isNotBlank()) { "Enter a savings goal name" }
        require(target.isPositive) { "Enter a target amount greater than zero" }
        require(targetDate == null || targetDate > 0L) { "Choose a valid target date" }
    }

    private suspend fun currentLiquidCashForGoals(): Money {
        val eligibleAccounts = accountDao.getAllAccounts().filter {
            val type = runCatching { AccountType.valueOf(it.accountType) }.getOrNull()
            !it.isArchived && it.includeInDashboardTotals && type?.isLiquid == true
        }
        var total = 0L
        eligibleAccounts.forEach { account ->
            val aggregates = journalDao.getBalanceAggregateForAccount(account.id)
            val debits = aggregates.filter { it.lineType == LineType.DEBIT.name }
                .fold(0L) { sum, row -> Math.addExact(sum, row.totalCentavos) }
            val credits = aggregates.filter { it.lineType == LineType.CREDIT.name }
                .fold(0L) { sum, row -> Math.addExact(sum, row.totalCentavos) }
            total = Math.addExact(total, Math.subtractExact(debits, credits))
        }
        return Money(total)
    }

    // Transactions
    val transactionsFlow: Flow<List<Transaction>> = combine(
        journalDao.getAllJournalEntriesFlow(),
        accountDao.getAllAccountsFlow(),
        categoryDao.getAllCategoriesFlow(),
        receivableDao.getAllReceivablesFlow(),
        debtDao.getAllDebtsFlow()
    ) { entries, accounts, categories, receivables, debts ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val receivableMap = receivables.associateBy { it.id }
        val debtMap = debts.associateBy { it.id }
        val reversedEntryIds = entries.mapNotNull { it.reversesEntryId }.toSet()

        entries.map { entry ->
            val accountName = accountMap[entry.primaryAccountId]?.name
                ?: receivableMap[entry.primaryAccountId]?.clientName?.let { "Receivable · $it" }
                ?: debtMap[entry.primaryAccountId]?.title?.let { "Payable · $it" }
                ?: "Account"
            val destAccountName = entry.destinationAccountId?.let { id ->
                accountMap[id]?.name ?: debtMap[id]?.title?.let { "Payable · $it" }
            }
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
    }.combine(interestDao.getCreditsFlow()) { transactions, interestCredits ->
        val creditsByEntry = interestCredits.associateBy { it.journalEntryId }
        transactions.map { transaction ->
            val credit = creditsByEntry[transaction.journalEntryId]
            if (credit == null) transaction else transaction.copy(
                secondaryCategoryId = credit.taxExpenseCategoryId
            )
        }
    }.combine(categoryDao.getAllCategoriesFlow()) { transactions, categories ->
        val categoryNames = categories.associate { it.id to it.name }
        transactions.map { transaction ->
            transaction.copy(secondaryCategoryName = transaction.secondaryCategoryId?.let(categoryNames::get))
        }
    }.combine(transactionTagDao.getAllTagsFlow()) { transactions, tags ->
        transactions to tags.associateBy { it.id }
    }.combine(transactionTagDao.getAllAssignmentsFlow()) { (transactions, tagsById), assignments ->
        val tagIdsByEntry = assignments.groupBy { it.journalEntryId }.mapValues { (_, rows) ->
            rows.map { it.tagId }.toSet()
        }
        transactions.map { transaction ->
            val assignedTags = tagIdsByEntry[transaction.journalEntryId].orEmpty()
                .mapNotNull { tagsById[it] }
                .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            transaction.copy(
                tagIds = assignedTags.map { it.id }.toSet(),
                tagNames = assignedTags.map { it.name }
            )
        }
    }

    val budgetsFlow: Flow<List<Budget>> = combine(
        budgetDao.getAllBudgetsFlow(),
        categoryDao.getAllCategoriesFlow(),
        transactionsFlow
    ) { budgetEntities, categoryEntities, transactions ->
        val categoryNames = categoryEntities.associate { it.id to it.name }
        val spendingByCategoryPeriod = BudgetSpendingCalculator.aggregate(transactions)
        budgetEntities.map { budget ->
            val key = BudgetSpendingKey(budget.categoryId, budget.year, budget.month)
            Budget(
                id = budget.id,
                categoryId = budget.categoryId,
                categoryName = categoryNames[budget.categoryId] ?: "Category",
                year = budget.year,
                month = budget.month,
                limit = Money(budget.limitCentavos),
                spent = spendingByCategoryPeriod[key] ?: Money.ZERO,
                isArchived = budget.isArchived
            )
        }
    }

    suspend fun createBudget(categoryId: String, year: Int, month: Int, limit: Money): String {
        validateBudgetValues(year, month, limit)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            validateBudgetCategory(categoryId)
            require(budgetDao.getBudgetForCategoryPeriod(categoryId, year, month) == null) {
                "A budget already exists for this category and month"
            }
            budgetDao.insertBudget(
                BudgetEntity(
                    id = id,
                    categoryId = categoryId,
                    year = year,
                    month = month,
                    limitCentavos = limit.centavos
                )
            )
        }
        return id
    }

    suspend fun updateBudget(id: String, categoryId: String, year: Int, month: Int, limit: Money) {
        validateBudgetValues(year, month, limit)
        database.withTransaction {
            val current = budgetDao.getBudgetById(id)
                ?: throw IllegalArgumentException("This budget no longer exists")
            validateBudgetCategory(categoryId, allowArchived = current.isArchived && categoryId == current.categoryId)
            val existing = budgetDao.getBudgetForCategoryPeriod(categoryId, year, month)
            require(existing == null || existing.id == id) {
                "A budget already exists for this category and month"
            }
            budgetDao.updateBudget(
                current.copy(
                    categoryId = categoryId,
                    year = year,
                    month = month,
                    limitCentavos = limit.centavos
                )
            )
        }
    }

    suspend fun setBudgetArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val budget = budgetDao.getBudgetById(id)
                ?: throw IllegalArgumentException("This budget no longer exists")
            if (!isArchived) validateBudgetCategory(budget.categoryId)
            budgetDao.setArchived(id, isArchived)
        }
    }

    suspend fun deleteBudget(id: String) {
        database.withTransaction {
            require(budgetDao.getBudgetById(id) != null) { "This budget no longer exists" }
            budgetDao.deleteBudget(id)
        }
    }

    private fun validateBudgetValues(year: Int, month: Int, limit: Money) {
        require(year in 1900..9999 && month in 1..12) { "Choose a valid budget month" }
        require(limit.isPositive) { "Enter a budget limit greater than zero" }
    }

    private suspend fun validateBudgetCategory(categoryId: String, allowArchived: Boolean = false) {
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Choose an existing expense category")
        require(category.type == CategoryType.EXPENSE.name && (allowArchived || !category.isArchived)) {
            "Choose an active expense category"
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

    suspend fun importCsvTransactions(rows: List<CsvImportedTransaction>): CsvImportExecutionResult {
        var imported = 0
        var duplicates = 0
        val failures = mutableListOf<Pair<Int, String>>()
        rows.forEach { row ->
            try {
                val wasImported = database.withTransaction {
                    val duplicateCount = journalDao.countMatchingImportDuplicate(
                        transactionType = row.transactionType.name,
                        primaryAccountId = row.accountId,
                        destinationAccountId = row.destinationAccountId,
                        categoryId = row.categoryId,
                        amountCentavos = row.amount.centavos,
                        feeCentavos = row.fee.centavos,
                        description = row.description,
                        counterparty = row.counterparty,
                        dayStartInclusive = row.dayStartInclusive,
                        nextDayExclusive = row.nextDayExclusive
                    )
                    if (duplicateCount > 0L) {
                        false
                    } else {
                        var postedEntryId: String? = null
                        when (row.transactionType) {
                            TransactionType.INCOME -> {
                                validateFinancialAccount(row.accountId)
                                validateCategory(row.categoryId ?: "", CategoryType.INCOME)
                                val entry = AccountingEngine.createIncomeEntry(
                                    row.accountId, row.categoryId ?: "", row.amount, row.description, row.timestamp
                                )
                                postedEntryId = entry.id
                                postJournalEntry(
                                    entry, TransactionType.INCOME, row.amount, Money.ZERO, row.accountId, null,
                                    row.categoryId, row.counterparty, row.notes
                                )
                            }
                            TransactionType.EXPENSE -> {
                                validateFinancialAccount(row.accountId)
                                validateCategory(row.categoryId ?: "", CategoryType.EXPENSE)
                                val entry = AccountingEngine.createExpenseEntry(
                                    row.accountId, row.categoryId ?: "", row.amount, row.description, row.timestamp
                                )
                                postedEntryId = entry.id
                                postJournalEntry(
                                    entry, TransactionType.EXPENSE, row.amount, Money.ZERO, row.accountId, null,
                                    row.categoryId, row.counterparty, row.notes
                                )
                            }
                            TransactionType.TRANSFER -> {
                                validateFinancialAccount(row.accountId)
                                val destinationId = row.destinationAccountId
                                    ?: throw IllegalArgumentException("Transfer destination is missing")
                                validateFinancialAccount(destinationId)
                                if (row.fee.isPositive) validateCategory(row.feeCategoryId ?: "", CategoryType.EXPENSE)
                                val entry = AccountingEngine.createTransferEntry(
                                    row.accountId, destinationId, row.amount, row.fee, row.feeCategoryId,
                                    row.description, row.timestamp
                                )
                                postedEntryId = entry.id
                                postJournalEntry(
                                    entry, TransactionType.TRANSFER, row.amount, row.fee, row.accountId,
                                    destinationId, row.feeCategoryId, row.counterparty, row.notes
                                )
                            }
                            else -> throw IllegalArgumentException("Only income, expense and transfer rows can be imported")
                        }
                        if (row.tagIds.isNotEmpty()) {
                            val assignableTags = row.tagIds.map { id ->
                                transactionTagDao.getTagById(id)
                                    ?: throw IllegalArgumentException("An imported transaction label no longer exists")
                            }
                            require(assignableTags.none { it.isArchived }) { "Restore an archived label before importing this row" }
                            transactionTagDao.insertAssignments(assignableTags.map { tag ->
                                JournalEntryTagEntity(journalEntryId = requireNotNull(postedEntryId), tagId = tag.id)
                            })
                        }
                        true
                    }
                }
                if (wasImported) imported++ else duplicates++
            } catch (error: Exception) {
                failures += row.lineNumber to (error.localizedMessage ?: "Row could not be imported")
            }
        }
        return CsvImportExecutionResult(imported, duplicates, failures)
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
            require(plannedPaymentDao.getRecordByJournalEntry(journalEntryId) == null) {
                "This expense is linked to a planned payment. Manage its correction from Planned Payments."
            }
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

            plannedPaymentDao.getRecordByJournalEntry(journalEntryId)?.let { payment ->
                require(payment.reversedAt == null) { "This planned payment has already been reversed" }
                plannedPaymentDao.updateRecord(payment.copy(reversedAt = System.currentTimeMillis()))
            }

            when (originalType) {
                TransactionType.INTEREST_CREDIT -> {
                    val credit = interestDao.getCreditByJournalEntry(journalEntryId)
                        ?: throw IllegalStateException("Interest credit history is missing")
                    require(credit.reversedAt == null) { "This interest credit has already been reversed" }
                    interestDao.updateCredit(credit.copy(reversedAt = System.currentTimeMillis()))
                }
                TransactionType.SUBSCRIPTION_PAYMENT -> {
                    val payment = subscriptionPaymentDao.getPaymentByJournalEntry(journalEntryId)
                        ?: throw IllegalStateException("Subscription payment history is missing")
                    require(payment.reversedAt == null) { "This subscription payment has already been reversed" }
                    val latestPayment = subscriptionPaymentDao.getLatestActivePayment(payment.subscriptionId)
                    require(latestPayment?.id == payment.id) {
                        "Reverse later subscription payments first so the billing schedule stays accurate"
                    }
                    val subscription = subscriptionDao.getSubscriptionById(payment.subscriptionId)
                        ?: throw IllegalStateException("The linked subscription is missing")
                    val nextBillingDate = if (subscription.nextBillingDate == payment.nextBillingDateAfter) {
                        payment.previousNextBillingDate
                    } else {
                        subscription.nextBillingDate
                    }
                    subscriptionDao.updateSubscription(
                        subscription.copy(
                            lastPaymentDate = payment.previousLastPaymentDate,
                            nextBillingDate = nextBillingDate
                        )
                    )
                    subscriptionPaymentDao.updatePayment(payment.copy(reversedAt = System.currentTimeMillis()))
                }
                TransactionType.RECEIVABLE_COLLECTION -> {
                    val payment = receivableDao.getPaymentByJournalEntryId(journalEntryId)
                        ?: throw IllegalStateException("Receivable payment history is missing")
                    require(payment.reversedAt == null) { "This payment has already been reversed" }
                    val receivable = receivableDao.getReceivableById(payment.receivableId)
                        ?: throw IllegalStateException("Linked receivable is missing")
                    require(!receivable.isArchived) { "Restore this receivable before reversing a collection" }
                    val restoredOutstanding = Math.addExact(receivable.outstandingAmountCentavos, payment.amountCentavos)
                    require(restoredOutstanding <= receivable.originalAmountCentavos) {
                        "Reversing this payment would exceed the original receivable balance"
                    }
                    receivableDao.updatePayment(payment.copy(reversedAt = System.currentTimeMillis()))
                    val remainingPayments = receivableDao.getPaymentsForReceivable(receivable.id)
                        .filter { it.id != payment.id && it.reversedAt == null }
                    val activeWriteOffs = receivableDao.getWriteOffsForReceivable(receivable.id)
                        .filter { it.reversedAt == null }
                    receivableDao.updateReceivable(
                        receivable.copy(
                            outstandingAmountCentavos = restoredOutstanding,
                            status = statusForReceivableBalance(
                                receivable,
                                restoredOutstanding,
                                remainingPayments.isNotEmpty(),
                                activeWriteOffs.isNotEmpty()
                            )
                        )
                    )
                }
                TransactionType.RECEIVABLE_RECOGNITION -> {
                    val receivable = receivableDao.getReceivableById(originalEntity.primaryAccountId)
                        ?: throw IllegalStateException("Linked receivable is missing")
                    require(!receivable.isArchived) { "Restore this receivable before cancelling it" }
                    val activePayments = receivableDao.getPaymentsForReceivable(receivable.id)
                        .any { it.reversedAt == null }
                    val activeWriteOffs = receivableDao.getWriteOffsForReceivable(receivable.id)
                        .any { it.reversedAt == null }
                    require(!activePayments && !activeWriteOffs) {
                        "Reverse active collections and write-offs before cancelling this receivable"
                    }
                    receivableDao.updateReceivable(
                        receivable.copy(
                            outstandingAmountCentavos = 0L,
                            status = ReceivableStatus.CANCELLED.name
                        )
                    )
                }
                TransactionType.RECEIVABLE_WRITEOFF -> {
                    val writeOff = receivableDao.getWriteOffByJournalEntryId(journalEntryId)
                        ?: throw IllegalStateException("Receivable write-off history is missing")
                    require(writeOff.reversedAt == null) { "This write-off has already been reversed" }
                    val receivable = receivableDao.getReceivableById(writeOff.receivableId)
                        ?: throw IllegalStateException("Linked receivable is missing")
                    require(!receivable.isArchived) { "Restore this receivable before reversing a write-off" }
                    receivableDao.updateWriteOff(writeOff.copy(reversedAt = System.currentTimeMillis()))
                    val paymentsAfterReversal = receivableDao.getPaymentsForReceivable(receivable.id)
                        .filter { it.reversedAt == null }
                    val writeOffsAfterReversal = receivableDao.getWriteOffsForReceivable(receivable.id)
                        .filter { it.reversedAt == null }
                    val activePaymentTotal = paymentsAfterReversal.fold(0L) { total, row -> Math.addExact(total, row.amountCentavos) }
                    val activeWriteOffTotal = writeOffsAfterReversal.fold(0L) { total, row -> Math.addExact(total, row.amountCentavos) }
                    val restoredOutstanding = Math.subtractExact(
                        Math.subtractExact(receivable.originalAmountCentavos, activePaymentTotal),
                        activeWriteOffTotal
                    )
                    require(restoredOutstanding >= 0L) { "Reversing this write-off would make the receivable invalid" }
                    receivableDao.updateReceivable(
                        receivable.copy(
                            outstandingAmountCentavos = restoredOutstanding,
                            status = statusForReceivableBalance(
                                receivable,
                                restoredOutstanding,
                                paymentsAfterReversal.isNotEmpty(),
                                writeOffsAfterReversal.isNotEmpty()
                            )
                        )
                    )
                }
                TransactionType.PAYABLE_PAYMENT, TransactionType.LOAN_REPAYMENT -> {
                    val payment = debtDao.getPaymentByJournalEntryId(journalEntryId)
                        ?: throw IllegalStateException("Debt payment history is missing")
                    require(payment.reversedAt == null) { "This payment has already been reversed" }
                    val debt = debtDao.getDebtById(payment.debtId)
                        ?: throw IllegalStateException("Linked bill or loan is missing")
                    require(!debt.isArchived && debt.status != DebtStatus.CANCELLED.name) {
                        "Restore this bill or loan before reversing a payment"
                    }
                    debtDao.updatePayment(payment.copy(reversedAt = System.currentTimeMillis()))
                    val activePayments = debtDao.getPaymentsForDebt(debt.id).filter { it.reversedAt == null }
                    val principalPaid = activePayments.fold(0L) { total, row -> Math.addExact(total, row.principalCentavos) }
                    val outstanding = Math.subtractExact(debt.originalAmountCentavos, principalPaid)
                    require(outstanding >= 0L) { "Reversing this payment would make the balance invalid" }
                    debtDao.updateDebt(debt.copy(
                        outstandingAmountCentavos = outstanding,
                        status = if (outstanding == 0L) DebtStatus.PAID.name
                            else if (outstanding < debt.originalAmountCentavos) DebtStatus.PARTIALLY_PAID.name
                            else DebtStatus.OPEN.name
                    ))
                }
                TransactionType.PAYABLE_RECOGNITION, TransactionType.LOAN_DISBURSEMENT -> {
                    val debt = if (originalType == TransactionType.PAYABLE_RECOGNITION) {
                        debtDao.getDebtById(originalEntity.primaryAccountId)
                    } else {
                        originalEntity.destinationAccountId?.let { debtDao.getDebtById(it) }
                    } ?: throw IllegalStateException("Linked bill or loan is missing")
                    require(!debt.isArchived) { "Restore this bill or loan before cancelling it" }
                    require(debtDao.getPaymentsForDebt(debt.id).none { it.reversedAt == null }) {
                        "Reverse active payments before cancelling this bill or loan"
                    }
                    debtDao.updateDebt(debt.copy(outstandingAmountCentavos = 0L, status = DebtStatus.CANCELLED.name))
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

    // Expected income
    val expectedIncomeFlow: Flow<List<ExpectedIncome>> = combine(
        expectedIncomeDao.getAllExpectedIncomeFlow(),
        categoryDao.getAllCategoriesFlow(),
        counterpartyDao.getAllFlow()
    ) { records, categories, counterparties ->
        val names = categories.associate { it.id to it.name }
        val counterpartyNames = counterparties.associate { it.id to it.name }
        records.map {
            it.toDomainModel(
                names[it.incomeCategoryId] ?: "Income source",
                it.counterpartyId?.let(counterpartyNames::get)
            )
        }
    }

    // Planned payments are forecasts only. They affect the ledger only when an actual payment is posted.
    val plannedPaymentsFlow: Flow<List<PlannedPayment>> = combine(
        plannedPaymentDao.getAllFlow(),
        plannedPaymentDao.getAllRecordsFlow(),
        categoryDao.getAllCategoriesFlow(),
        counterpartyDao.getAllFlow()
    ) { plans, records, categories, counterparties ->
        val categoryNames = categories.associate { it.id to it.name }
        val counterpartyNames = counterparties.associate { it.id to it.name }
        val paymentsByPlan = records.groupBy { it.plannedPaymentId }
        plans.map { plan ->
            PlannedPayment(
                id = plan.id,
                description = plan.description,
                counterparty = plan.counterpartyId?.let(counterpartyNames::get) ?: plan.counterparty,
                amount = Money(plan.amountCentavos),
                dueDate = plan.dueDate,
                expenseCategoryId = plan.expenseCategoryId,
                categoryName = categoryNames[plan.expenseCategoryId] ?: "Expense category",
                status = PlannedPaymentStatus.valueOf(plan.status),
                isArchived = plan.isArchived,
                createdAt = plan.createdAt,
                notes = plan.notes,
                counterpartyId = plan.counterpartyId,
                payments = paymentsByPlan[plan.id].orEmpty().map { payment ->
                    PlannedPaymentRecord(
                        id = payment.id,
                        plannedPaymentId = payment.plannedPaymentId,
                        journalEntryId = payment.journalEntryId,
                        amount = Money(payment.amountCentavos),
                        paymentDate = payment.paymentDate,
                        notes = payment.notes,
                        reversedAt = payment.reversedAt
                    )
                }
            )
        }
    }

    suspend fun createPlannedPayment(
        description: String,
        counterparty: String,
        amount: Money,
        dueDate: Long,
        expenseCategoryId: String,
        counterpartyId: String? = null,
        notes: String = ""
    ): String {
        validatePlannedPaymentValues(description, counterparty, amount, dueDate, notes)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            validatePlannedPaymentCategory(expenseCategoryId)
            val savedCounterparty = validateCounterparty(counterpartyId)
            plannedPaymentDao.insert(
                PlannedPaymentEntity(
                    id = id,
                    description = description.trim(),
                    counterparty = savedCounterparty?.name ?: counterparty.trim(),
                    amountCentavos = amount.centavos,
                    dueDate = dueDate,
                    expenseCategoryId = expenseCategoryId,
                    notes = notes.trim(),
                    status = PlannedPaymentStatus.PLANNED.name,
                    createdAt = System.currentTimeMillis(),
                    counterpartyId = savedCounterparty?.id
                )
            )
        }
        return id
    }

    suspend fun updatePlannedPayment(
        id: String,
        description: String,
        counterparty: String,
        amount: Money,
        dueDate: Long,
        expenseCategoryId: String,
        counterpartyId: String? = null,
        notes: String = ""
    ) {
        validatePlannedPaymentValues(description, counterparty, amount, dueDate, notes)
        database.withTransaction {
            val current = plannedPaymentDao.getById(id)
                ?: throw IllegalArgumentException("This planned payment no longer exists")
            require(!current.isArchived && current.status == PlannedPaymentStatus.PLANNED.name) {
                "Only active planned payments can be edited"
            }
            require(plannedPaymentDao.countRecords(id) == 0L) {
                "A payment has been recorded. Reverse its history before editing this plan."
            }
            validatePlannedPaymentCategory(expenseCategoryId, current.expenseCategoryId)
            val savedCounterparty = validateCounterparty(counterpartyId, current.counterpartyId)
            plannedPaymentDao.update(
                current.copy(
                    description = description.trim(),
                    counterparty = savedCounterparty?.name ?: counterparty.trim(),
                    amountCentavos = amount.centavos,
                    dueDate = dueDate,
                    expenseCategoryId = expenseCategoryId,
                    notes = notes.trim(),
                    counterpartyId = savedCounterparty?.id
                )
            )
        }
    }

    suspend fun cancelPlannedPayment(id: String) = setPlannedPaymentStatus(id, PlannedPaymentStatus.CANCELLED)
    suspend fun reactivatePlannedPayment(id: String) = setPlannedPaymentStatus(id, PlannedPaymentStatus.PLANNED)

    private suspend fun setPlannedPaymentStatus(id: String, status: PlannedPaymentStatus) {
        database.withTransaction {
            val plan = plannedPaymentDao.getById(id)
                ?: throw IllegalArgumentException("This planned payment no longer exists")
            require(!plan.isArchived) { "Restore this planned payment before changing its status" }
            require(plannedPaymentDao.countActiveRecords(id) == 0L) {
                "Reverse the active payment before changing this plan's status"
            }
            when (status) {
                PlannedPaymentStatus.CANCELLED -> require(plan.status == PlannedPaymentStatus.PLANNED.name) {
                    "Only planned payments can be cancelled"
                }
                PlannedPaymentStatus.PLANNED -> {
                    require(plan.status == PlannedPaymentStatus.CANCELLED.name) {
                        "Only cancelled payments can be reactivated"
                    }
                    validatePlannedPaymentCategory(plan.expenseCategoryId)
                }
            }
            plannedPaymentDao.update(plan.copy(status = status.name))
        }
    }

    suspend fun setPlannedPaymentArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val plan = plannedPaymentDao.getById(id)
                ?: throw IllegalArgumentException("This planned payment no longer exists")
            plannedPaymentDao.update(plan.copy(isArchived = isArchived))
        }
    }

    suspend fun deletePlannedPayment(id: String) {
        database.withTransaction {
            require(plannedPaymentDao.getById(id) != null) { "This planned payment no longer exists" }
            require(plannedPaymentDao.countRecords(id) == 0L) {
                "This payment has accounting history. Archive it to preserve the audit trail."
            }
            plannedPaymentDao.delete(id)
        }
    }

    suspend fun recordPlannedPayment(
        id: String,
        accountId: String,
        actualAmount: Money,
        paymentDate: Long,
        paymentNotes: String = ""
    ): String = database.withTransaction {
        val plan = plannedPaymentDao.getById(id)
            ?: throw IllegalArgumentException("This planned payment no longer exists")
        require(!plan.isArchived && plan.status == PlannedPaymentStatus.PLANNED.name) {
            "Restore and reactivate this plan before recording a payment"
        }
        require(plannedPaymentDao.countActiveRecords(id) == 0L) {
            "This plan already has an active payment. Reverse it before recording another."
        }
        require(actualAmount.isPositive) { "Enter an amount greater than zero" }
        require(paymentDate > 0L) { "Choose a valid payment date" }
        require(paymentDate <= System.currentTimeMillis()) { "Payment date cannot be in the future" }
        require(paymentNotes.length <= 2_000) { "Payment notes must be 2,000 characters or fewer" }
        validateFinancialAccount(accountId)
        val category = validatePlannedPaymentCategory(plan.expenseCategoryId)
        val savedCounterparty = plan.counterpartyId?.let { counterpartyDao.getById(it) }
        val counterpartyName = savedCounterparty?.name ?: plan.counterparty
        val entry = AccountingEngine.createExpenseEntry(accountId, category.id, actualAmount, plan.description, paymentDate)
        postJournalEntry(
            entry = entry,
            transactionType = TransactionType.EXPENSE,
            amount = actualAmount,
            primaryAccountId = accountId,
            categoryId = category.id,
            counterparty = counterpartyName,
            notes = listOf(plan.notes, paymentNotes.trim()).filter { it.isNotBlank() }.joinToString("\n")
        )
        val journalId = entry.id
        plannedPaymentDao.insertRecord(
            PlannedPaymentRecordEntity(
                id = UUID.randomUUID().toString(),
                plannedPaymentId = id,
                journalEntryId = journalId,
                amountCentavos = actualAmount.centavos,
                paymentDate = paymentDate,
                notes = paymentNotes.trim()
            )
        )
        journalId
    }

    suspend fun reversePlannedPaymentRecord(recordId: String) {
        database.withTransaction {
            val record = plannedPaymentDao.getRecordById(recordId)
                ?: throw IllegalArgumentException("This payment history no longer exists")
            require(record.reversedAt == null) { "This payment has already been reversed" }
            reverseTransactionInCurrentTransaction(record.journalEntryId)
        }
    }

    private fun validatePlannedPaymentValues(description: String, counterparty: String, amount: Money, dueDate: Long, notes: String) {
        require(description.isNotBlank()) { "Description is required" }
        require(description.length <= 160) { "Description must be 160 characters or fewer" }
        require(counterparty.length <= 120) { "Payee must be 120 characters or fewer" }
        require(amount.isPositive) { "Planned amount must be positive" }
        require(dueDate > 0L) { "Choose a valid due date" }
        require(notes.length <= 2_000) { "Notes must be 2,000 characters or fewer" }
    }

    private suspend fun validatePlannedPaymentCategory(categoryId: String, allowArchivedId: String? = null): CategoryEntity {
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Select a valid expense category")
        require(category.type == CategoryType.EXPENSE.name && (!category.isArchived || category.id == allowArchivedId)) {
            "Choose an active expense category"
        }
        return category
    }

    suspend fun createExpectedIncome(
        description: String,
        counterparty: String,
        amount: Money,
        expectedDate: Long,
        incomeCategoryId: String,
        counterpartyId: String? = null
    ): String {
        validateExpectedIncomeValues(description, amount, expectedDate)
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            validateExpectedIncomeCategory(incomeCategoryId)
            val savedCounterparty = validateCounterparty(counterpartyId)
            expectedIncomeDao.insert(
                ExpectedIncomeEntity(
                    id = id,
                    description = description.trim(),
                    counterparty = savedCounterparty?.name ?: counterparty.trim(),
                    amountCentavos = amount.centavos,
                    expectedDate = expectedDate,
                    incomeCategoryId = incomeCategoryId,
                    status = ExpectedIncomeStatus.PLANNED.name,
                    createdAt = System.currentTimeMillis(),
                    counterpartyId = savedCounterparty?.id
                )
            )
        }
        return id
    }

    suspend fun updateExpectedIncome(
        id: String,
        description: String,
        counterparty: String,
        amount: Money,
        expectedDate: Long,
        incomeCategoryId: String,
        counterpartyId: String? = null
    ) {
        validateExpectedIncomeValues(description, amount, expectedDate)
        database.withTransaction {
            val current = expectedIncomeDao.getById(id)
                ?: throw IllegalArgumentException("This expected income no longer exists")
            require(current.status == ExpectedIncomeStatus.PLANNED.name && !current.isArchived) {
                "Only active planned income can be edited"
            }
            validateExpectedIncomeCategory(incomeCategoryId, allowArchivedId = current.incomeCategoryId)
            val savedCounterparty = validateCounterparty(counterpartyId, allowArchivedId = current.counterpartyId)
            expectedIncomeDao.update(
                current.copy(
                    description = description.trim(),
                    counterparty = savedCounterparty?.name ?: counterparty.trim(),
                    amountCentavos = amount.centavos,
                    expectedDate = expectedDate,
                    incomeCategoryId = incomeCategoryId,
                    counterpartyId = savedCounterparty?.id
                )
            )
        }
    }

    suspend fun cancelExpectedIncome(id: String) {
        setExpectedIncomeStatus(id, ExpectedIncomeStatus.CANCELLED)
    }

    suspend fun reactivateExpectedIncome(id: String) {
        setExpectedIncomeStatus(id, ExpectedIncomeStatus.PLANNED)
    }

    private suspend fun setExpectedIncomeStatus(id: String, status: ExpectedIncomeStatus) {
        database.withTransaction {
            val current = expectedIncomeDao.getById(id)
                ?: throw IllegalArgumentException("This expected income no longer exists")
            require(!current.isArchived) { "Restore this expected income before changing its status" }
            when (status) {
                ExpectedIncomeStatus.CANCELLED -> require(current.status == ExpectedIncomeStatus.PLANNED.name) {
                    "Only planned income can be cancelled"
                }
                ExpectedIncomeStatus.PLANNED -> {
                    require(current.status == ExpectedIncomeStatus.CANCELLED.name) {
                        "Only cancelled income can be reactivated"
                    }
                    validateExpectedIncomeCategory(current.incomeCategoryId)
                }
                ExpectedIncomeStatus.CONVERTED -> error("Expected income can only be converted to a receivable")
            }
            expectedIncomeDao.update(current.copy(status = status.name))
        }
    }

    suspend fun setExpectedIncomeArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val current = expectedIncomeDao.getById(id)
                ?: throw IllegalArgumentException("This expected income no longer exists")
            expectedIncomeDao.update(current.copy(isArchived = isArchived))
        }
    }

    suspend fun deleteExpectedIncome(id: String) {
        database.withTransaction {
            val current = expectedIncomeDao.getById(id)
                ?: throw IllegalArgumentException("This expected income no longer exists")
            require(current.status != ExpectedIncomeStatus.CONVERTED.name && current.convertedReceivableId == null) {
                "This forecast created a receivable. Archive it to preserve its financial history."
            }
            expectedIncomeDao.delete(id)
        }
    }

    suspend fun convertExpectedIncomeToReceivable(id: String): String = database.withTransaction {
        val expected = expectedIncomeDao.getById(id)
            ?: throw IllegalArgumentException("This expected income no longer exists")
        require(expected.status == ExpectedIncomeStatus.PLANNED.name && !expected.isArchived) {
            "Only active planned income can be converted"
        }
        val category = validateExpectedIncomeCategory(expected.incomeCategoryId)
        val receivableId = UUID.randomUUID().toString()
        val savedCounterparty = expected.counterpartyId?.let { counterpartyId -> counterpartyDao.getById(counterpartyId) }
        val clientName = savedCounterparty?.name ?: expected.counterparty.ifBlank { category.name }
        val description = expected.description.ifBlank { "Expected income from ${category.name}" }
        val receivable = ReceivableEntity(
            id = receivableId,
            clientName = clientName,
            originalAmountCentavos = expected.amountCentavos,
            outstandingAmountCentavos = expected.amountCentavos,
            recognitionDate = System.currentTimeMillis(),
            dueDate = expected.expectedDate,
            linkedCategoryId = expected.incomeCategoryId,
            description = description,
            status = ReceivableStatus.CONFIRMED.name,
            counterpartyId = expected.counterpartyId
        )
        val entry = AccountingEngine.createReceivableRecognitionEntry(
            receivableAccountId = receivableId,
            incomeCategoryId = expected.incomeCategoryId,
            amount = Money(expected.amountCentavos),
            description = description.ifBlank { "Receivable recognized: $clientName" }
        )
        receivableDao.insertReceivable(receivable)
        postJournalEntry(
            entry = entry,
            transactionType = TransactionType.RECEIVABLE_RECOGNITION,
            amount = Money(expected.amountCentavos),
            primaryAccountId = receivableId,
            categoryId = expected.incomeCategoryId,
            counterparty = clientName
        )
        expectedIncomeDao.update(
            expected.copy(
                status = ExpectedIncomeStatus.CONVERTED.name,
                convertedReceivableId = receivableId
            )
        )
        receivableId
    }

    private fun validateExpectedIncomeValues(description: String, amount: Money, expectedDate: Long) {
        require(description.isNotBlank()) { "Description is required" }
        require(amount.isPositive) { "Expected amount must be positive" }
        require(expectedDate > 0L) { "Choose a valid expected date" }
    }

    private suspend fun validateExpectedIncomeCategory(
        categoryId: String,
        allowArchivedId: String? = null
    ): CategoryEntity {
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Select a valid income source")
        require(category.type == CategoryType.INCOME.name && (!category.isArchived || category.id == allowArchivedId)) {
            "Choose an active income category"
        }
        return category
    }

    // Receivables
    val receivablesFlow: Flow<List<Receivable>> = combine(
        receivableDao.getAllReceivablesFlow(),
        receivableDao.getAllPaymentsFlow(),
        receivableDao.getAllWriteOffsFlow(),
        counterpartyDao.getAllFlow()
    ) { receivables, allPayments, allWriteOffs, counterparties ->
        val paymentGroup = allPayments.groupBy { it.receivableId }
        val writeOffGroup = allWriteOffs.groupBy { it.receivableId }
        val counterpartyNames = counterparties.associate { it.id to it.name }
        val now = System.currentTimeMillis()

        receivables.map { recEntity ->
            val payments = paymentGroup[recEntity.id]?.map { it.toDomainModel() } ?: emptyList()
            val writeOffs = writeOffGroup[recEntity.id]?.map { it.toDomainModel() } ?: emptyList()
            val receivable = recEntity.toDomainModel(
                payments,
                writeOffs,
                recEntity.counterpartyId?.let(counterpartyNames::get)
            )
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
        description: String,
        counterpartyId: String? = null
    ): Receivable {
        require(clientName.isNotBlank()) { "Client name is required" }
        require(amount.isPositive) { "Receivable amount must be positive" }
        require(dueDate > 0L) { "Choose a valid due date" }
        val categoryId = incomeCategoryId ?: "cat_other_income"
        val category = categoryDao.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Select a valid income category")
        require(category.type == CategoryType.INCOME.name && !category.isArchived) {
            "Receivable recognition requires an active income category"
        }
        val receivable = database.withTransaction {
            val savedCounterparty = validateCounterparty(counterpartyId)
            val resolvedClientName = savedCounterparty?.name ?: clientName.trim()
            val id = UUID.randomUUID().toString()
            val recEntity = ReceivableEntity(
                id = id,
                clientName = resolvedClientName,
                originalAmountCentavos = amount.centavos,
                outstandingAmountCentavos = amount.centavos,
                recognitionDate = System.currentTimeMillis(),
                dueDate = dueDate,
                linkedCategoryId = categoryId,
                description = description.trim(),
                status = ReceivableStatus.CONFIRMED.name,
                counterpartyId = savedCounterparty?.id
            )
            val entry = AccountingEngine.createReceivableRecognitionEntry(
                receivableAccountId = id,
                incomeCategoryId = categoryId,
                amount = amount,
                description = description.ifBlank { "Receivable recognized: $resolvedClientName" }
            )
            receivableDao.insertReceivable(recEntity)
            postJournalEntry(
                entry = entry,
                transactionType = TransactionType.RECEIVABLE_RECOGNITION,
                amount = amount,
                primaryAccountId = id,
                categoryId = categoryId,
                counterparty = resolvedClientName
            )
            recEntity
        }
        return receivable.toDomainModel(emptyList())
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

    suspend fun updateReceivableDetails(
        id: String,
        clientName: String,
        dueDate: Long,
        description: String,
        counterpartyId: String? = null
    ) {
        require(clientName.isNotBlank()) { "Client name is required" }
        require(dueDate > 0L) { "Choose a valid due date" }
        database.withTransaction {
            val current = receivableDao.getReceivableById(id)
                ?: throw IllegalArgumentException("Receivable no longer exists")
            require(!current.isArchived) { "Restore this receivable before editing it" }
            val savedCounterparty = validateCounterparty(counterpartyId, allowArchivedId = current.counterpartyId)
            val closedStatus = current.status.takeIf {
                it in setOf(ReceivableStatus.PAID.name, ReceivableStatus.CANCELLED.name, ReceivableStatus.WRITTEN_OFF.name)
            }
            val updatedMetadata = current.copy(
                clientName = savedCounterparty?.name ?: clientName.trim(),
                counterpartyId = savedCounterparty?.id,
                dueDate = dueDate,
                description = description.trim()
            )
            val activePayments = receivableDao.getPaymentsForReceivable(id).any { it.reversedAt == null }
            val activeWriteOffs = receivableDao.getWriteOffsForReceivable(id).any { it.reversedAt == null }
            receivableDao.updateReceivable(
                updatedMetadata.copy(
                    status = closedStatus ?: statusForReceivableBalance(
                        updatedMetadata,
                        current.outstandingAmountCentavos,
                        activePayments,
                        activeWriteOffs
                    )
                )
            )
        }
    }

    suspend fun cancelReceivable(id: String) {
        database.withTransaction {
            val receivable = receivableDao.getReceivableById(id)
                ?: throw IllegalArgumentException("Receivable no longer exists")
            require(!receivable.isArchived) { "Restore this receivable before cancelling it" }
            require(receivable.status !in setOf(ReceivableStatus.PAID.name, ReceivableStatus.CANCELLED.name, ReceivableStatus.WRITTEN_OFF.name)) {
                "A settled receivable cannot be cancelled. Write off any remaining uncollectible balance instead."
            }
            val hasActivePayments = receivableDao.getPaymentsForReceivable(id).any { it.reversedAt == null }
            val hasActiveWriteOffs = receivableDao.getWriteOffsForReceivable(id).any { it.reversedAt == null }
            require(!hasActivePayments && !hasActiveWriteOffs) {
                "Reverse active collections and write-offs before cancelling this receivable"
            }
            require(receivable.outstandingAmountCentavos == receivable.originalAmountCentavos) {
                "Reverse all financial history before cancelling this receivable"
            }
            val recognition = journalDao.getFirstEntryByTypeAndPrimaryAccount(
                TransactionType.RECEIVABLE_RECOGNITION.name,
                id
            ) ?: throw IllegalStateException("Receivable recognition history is missing")
            reverseTransactionInCurrentTransaction(recognition.id)
        }
    }

    suspend fun writeOffReceivable(id: String, amount: Money, expenseCategoryId: String, notes: String) {
        require(amount.isPositive) { "Write-off amount must be greater than zero" }
        database.withTransaction {
            val receivable = receivableDao.getReceivableById(id)
                ?: throw IllegalArgumentException("Receivable no longer exists")
            require(!receivable.isArchived) { "Restore this receivable before writing off its balance" }
            require(receivable.status != ReceivableStatus.CANCELLED.name) { "A cancelled receivable cannot be written off" }
            val outstanding = Money(receivable.outstandingAmountCentavos)
            require(outstanding.isPositive) { "There is no outstanding balance to write off" }
            require(amount <= outstanding) { "Write-off cannot exceed the outstanding balance" }
            val category = categoryDao.getCategoryById(expenseCategoryId)
                ?: throw IllegalArgumentException("Select a valid expense category")
            require(category.type == CategoryType.EXPENSE.name && !category.isArchived) {
                "Write-offs require an active expense category"
            }

            val timestamp = System.currentTimeMillis()
            val entry = AccountingEngine.createExpenseEntry(
                accountId = receivable.id,
                expenseCategoryId = expenseCategoryId,
                amount = amount,
                description = "Write-off: ${receivable.clientName}",
                timestamp = timestamp
            )
            postJournalEntry(
                entry = entry,
                transactionType = TransactionType.RECEIVABLE_WRITEOFF,
                amount = amount,
                primaryAccountId = receivable.id,
                categoryId = expenseCategoryId,
                counterparty = receivable.clientName,
                notes = notes.trim()
            )
            receivableDao.insertWriteOff(
                ReceivableWriteOffEntity(
                    id = UUID.randomUUID().toString(),
                    receivableId = id,
                    journalEntryId = entry.id,
                    amountCentavos = amount.centavos,
                    expenseCategoryId = expenseCategoryId,
                    date = timestamp,
                    notes = notes.trim()
                )
            )
            val newOutstanding = (outstanding - amount).centavos
            val activePayments = receivableDao.getPaymentsForReceivable(id).any { it.reversedAt == null }
            val activeWriteOffs = receivableDao.getWriteOffsForReceivable(id).any { it.reversedAt == null }
            receivableDao.updateReceivable(
                receivable.copy(
                    outstandingAmountCentavos = newOutstanding,
                    status = statusForReceivableBalance(receivable, newOutstanding, activePayments, activeWriteOffs)
                )
            )
        }
    }

    suspend fun setReceivableArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val receivable = receivableDao.getReceivableById(id)
                ?: throw IllegalArgumentException("Receivable no longer exists")
            if (isArchived) {
                require(receivable.outstandingAmountCentavos == 0L && receivable.status in setOf(
                    ReceivableStatus.PAID.name,
                    ReceivableStatus.CANCELLED.name,
                    ReceivableStatus.WRITTEN_OFF.name
                )) { "Only settled, cancelled, or written-off receivables can be archived" }
            }
            receivableDao.updateReceivable(receivable.copy(isArchived = isArchived))
        }
    }

    private fun statusForReceivableBalance(
        receivable: ReceivableEntity,
        outstandingCentavos: Long,
        hasActivePayments: Boolean,
        hasActiveWriteOffs: Boolean
    ): String = when {
        outstandingCentavos == 0L && hasActiveWriteOffs -> ReceivableStatus.WRITTEN_OFF.name
        outstandingCentavos == 0L -> ReceivableStatus.PAID.name
        receivable.dueDate < System.currentTimeMillis() -> ReceivableStatus.OVERDUE.name
        hasActivePayments || hasActiveWriteOffs -> ReceivableStatus.PARTIALLY_PAID.name
        else -> ReceivableStatus.CONFIRMED.name
    }

    // Subscriptions
    val subscriptionsFlow: Flow<List<Subscription>> = combine(
        subscriptionDao.getAllSubscriptionsFlow(),
        accountDao.getAllAccountsFlow(),
        subscriptionPaymentDao.getPaymentsFlow(),
        categoryDao.getAllCategoriesFlow(),
        counterpartyDao.getAllFlow()
    ) { subs, accounts, payments, categories, counterparties ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val counterpartyMap = counterparties.associateBy { it.id }
        val paymentsBySubscription = payments.groupBy { it.subscriptionId }
        subs.map { subEntity ->
            val accountName = accountMap[subEntity.assignedAccountId]?.name ?: "Assigned Account"
            subEntity.toDomainModel(accountName).copy(
                merchant = subEntity.counterpartyId?.let(counterpartyMap::get)?.name ?: subEntity.merchant,
                payments = paymentsBySubscription[subEntity.id].orEmpty().map { payment ->
                    payment.toSubscriptionPaymentDomainModel(
                        paymentAccountName = accountMap[payment.paymentAccountId]?.name ?: "Archived account",
                        expenseCategoryName = categoryMap[payment.expenseCategoryId]?.name ?: "Archived category"
                    )
                }
            )
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
            val savedCounterparty = validateCounterparty(subscription.counterpartyId)
            subscriptionDao.insertSubscription(
                SubscriptionEntity.fromDomainModel(
                    subscription.copy(
                        merchant = savedCounterparty?.name ?: subscription.merchant.trim(),
                        counterpartyId = savedCounterparty?.id
                    )
                )
            )
        }
    }

    suspend fun updateSubscription(subscription: Subscription) {
        require(subscription.name.isNotBlank()) { "Subscription name is required" }
        require(subscription.billingAmount.isPositive) { "Subscription billing amount must be positive" }
        database.withTransaction {
            val current = subscriptionDao.getSubscriptionById(subscription.id)
                ?: throw IllegalArgumentException("This subscription no longer exists")
            val account = accountDao.getAccountById(subscription.assignedAccountId)
                ?: throw IllegalArgumentException("Select a valid payment account")
            require(AccountType.valueOf(account.accountType).isLiquid && !account.isArchived) {
                "Choose an active cash, bank, or e-wallet account for this subscription"
            }
            val savedCounterparty = validateCounterparty(subscription.counterpartyId, allowArchivedId = current.counterpartyId)
            subscriptionDao.updateSubscription(
                SubscriptionEntity.fromDomainModel(
                    subscription.copy(
                        merchant = savedCounterparty?.name ?: subscription.merchant.trim(),
                        counterpartyId = savedCounterparty?.id,
                        status = SubscriptionStatus.valueOf(current.status),
                        lastPaymentDate = current.lastPaymentDate,
                        cancellationDate = current.cancellationDate,
                        isArchived = current.isArchived
                    )
                )
            )
        }
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        database.withTransaction {
            val current = subscriptionDao.getSubscriptionById(subscription.id)
                ?: throw IllegalArgumentException("This subscription no longer exists")
            require(current.lastPaymentDate == null) {
                "This subscription has recorded payments. Cancel or archive it to preserve its history."
            }
            require(subscriptionPaymentDao.countPayments(current.id) == 0L) {
                "This subscription has payment history. Archive it to preserve its financial records."
            }
            subscriptionDao.deleteSubscription(current)
        }
    }

    suspend fun recordSubscriptionPayment(
        subscriptionId: String,
        amount: Money,
        expenseCategoryId: String,
        paymentDate: Long,
        notes: String
    ): String {
        require(amount.isPositive) { "Enter a payment amount greater than zero" }
        require(paymentDate in 1L..System.currentTimeMillis()) { "Payment date must be in the past or today" }
        return database.withTransaction {
            val paymentId = UUID.randomUUID().toString()
            val current = subscriptionDao.getSubscriptionById(subscriptionId)
                ?: throw IllegalArgumentException("This subscription no longer exists")
            require(!current.isArchived && current.status == SubscriptionStatus.ACTIVE.name) {
                "Restore and resume this subscription before recording a payment"
            }
            require(paymentDate > (current.lastPaymentDate ?: 0L)) {
                "Payment date must be later than the previous recorded payment"
            }
            val account = accountDao.getAccountById(current.assignedAccountId)
                ?: throw IllegalArgumentException("Choose a valid payment account")
            require(AccountType.valueOf(account.accountType).isLiquid && !account.isArchived) {
                "Restore an active cash, bank, or e-wallet account before recording payment"
            }
            validateCategory(expenseCategoryId, CategoryType.EXPENSE)
            val entry = AccountingEngine.createExpenseEntry(
                accountId = current.assignedAccountId,
                expenseCategoryId = expenseCategoryId,
                amount = amount,
                description = "Subscription payment: ${current.name}",
                timestamp = paymentDate
            )
            postJournalEntry(
                entry = entry,
                transactionType = TransactionType.SUBSCRIPTION_PAYMENT,
                amount = amount,
                fee = Money.ZERO,
                primaryAccountId = current.assignedAccountId,
                categoryId = expenseCategoryId,
                notes = notes.trim()
            )
            val nextBillingDate = SubscriptionSchedule.nextBillingDateAfterPayment(
                current.nextBillingDate, paymentDate, BillingFrequency.valueOf(current.billingFrequency)
            )
            subscriptionDao.updateSubscription(
                current.copy(lastPaymentDate = paymentDate, nextBillingDate = nextBillingDate)
            )
            subscriptionPaymentDao.insertPayment(
                SubscriptionPaymentEntity(
                    id = paymentId,
                    subscriptionId = current.id,
                    journalEntryId = entry.id,
                    paymentAccountId = current.assignedAccountId,
                    expenseCategoryId = expenseCategoryId,
                    amountCentavos = amount.centavos,
                    paymentDate = paymentDate,
                    notes = notes.trim(),
                    reversedAt = null,
                    previousLastPaymentDate = current.lastPaymentDate,
                    previousNextBillingDate = current.nextBillingDate,
                    nextBillingDateAfter = nextBillingDate
                )
            )
            entry.id
        }
    }

    suspend fun setSubscriptionStatus(id: String, status: SubscriptionStatus) {
        database.withTransaction {
            val current = subscriptionDao.getSubscriptionById(id)
                ?: throw IllegalArgumentException("This subscription no longer exists")
            require(!current.isArchived) { "Restore this subscription before changing its status" }
            val cancellationDate = if (status == SubscriptionStatus.CANCELLED) {
                current.cancellationDate ?: System.currentTimeMillis()
            } else {
                null
            }
            subscriptionDao.updateSubscription(current.copy(status = status.name, cancellationDate = cancellationDate))
        }
    }

    suspend fun setSubscriptionArchived(id: String, isArchived: Boolean) {
        database.withTransaction {
            val current = subscriptionDao.getSubscriptionById(id)
                ?: throw IllegalArgumentException("This subscription no longer exists")
            subscriptionDao.updateSubscription(current.copy(isArchived = isArchived))
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
    private val accountsAndSavingsGoalsFlow = combine(accountsFlow, savingsGoalsFlow) { accounts, goals -> accounts to goals }
    val dashboardSummaryFlow: Flow<DashboardSummary> = combine(
        accountsAndSavingsGoalsFlow,
        receivablesFlow,
        subscriptionsFlow,
        transactionsFlow,
        debtsFlow
    ) { accountData, receivables, subscriptions, transactions, debts ->
        DashboardSummaryCalculator.calculate(
            accounts = accountData.first,
            receivables = receivables,
            subscriptions = subscriptions,
            transactions = transactions,
            startOfMonthTimestamp = getStartOfMonthTimestamp(),
            debts = debts,
            savingsGoals = accountData.second
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
