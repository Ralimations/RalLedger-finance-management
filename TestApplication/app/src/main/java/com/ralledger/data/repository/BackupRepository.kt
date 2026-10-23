package com.ralledger.data.repository

import androidx.room.withTransaction
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.BudgetEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.local.entity.ExpectedIncomeEntity
import com.ralledger.data.local.entity.DebtEntity
import com.ralledger.data.local.entity.DebtPaymentEntity
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
import com.ralledger.data.local.entity.SubscriptionEntity
import com.ralledger.domain.model.LineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPreview(
    val createdAt: Long,
    val accounts: Int,
    val categories: Int,
    val budgets: Int,
    val expectedIncome: Int,
    val debts: Int,
    val debtPayments: Int,
    val savingsGoals: Int = 0,
    val savingsGoalEvents: Int = 0,
    val interestProducts: Int = 0,
    val interestCredits: Int = 0,
    val journalEntries: Int,
    val journalLines: Int,
    val receivables: Int,
    val payments: Int,
    val writeOffs: Int,
    val subscriptions: Int,
    val reconciliations: Int,
    val validationErrors: List<String>
) {
    val canRestore: Boolean get() = validationErrors.isEmpty()
}

class BackupRepository(
    private val database: RalLedgerDatabase
) {
    @Volatile
    private var pendingRestore: BackupSnapshot? = null

    suspend fun createEncryptedBackup(password: String): ByteArray = withContext(Dispatchers.IO) {
        requireValidPassword(password)
        val snapshot = database.withTransaction { readSnapshot() }
        encrypt(snapshot.toJson().toString().toByteArray(StandardCharsets.UTF_8), password)
    }

    suspend fun prepareRestore(encryptedBytes: ByteArray, password: String): BackupPreview = withContext(Dispatchers.IO) {
        requireValidPassword(password)
        val clearText = decrypt(encryptedBytes, password)
        val snapshot = try {
            BackupSnapshot.fromJson(JSONObject(String(clearText, StandardCharsets.UTF_8)))
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup contents are invalid or unsupported", error)
        } finally {
            Arrays.fill(clearText, 0)
        }
        val preview = snapshot.preview()
        pendingRestore = snapshot
        preview
    }

    suspend fun restorePreparedBackup(): BackupPreview = withContext(Dispatchers.IO) {
        val snapshot = pendingRestore ?: throw IllegalStateException("Choose and inspect a backup before restoring")
        val preview = snapshot.preview()
        require(preview.canRestore) {
            "Backup failed integrity checks: ${preview.validationErrors.joinToString()}"
        }

        database.withTransaction {
            val accounts = database.accountDao()
            val budgets = database.budgetDao()
            val categories = database.categoryDao()
            val expectedIncome = database.expectedIncomeDao()
            val debts = database.debtDao()
            val savingsGoals = database.savingsGoalDao()
            val interest = database.interestDao()
            val journals = database.journalDao()
            val receivables = database.receivableDao()
            val subscriptions = database.subscriptionDao()
            val reconciliations = database.reconciliationDao()

            // Clear child rows first. Room rolls this entire replacement back if any insert or
            // the verification step fails.
            receivables.clearPayments()
            receivables.clearWriteOffs()
            expectedIncome.clear()
            debts.clearPayments()
            debts.clearDebts()
            savingsGoals.clearEvents()
            savingsGoals.clearGoals()
            interest.clearCredits()
            interest.clearProducts()
            journals.clearJournalLines()
            journals.clearJournalEntries()
            receivables.clearReceivables()
            subscriptions.clearSubscriptions()
            reconciliations.clearReconciliations()
            budgets.clearBudgets()
            categories.clearCategories()
            accounts.clearAccounts()

            if (snapshot.accounts.isNotEmpty()) accounts.insertAccounts(snapshot.accounts)
            if (snapshot.categories.isNotEmpty()) categories.insertCategoriesForRestore(snapshot.categories)
            if (snapshot.budgets.isNotEmpty()) budgets.insertBudgetsForRestore(snapshot.budgets)
            if (snapshot.entries.isNotEmpty()) journals.insertJournalEntries(snapshot.entries)
            if (snapshot.lines.isNotEmpty()) journals.insertJournalLinesForRestore(snapshot.lines)
            if (snapshot.receivables.isNotEmpty()) receivables.insertReceivables(snapshot.receivables)
            if (snapshot.expectedIncome.isNotEmpty()) expectedIncome.insertAll(snapshot.expectedIncome)
            if (snapshot.debts.isNotEmpty()) debts.insertDebts(snapshot.debts)
            if (snapshot.debtPayments.isNotEmpty()) debts.insertPayments(snapshot.debtPayments)
            if (snapshot.savingsGoals.isNotEmpty()) savingsGoals.insertGoals(snapshot.savingsGoals)
            if (snapshot.savingsGoalEvents.isNotEmpty()) savingsGoals.insertEvents(snapshot.savingsGoalEvents)
            if (snapshot.interestProducts.isNotEmpty()) interest.insertProducts(snapshot.interestProducts)
            if (snapshot.payments.isNotEmpty()) receivables.insertPayments(snapshot.payments)
            if (snapshot.writeOffs.isNotEmpty()) receivables.insertWriteOffs(snapshot.writeOffs)
            if (snapshot.interestCredits.isNotEmpty()) interest.insertCredits(snapshot.interestCredits)
            if (snapshot.subscriptions.isNotEmpty()) subscriptions.insertSubscriptions(snapshot.subscriptions)
            if (snapshot.reconciliations.isNotEmpty()) reconciliations.insertReconciliations(snapshot.reconciliations)

            val restored = readSnapshot().copy(createdAt = snapshot.createdAt)
            check(restored.toJson().toString() == snapshot.toJson().toString()) {
                "Restored records do not match the validated backup"
            }
        }
        pendingRestore = null
        preview
    }

    fun cancelPreparedRestore() {
        pendingRestore = null
    }

    suspend fun writeEncryptedBackupToDocument(password: String, writeDocument: suspend (ByteArray) -> Unit): BackupPreview {
        requireValidPassword(password)
        val snapshot = database.withTransaction { readSnapshot() }
        val bytes = encrypt(snapshot.toJson().toString().toByteArray(StandardCharsets.UTF_8), password)
        try {
            writeDocument(bytes)
        } finally {
            Arrays.fill(bytes, 0)
        }
        return snapshot.preview()
    }

    private suspend fun readSnapshot() = BackupSnapshot(
        createdAt = System.currentTimeMillis(),
        accounts = database.accountDao().getAllAccounts(),
        categories = database.categoryDao().getAllCategories(),
        budgets = database.budgetDao().getAllBudgets(),
        expectedIncome = database.expectedIncomeDao().getAllExpectedIncome(),
        debts = database.debtDao().getAllDebts(),
        debtPayments = database.debtDao().getAllPayments(),
        savingsGoals = database.savingsGoalDao().getAllGoals(),
        savingsGoalEvents = database.savingsGoalDao().getAllEvents(),
        interestProducts = database.interestDao().getAllProducts(),
        interestCredits = database.interestDao().getAllCredits(),
        entries = database.journalDao().getAllJournalEntries(),
        lines = database.journalDao().getAllJournalLines(),
        receivables = database.receivableDao().getAllReceivables(),
        payments = database.receivableDao().getAllPayments(),
        writeOffs = database.receivableDao().getAllWriteOffs(),
        subscriptions = database.subscriptionDao().getAllSubscriptions(),
        reconciliations = database.reconciliationDao().getAllReconciliations()
    )

    private fun requireValidPassword(password: String) {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Use a backup password with at least $MIN_PASSWORD_LENGTH characters"
        }
    }

    private fun encrypt(clearText: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val iterationBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(PBKDF2_ITERATIONS).array()
        val header = MAGIC + iterationBytes + salt + nonce
        val keyBytes = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(clearText)
        } finally {
            Arrays.fill(clearText, 0)
            Arrays.fill(keyBytes, 0)
            Arrays.fill(salt, 0)
            Arrays.fill(nonce, 0)
        }
    }

    private fun decrypt(encrypted: ByteArray, password: String): ByteArray {
        val headerSize = MAGIC.size + Int.SIZE_BYTES + SALT_BYTES + NONCE_BYTES
        require(encrypted.size >= headerSize + TAG_BYTES) { "Backup file is too short" }
        require(encrypted.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "This is not a supported RalLedger encrypted backup"
        }
        val iterationOffset = MAGIC.size
        val iterations = ByteBuffer.wrap(encrypted, iterationOffset, Int.SIZE_BYTES).int
        require(iterations == PBKDF2_ITERATIONS) { "Backup uses an unsupported password derivation setting" }
        val saltOffset = iterationOffset + Int.SIZE_BYTES
        val salt = encrypted.copyOfRange(saltOffset, saltOffset + SALT_BYTES)
        val nonceOffset = saltOffset + SALT_BYTES
        val nonce = encrypted.copyOfRange(nonceOffset, nonceOffset + NONCE_BYTES)
        val header = encrypted.copyOfRange(0, headerSize)
        val keyBytes = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(encrypted, headerSize, encrypted.size - headerSize)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Password is incorrect or the backup was modified", error)
        } finally {
            Arrays.fill(keyBytes, 0)
            Arrays.fill(salt, 0)
            Arrays.fill(nonce, 0)
            Arrays.fill(header, 0)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    private data class BackupSnapshot(
        val createdAt: Long,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val budgets: List<BudgetEntity>,
        val expectedIncome: List<ExpectedIncomeEntity>,
        val debts: List<DebtEntity>,
        val debtPayments: List<DebtPaymentEntity>,
        val savingsGoals: List<SavingsGoalEntity>,
        val savingsGoalEvents: List<SavingsGoalEventEntity>,
        val interestProducts: List<InterestProductEntity>,
        val interestCredits: List<InterestCreditEntity>,
        val entries: List<JournalEntryEntity>,
        val lines: List<JournalLineEntity>,
        val receivables: List<ReceivableEntity>,
        val payments: List<ReceivablePaymentEntity>,
        val writeOffs: List<ReceivableWriteOffEntity>,
        val subscriptions: List<SubscriptionEntity>,
        val reconciliations: List<ReconciliationEntity>
    ) {
        fun toJson() = JSONObject().apply {
            put("appName", "RalLedger")
            put("backupVersion", BACKUP_VERSION)
            put("schemaVersion", RalLedgerDatabase.CURRENT_SCHEMA_VERSION)
            put("createdAt", createdAt)
            put("accounts", JSONArray().also { array -> accounts.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("categories", JSONArray().also { array -> categories.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("budgets", JSONArray().also { array -> budgets.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("expectedIncome", JSONArray().also { array -> expectedIncome.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("debts", JSONArray().also { array -> debts.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("debtPayments", JSONArray().also { array -> debtPayments.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("savingsGoals", JSONArray().also { array -> savingsGoals.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("savingsGoalEvents", JSONArray().also { array -> savingsGoalEvents.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("interestProducts", JSONArray().also { array -> interestProducts.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("interestCredits", JSONArray().also { array -> interestCredits.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("journalEntries", JSONArray().also { array -> entries.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("journalLines", JSONArray().also { array -> lines.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("receivables", JSONArray().also { array -> receivables.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("receivablePayments", JSONArray().also { array -> payments.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("receivableWriteOffs", JSONArray().also { array -> writeOffs.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("subscriptions", JSONArray().also { array -> subscriptions.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("reconciliations", JSONArray().also { array -> reconciliations.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
        }

        fun preview(): BackupPreview {
            val errors = mutableListOf<String>()
            val now = System.currentTimeMillis()
            fun <T> checkUnique(label: String, rows: List<T>, id: (T) -> String) {
                if (rows.map(id).toSet().size != rows.size) errors += "Duplicate $label IDs"
            }
            checkUnique("account", accounts) { it.id }
            checkUnique("category", categories) { it.id }
            checkUnique("budget", budgets) { it.id }
            checkUnique("expected income", expectedIncome) { it.id }
            checkUnique("debt", debts) { it.id }
            checkUnique("debt payment", debtPayments) { it.id }
            checkUnique("savings goal", savingsGoals) { it.id }
            checkUnique("savings goal event", savingsGoalEvents) { it.id }
            checkUnique("interest product", interestProducts) { it.id }
            checkUnique("interest credit", interestCredits) { it.id }
            checkUnique("interest journal entry", interestCredits) { it.journalEntryId }
            checkUnique("journal entry", entries) { it.id }
            checkUnique("journal line", lines) { it.id }
            checkUnique("receivable", receivables) { it.id }
            checkUnique("payment", payments) { it.id }
            checkUnique("receivable write-off", writeOffs) { it.id }
            checkUnique("subscription", subscriptions) { it.id }
            checkUnique("reconciliation", reconciliations) { it.id }

            val goalsById = savingsGoals.associateBy { it.id }
            val interestProductsById = interestProducts.associateBy { it.id }
            val interestProductsByAccount = interestProducts.groupBy { it.accountId }
            if (interestProductsByAccount.values.any { it.size > 1 }) errors += "An account has more than one interest profile"
            if (interestProducts.any { product ->
                    val type = runCatching { com.ralledger.domain.model.InterestProductType.valueOf(product.type) }.getOrNull()
                    val compounding = runCatching { com.ralledger.domain.model.InterestCompounding.valueOf(product.compounding) }.isSuccess
                    product.accountId.isBlank() || product.annualRateBasisPoints !in 0..1_000_000 ||
                        product.taxRateBasisPoints !in 0..10_000 || !compounding || product.openedAt <= 0L || product.openedAt > now || product.createdAt <= 0L ||
                        when (type) {
                            com.ralledger.domain.model.InterestProductType.SAVINGS_ACCOUNT -> product.maturityDate != null || product.principalCentavos != null
                            com.ralledger.domain.model.InterestProductType.TIME_DEPOSIT -> product.maturityDate == null ||
                                product.maturityDate <= product.openedAt || (product.principalCentavos ?: 0L) <= 0L
                            null -> true
                        }
                }) errors += "Interest profile has an invalid account, rate, date, type, or principal"
            if (savingsGoals.any { it.name.isBlank() || it.targetAmountCentavos <= 0L || it.allocatedAmountCentavos < 0L || (it.targetDate != null && it.targetDate <= 0L) || it.createdAt <= 0L }) {
                errors += "Savings goal has an invalid name, target, date, or allocation"
            }
            if (savingsGoalEvents.any { it.goalId !in goalsById || it.amountCentavos <= 0L || it.date <= 0L ||
                    runCatching { com.ralledger.domain.model.SavingsGoalEventType.valueOf(it.type) }.isFailure }) {
                errors += "Savings goal allocation history has a missing goal, invalid type, amount, or date"
            }
            savingsGoals.forEach { goal ->
                val events = savingsGoalEvents.filter { it.goalId == goal.id }
                val eventBalance = events.fold(java.math.BigInteger.ZERO) { sum, event ->
                    val amount = java.math.BigInteger.valueOf(event.amountCentavos)
                    if (event.type == "ALLOCATE") sum + amount else sum - amount
                }
                if (eventBalance.signum() < 0 || eventBalance != java.math.BigInteger.valueOf(goal.allocatedAmountCentavos)) {
                    errors += "Savings goal ${goal.id} does not match its allocation history"
                }
            }

            val entryIds = entries.map { it.id }.toSet()
            val entryById = entries.associateBy { it.id }
            val reversals = entries.filter { it.transactionType == "REVERSAL" }
            val linesByEntry = lines.groupBy { it.journalEntryId }
            if (lines.any { it.journalEntryId !in entryIds }) errors += "Journal line references a missing journal entry"
            val categoryIds = categories.map { it.id }.toSet()
            val receivableIds = receivables.map { it.id }.toSet()
            val debtIds = debts.map { it.id }.toSet()
            val accountIds = accounts.map { it.id }.toSet()
            val validLineAccounts = accountIds + categoryIds + receivableIds + debtIds
            if (lines.any { it.accountId !in validLineAccounts }) {
                errors += "Journal line references a missing account, category, receivable, or payable"
            }
            if (entries.any { entry ->
                    entry.primaryAccountId !in validLineAccounts ||
                        (entry.destinationAccountId != null && entry.destinationAccountId !in validLineAccounts) ||
                        (entry.categoryId != null && entry.categoryId !in categoryIds) ||
                        (entry.reversesEntryId != null && entry.reversesEntryId !in entryIds)
                }
            ) errors += "Journal entry references a missing related record"
            val categoriesById = categories.associateBy { it.id }
            if (interestProducts.any { product ->
                    val account = accountsById[product.accountId]
                    val accountType = account?.accountType?.let { runCatching { com.ralledger.domain.model.AccountType.valueOf(it) }.getOrNull() }
                    account == null || accountType == null || !accountType.isAsset || accountType.isReceivable ||
                        (product.type == "TIME_DEPOSIT" && accountType != com.ralledger.domain.model.AccountType.TIME_DEPOSIT) ||
                        (product.type == "SAVINGS_ACCOUNT" && accountType !in setOf(
                            com.ralledger.domain.model.AccountType.TRADITIONAL_BANK, com.ralledger.domain.model.AccountType.DIGITAL_BANK,
                            com.ralledger.domain.model.AccountType.E_WALLET, com.ralledger.domain.model.AccountType.PHYSICAL_SAVINGS
                        )) || (account.isArchived && !product.isArchived)
                }) errors += "Interest profile references an incompatible or archived account"
            val entriesById = entries.associateBy { it.id }
            if (interestCredits.any { credit ->
                    val product = interestProductsById[credit.productId]
                    val entry = entriesById[credit.journalEntryId]
                    val taxCategory = credit.taxExpenseCategoryId?.let(categoriesById::get)
                    product == null || entry == null || entry.transactionType != "INTEREST_CREDIT" ||
                        entry.primaryAccountId != product.accountId || entry.amountCentavos != credit.grossCentavos ||
                        entry.feeCentavos != credit.taxCentavos || entry.timestamp != credit.creditedAt ||
                        credit.grossCentavos <= 0L || credit.taxCentavos < 0L || credit.taxCentavos > credit.grossCentavos ||
                        credit.annualRateBasisPoints !in 0..1_000_000 || credit.taxRateBasisPoints !in 0..10_000 ||
                        credit.creditedAt < (product?.openedAt ?: Long.MAX_VALUE) ||
                        credit.creditedAt > now ||
                        (credit.taxCentavos > 0L && taxCategory?.type != "EXPENSE") ||
                        (credit.taxCentavos == 0L && credit.taxExpenseCategoryId != null) ||
                        (credit.reversedAt != null && credit.reversedAt <= 0L)
                }) errors += "Interest credit references a missing or mismatched profile, journal, category, or amount"
            interestCredits.forEach { credit ->
                val entry = entriesById[credit.journalEntryId] ?: return@forEach
                val entryLines = lines.filter { it.journalEntryId == entry.id }
                val product = interestProductsById[credit.productId] ?: return@forEach
                val net = credit.grossCentavos - credit.taxCentavos
                val validAccountDebit = if (net == 0L) entryLines.none { it.accountId == product.accountId && it.lineType == "DEBIT" }
                    else entryLines.any { it.accountId == product.accountId && it.lineType == "DEBIT" && it.amountCentavos == net }
                val validIncomeCredit = entry.categoryId?.let { incomeCategoryId ->
                    categoriesById[incomeCategoryId]?.type == "INCOME" &&
                        entryLines.any { it.accountId == incomeCategoryId && it.lineType == "CREDIT" && it.amountCentavos == credit.grossCentavos }
                } == true
                val validTaxDebit = if (credit.taxCentavos == 0L) true else credit.taxExpenseCategoryId?.let { taxCategoryId ->
                    entryLines.any { it.accountId == taxCategoryId && it.lineType == "DEBIT" && it.amountCentavos == credit.taxCentavos }
                } == true
                val expectedLineCount = (if (net > 0L) 1 else 0) + 1 + if (credit.taxCentavos > 0L) 1 else 0
                if (!validAccountDebit || !validIncomeCredit || !validTaxDebit || entryLines.size != expectedLineCount) {
                    errors += "Interest credit ${credit.id} does not match its balanced journal posting"
                }
                val hasReversal = entries.any { it.reversesEntryId == entry.id }
                if ((credit.reversedAt != null) != hasReversal) errors += "Interest credit ${credit.id} reversal state does not match its journal"
            }
            if (categories.any { category ->
                    category.parentCategoryId?.let { parentId ->
                        val parent = categoriesById[parentId]
                        parent == null || parent.type != category.type || parent.parentCategoryId != null
                    } == true
                }
            ) errors += "Category has a missing, mismatched, or nested parent"
            if (budgets.any { it.categoryId !in categoryIds || categoriesById[it.categoryId]?.type != "EXPENSE" }) {
                errors += "Budget references a missing or non-expense category"
            }
            if (budgets.any { it.limitCentavos <= 0L || it.year !in 1900..9999 || it.month !in 1..12 }) {
                errors += "Budget has an invalid period or limit"
            }
            if (budgets.map { Triple(it.categoryId, it.year, it.month) }.toSet().size != budgets.size) {
                errors += "Duplicate category budgets for the same month"
            }
            if (expectedIncome.any {
                    it.incomeCategoryId !in categoryIds || categoriesById[it.incomeCategoryId]?.type != "INCOME" ||
                        it.amountCentavos <= 0L || it.expectedDate <= 0L || it.createdAt <= 0L
                }
            ) errors += "Expected income references a missing or non-income category or has an invalid amount or date"
            if (expectedIncome.any { runCatching { com.ralledger.domain.model.ExpectedIncomeStatus.valueOf(it.status) }.isFailure }) {
                errors += "Expected income contains an unsupported status"
            }
            val expectedReceivableIds = expectedIncome.mapNotNull { it.convertedReceivableId }
            if (expectedReceivableIds.toSet().size != expectedReceivableIds.size) {
                errors += "Multiple expected income records reference the same receivable"
            }
            val receivablesById = receivables.associateBy { it.id }
            val debtsById = debts.associateBy { it.id }
            val accountsById = accounts.associateBy { it.id }
            if (debts.any { debt ->
                    val kind = runCatching { com.ralledger.domain.model.DebtKind.valueOf(debt.kind) }.getOrNull()
                    val status = runCatching { com.ralledger.domain.model.DebtStatus.valueOf(debt.status) }.getOrNull()
                    val category = debt.categoryId?.let(categoriesById::get)
                    val funding = debt.fundingAccountId?.let(accountsById::get)
                    val recognition = entryById[debt.recognitionJournalEntryId]
                    val recognitionReversed = reversals.any { it.reversesEntryId == debt.recognitionJournalEntryId }
                    kind == null || status == null || debt.originalAmountCentavos <= 0L || debt.outstandingAmountCentavos < 0L ||
                        debt.dueDate <= 0L || debt.createdAt <= 0L || debt.title.isBlank() || recognition == null ||
                        recognition.amountCentavos != debt.originalAmountCentavos ||
                        (status == com.ralledger.domain.model.DebtStatus.CANCELLED) != recognitionReversed ||
                        (kind == com.ralledger.domain.model.DebtKind.BILL &&
                            (category?.type != "EXPENSE" || debt.fundingAccountId != null || recognition.transactionType != "PAYABLE_RECOGNITION" ||
                                recognition.primaryAccountId != debt.id || recognition.categoryId != debt.categoryId)) ||
                        (kind == com.ralledger.domain.model.DebtKind.LOAN &&
                            (debt.categoryId != null || funding == null ||
                                runCatching { com.ralledger.domain.model.AccountType.valueOf(funding.accountType) }.getOrNull()?.isAsset != true ||
                                recognition.transactionType != "LOAN_DISBURSEMENT" || recognition.primaryAccountId != debt.fundingAccountId ||
                                recognition.destinationAccountId != debt.id))
                }
            ) errors += "Bill or loan record has invalid type, account, category, amount, or recognition entry"
            if (debtPayments.any { payment ->
                    val debt = debtsById[payment.debtId]
                    val entry = entryById[payment.journalEntryId]
                    val category = payment.interestCategoryId?.let(categoriesById::get)
                    val account = accountsById[payment.paymentAccountId]
                    val expectedType = if (debt?.kind == "LOAN") "LOAN_REPAYMENT" else "PAYABLE_PAYMENT"
                    payment.principalCentavos < 0L || payment.interestCentavos < 0L ||
                        (payment.principalCentavos == 0L && payment.interestCentavos == 0L) ||
                        payment.paymentDate <= 0L || (payment.reversedAt != null && payment.reversedAt <= 0L) ||
                        debt == null || account == null ||
                        runCatching { com.ralledger.domain.model.AccountType.valueOf(account.accountType) }.getOrNull()?.isAsset != true || entry == null ||
                        (payment.interestCentavos > 0L && category?.type != "EXPENSE") ||
                        (payment.interestCentavos == 0L && payment.interestCategoryId != null) ||
                        entry.transactionType != expectedType || entry.primaryAccountId != payment.paymentAccountId ||
                        entry.destinationAccountId != payment.debtId ||
                        java.math.BigInteger.valueOf(entry.amountCentavos) != java.math.BigInteger.valueOf(payment.principalCentavos).add(java.math.BigInteger.valueOf(payment.interestCentavos)) ||
                        entry.feeCentavos != payment.interestCentavos || entry.categoryId != payment.interestCategoryId ||
                        (payment.reversedAt == null && reversals.any { it.reversesEntryId == payment.journalEntryId }) ||
                        (payment.reversedAt != null && reversals.none { it.reversesEntryId == payment.journalEntryId })
                }
            ) errors += "Bill or loan payment history does not match its journal and reversal entries"
            val debtPaymentsById = debtPayments.groupBy { it.debtId }
            debts.forEach { debt ->
                val rows = debtPaymentsById[debt.id].orEmpty()
                val activeRows = rows.filter { it.reversedAt == null }
                val activePrincipal = activeRows.fold(java.math.BigInteger.ZERO) { sum, payment ->
                    sum + java.math.BigInteger.valueOf(payment.principalCentavos)
                }
                val original = java.math.BigInteger.valueOf(debt.originalAmountCentavos)
                val outstanding = java.math.BigInteger.valueOf(debt.outstandingAmountCentavos)
                val cancelled = debt.status == "CANCELLED"
                val expectedStatus = when {
                    cancelled -> "CANCELLED"
                    outstanding.signum() == 0 -> "PAID"
                    activePrincipal.signum() == 0 -> "OPEN"
                    else -> "PARTIALLY_PAID"
                }
                if (activeRows.any { it.principalCentavos < 0L || it.interestCentavos < 0L } ||
                    debtPayments.any { it.debtId == debt.id && it.journalEntryId !in entryIds } ||
                    (cancelled && (debt.outstandingAmountCentavos != 0L || activeRows.isNotEmpty())) ||
                    (!cancelled && outstanding + activePrincipal != original) ||
                    debt.status != expectedStatus ||
                    (debt.isArchived && debt.status !in setOf("PAID", "CANCELLED"))
                ) errors += "Bill or loan ${debt.id} does not reconcile to its active payment history"
            }
            if (expectedIncome.any { expected ->
                    val converted = expected.status == "CONVERTED"
                    val linkedReceivable = expected.convertedReceivableId?.let(receivablesById::get)
                    if (converted) {
                        linkedReceivable == null || linkedReceivable.originalAmountCentavos != expected.amountCentavos ||
                            linkedReceivable.linkedCategoryId != expected.incomeCategoryId ||
                            linkedReceivable.dueDate != expected.expectedDate ||
                            (expected.counterparty.isNotBlank() && linkedReceivable.clientName != expected.counterparty)
                    } else {
                        expected.convertedReceivableId != null
                    }
                }
            ) errors += "Expected income conversion does not match its receivable"
            if (accounts.any { runCatching { com.ralledger.domain.model.AccountType.valueOf(it.accountType) }.isFailure } ||
                categories.any { runCatching { com.ralledger.domain.model.CategoryType.valueOf(it.type) }.isFailure } ||
                receivables.any { runCatching { com.ralledger.domain.model.ReceivableStatus.valueOf(it.status) }.isFailure } ||
                subscriptions.any {
                    runCatching { com.ralledger.domain.model.BillingFrequency.valueOf(it.billingFrequency) }.isFailure ||
                        runCatching { com.ralledger.domain.model.SubscriptionStatus.valueOf(it.status) }.isFailure
                } ||
                entries.any { runCatching { com.ralledger.domain.model.TransactionType.valueOf(it.transactionType) }.isFailure }
            ) errors += "Backup contains an unsupported financial record type"
            if (reversals.any { reversal ->
                    val original = reversal.reversesEntryId?.let(entryById::get)
                    original == null || original.transactionType == "REVERSAL"
                } || reversals.mapNotNull { it.reversesEntryId }.toSet().size != reversals.size
            ) errors += "Reversal history has a missing, nested, or duplicate reversal target"
            entries.forEach { entry ->
                val journalLines = linesByEntry[entry.id].orEmpty()
                val invalidLineType = journalLines.any { it.lineType != LineType.DEBIT.name && it.lineType != LineType.CREDIT.name }
                val debits = journalLines.filter { it.lineType == LineType.DEBIT.name }
                    .fold(java.math.BigInteger.ZERO) { sum, line -> sum + java.math.BigInteger.valueOf(line.amountCentavos) }
                val credits = journalLines.filter { it.lineType == LineType.CREDIT.name }
                    .fold(java.math.BigInteger.ZERO) { sum, line -> sum + java.math.BigInteger.valueOf(line.amountCentavos) }
                if (journalLines.size < 2 || invalidLineType || journalLines.any { it.amountCentavos <= 0L } || debits != credits) {
                    errors += "Journal entry ${entry.id} is unbalanced"
                }
            }

            val receivableById = receivables.associateBy { it.id }
            val paymentsByReceivable = payments.groupBy { it.receivableId }
            val writeOffsByReceivable = writeOffs.groupBy { it.receivableId }
            if (payments.any { it.receivableId !in receivableById || it.journalEntryId !in entryIds }) {
                errors += "Payment history references a missing receivable or journal entry"
            }
            if (writeOffs.any { it.receivableId !in receivableById || it.journalEntryId !in entryIds }) {
                errors += "Write-off history references a missing receivable or journal entry"
            }
            if (payments.any { payment ->
                    val paymentEntry = entryById[payment.journalEntryId]
                    paymentEntry?.transactionType != "RECEIVABLE_COLLECTION" ||
                        (payment.reversedAt == null && entries.any { it.reversesEntryId == payment.journalEntryId }) ||
                        (payment.reversedAt != null && entries.none { it.reversesEntryId == payment.journalEntryId })
                }
            ) errors += "Payment history does not match its collection and reversal journal entries"
            if (payments.any { it.destinationAccountId !in accountIds }) {
                errors += "Payment history references a missing destination account"
            }
            if (writeOffs.any { it.expenseCategoryId !in categoryIds || categoriesById[it.expenseCategoryId]?.type != "EXPENSE" }) {
                errors += "Write-off history references a missing or non-expense category"
            }
            if (writeOffs.any { writeOff ->
                    val entry = entryById[writeOff.journalEntryId]
                    writeOff.amountCentavos <= 0L || entry?.transactionType != "RECEIVABLE_WRITEOFF" ||
                        entry.primaryAccountId != writeOff.receivableId || entry.categoryId != writeOff.expenseCategoryId ||
                        entry.amountCentavos != writeOff.amountCentavos ||
                        (writeOff.reversedAt == null && entries.any { it.reversesEntryId == writeOff.journalEntryId }) ||
                        (writeOff.reversedAt != null && entries.none { it.reversesEntryId == writeOff.journalEntryId })
                }
            ) errors += "Write-off history does not match its journal and reversal entries"
            if (subscriptions.any { it.assignedAccountId !in accountIds }) {
                errors += "Subscription references a missing account"
            }
            if (reconciliations.any { it.accountId !in accountIds || (it.journalEntryId != null && it.journalEntryId !in entryIds) }) {
                errors += "Reconciliation references a missing account or journal entry"
            }
            receivables.forEach { receivable ->
                val activePaymentRows = paymentsByReceivable[receivable.id].orEmpty().filter { it.reversedAt == null }
                val activePayments = activePaymentRows.fold(java.math.BigInteger.ZERO) { sum, payment ->
                    sum + java.math.BigInteger.valueOf(payment.amountCentavos)
                }
                val activeWriteOffRows = writeOffsByReceivable[receivable.id].orEmpty().filter { it.reversedAt == null }
                val activeWriteOffs = activeWriteOffRows.fold(java.math.BigInteger.ZERO) { sum, writeOff ->
                    sum + java.math.BigInteger.valueOf(writeOff.amountCentavos)
                }
                val original = java.math.BigInteger.valueOf(receivable.originalAmountCentavos)
                val outstanding = java.math.BigInteger.valueOf(receivable.outstandingAmountCentavos)
                val cancelled = receivable.status == "CANCELLED"
                val writtenOff = receivable.status == "WRITTEN_OFF"
                if (receivable.originalAmountCentavos <= 0L || receivable.outstandingAmountCentavos < 0L ||
                    activePaymentRows.any { it.amountCentavos <= 0L } ||
                    activeWriteOffRows.any { it.amountCentavos <= 0L } ||
                    (cancelled && (receivable.outstandingAmountCentavos != 0L || activePayments.signum() != 0 || activeWriteOffs.signum() != 0)) ||
                    (!cancelled && outstanding + activePayments + activeWriteOffs != original) ||
                    (writtenOff && (receivable.outstandingAmountCentavos != 0L || activeWriteOffs.signum() == 0)) ||
                    (receivable.isArchived && (receivable.outstandingAmountCentavos != 0L || receivable.status !in setOf("PAID", "CANCELLED", "WRITTEN_OFF")))
                ) {
                    errors += "Receivable ${receivable.id} does not reconcile to its active payment history"
                }
            }

            return BackupPreview(
                createdAt = createdAt,
                accounts = accounts.size,
                categories = categories.size,
                budgets = budgets.size,
                expectedIncome = expectedIncome.size,
                debts = debts.size,
                debtPayments = debtPayments.size,
                savingsGoals = savingsGoals.size,
                savingsGoalEvents = savingsGoalEvents.size,
                interestProducts = interestProducts.size,
                interestCredits = interestCredits.size,
                journalEntries = entries.size,
                journalLines = lines.size,
                receivables = receivables.size,
                payments = payments.size,
                writeOffs = writeOffs.size,
                subscriptions = subscriptions.size,
                reconciliations = reconciliations.size,
                validationErrors = errors.distinct()
            )
        }

        companion object {
            fun fromJson(json: JSONObject): BackupSnapshot {
                require(json.getString("appName") == "RalLedger") { "Backup belongs to another application" }
                require(json.getInt("backupVersion") == BACKUP_VERSION) { "Unsupported backup version" }
                val schemaVersion = json.getInt("schemaVersion")
                require(schemaVersion in 2..RalLedgerDatabase.CURRENT_SCHEMA_VERSION) {
                    "Backup database schema is not supported by this release"
                }
                return BackupSnapshot(
                    createdAt = json.getLong("createdAt"),
                    accounts = json.getJSONArray("accounts").objects().map { it.toAccount() },
                    categories = json.getJSONArray("categories").objects().map { it.toCategory() },
                    budgets = json.optJSONArray("budgets")?.objects()?.map { it.toBudget() }.orEmpty(),
                    expectedIncome = json.optJSONArray("expectedIncome")?.objects()?.map { it.toExpectedIncome() }.orEmpty(),
                    debts = json.optJSONArray("debts")?.objects()?.map { it.toDebt() }.orEmpty(),
                    debtPayments = json.optJSONArray("debtPayments")?.objects()?.map { it.toDebtPayment() }.orEmpty(),
                    savingsGoals = json.optJSONArray("savingsGoals")?.objects()?.map { it.toSavingsGoal() }.orEmpty(),
                    savingsGoalEvents = json.optJSONArray("savingsGoalEvents")?.objects()?.map { it.toSavingsGoalEvent() }.orEmpty(),
                    interestProducts = json.optJSONArray("interestProducts")?.objects()?.map { it.toInterestProduct() }.orEmpty(),
                    interestCredits = json.optJSONArray("interestCredits")?.objects()?.map { it.toInterestCredit() }.orEmpty(),
                    entries = json.getJSONArray("journalEntries").objects().map { it.toJournalEntry() },
                    lines = json.getJSONArray("journalLines").objects().map { it.toJournalLine() },
                    receivables = json.getJSONArray("receivables").objects().map { it.toReceivable() },
                    payments = json.getJSONArray("receivablePayments").objects().map { it.toPayment() },
                    writeOffs = json.optJSONArray("receivableWriteOffs")?.objects()?.map { it.toReceivableWriteOff() }.orEmpty(),
                    subscriptions = json.getJSONArray("subscriptions").objects().map { it.toSubscription() },
                    reconciliations = json.getJSONArray("reconciliations").objects().map { it.toReconciliation() }
                )
            }
        }
    }

    private companion object {
        const val BACKUP_VERSION = 1
        const val MIN_PASSWORD_LENGTH = 12
        const val PBKDF2_ITERATIONS = 310_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        val MAGIC = "RALBKUP1".toByteArray(StandardCharsets.US_ASCII)
        val random = SecureRandom()
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private fun AccountEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("institution", institution); put("accountType", accountType)
    put("currency", currency); put("openingBalanceCentavos", openingBalanceCentavos); put("iconName", iconName)
    put("colorHex", colorHex); put("createdAt", createdAt); put("isArchived", isArchived)
    put("includeInDashboardTotals", includeInDashboardTotals); putNullable("lastReconciledAt", lastReconciledAt)
    put("description", description)
}
private fun JSONObject.toAccount() = AccountEntity(
    id = getString("id"), name = getString("name"), institution = getString("institution"),
    accountType = getString("accountType"), currency = getString("currency"),
    openingBalanceCentavos = getLong("openingBalanceCentavos"), iconName = getString("iconName"),
    colorHex = getString("colorHex"), createdAt = getLong("createdAt"), isArchived = getBoolean("isArchived"),
    includeInDashboardTotals = getBoolean("includeInDashboardTotals"), lastReconciledAt = longOrNull("lastReconciledAt"),
    description = optString("description", "")
)

private fun CategoryEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type); put("iconName", iconName)
    put("colorHex", colorHex); put("isDefault", isDefault); putNullable("parentCategoryId", parentCategoryId)
    put("isArchived", isArchived)
}
private fun JSONObject.toCategory() = CategoryEntity(
    id = getString("id"), name = getString("name"), type = getString("type"), iconName = getString("iconName"),
    colorHex = getString("colorHex"), isDefault = getBoolean("isDefault"),
    parentCategoryId = stringOrNull("parentCategoryId"), isArchived = optBoolean("isArchived", false)
)

private fun BudgetEntity.toJson() = JSONObject().apply {
    put("id", id); put("categoryId", categoryId); put("year", year); put("month", month)
    put("limitCentavos", limitCentavos); put("isArchived", isArchived)
}
private fun JSONObject.toBudget() = BudgetEntity(
    id = getString("id"), categoryId = getString("categoryId"), year = getInt("year"),
    month = getInt("month"), limitCentavos = getLong("limitCentavos"), isArchived = optBoolean("isArchived", false)
)

private fun ExpectedIncomeEntity.toJson() = JSONObject().apply {
    put("id", id); put("description", description); put("counterparty", counterparty)
    put("amountCentavos", amountCentavos); put("expectedDate", expectedDate)
    put("incomeCategoryId", incomeCategoryId); put("status", status)
    putNullable("convertedReceivableId", convertedReceivableId); put("isArchived", isArchived)
    put("createdAt", createdAt)
}
private fun JSONObject.toExpectedIncome() = ExpectedIncomeEntity(
    id = getString("id"), description = getString("description"), counterparty = getString("counterparty"),
    amountCentavos = getLong("amountCentavos"), expectedDate = getLong("expectedDate"),
    incomeCategoryId = getString("incomeCategoryId"), status = getString("status"),
    convertedReceivableId = stringOrNull("convertedReceivableId"), isArchived = optBoolean("isArchived", false),
    createdAt = getLong("createdAt")
)

private fun DebtEntity.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("counterparty", counterparty); put("kind", kind)
    put("originalAmountCentavos", originalAmountCentavos); put("outstandingAmountCentavos", outstandingAmountCentavos)
    put("dueDate", dueDate); putNullable("categoryId", categoryId); putNullable("fundingAccountId", fundingAccountId)
    put("recognitionJournalEntryId", recognitionJournalEntryId); put("status", status)
    put("isArchived", isArchived); put("createdAt", createdAt)
}
private fun JSONObject.toDebt() = DebtEntity(
    id = getString("id"), title = getString("title"), counterparty = getString("counterparty"), kind = getString("kind"),
    originalAmountCentavos = getLong("originalAmountCentavos"), outstandingAmountCentavos = getLong("outstandingAmountCentavos"),
    dueDate = getLong("dueDate"), categoryId = stringOrNull("categoryId"), fundingAccountId = stringOrNull("fundingAccountId"),
    recognitionJournalEntryId = getString("recognitionJournalEntryId"), status = getString("status"),
    isArchived = optBoolean("isArchived", false), createdAt = getLong("createdAt")
)

private fun DebtPaymentEntity.toJson() = JSONObject().apply {
    put("id", id); put("debtId", debtId); put("journalEntryId", journalEntryId); put("paymentAccountId", paymentAccountId)
    put("principalCentavos", principalCentavos); put("interestCentavos", interestCentavos)
    putNullable("interestCategoryId", interestCategoryId); put("paymentDate", paymentDate); put("notes", notes)
    putNullable("reversedAt", reversedAt)
}
private fun JSONObject.toDebtPayment() = DebtPaymentEntity(
    id = getString("id"), debtId = getString("debtId"), journalEntryId = getString("journalEntryId"),
    paymentAccountId = getString("paymentAccountId"), principalCentavos = getLong("principalCentavos"),
    interestCentavos = getLong("interestCentavos"), interestCategoryId = stringOrNull("interestCategoryId"),
    paymentDate = getLong("paymentDate"), notes = optString("notes", ""), reversedAt = longOrNull("reversedAt")
)

private fun SavingsGoalEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("targetAmountCentavos", targetAmountCentavos)
    putNullable("targetDate", targetDate); put("notes", notes); put("allocatedAmountCentavos", allocatedAmountCentavos)
    put("isArchived", isArchived); put("createdAt", createdAt)
}
private fun JSONObject.toSavingsGoal() = SavingsGoalEntity(
    id = getString("id"), name = getString("name"), targetAmountCentavos = getLong("targetAmountCentavos"),
    targetDate = longOrNull("targetDate"), notes = optString("notes", ""),
    allocatedAmountCentavos = getLong("allocatedAmountCentavos"), isArchived = optBoolean("isArchived", false),
    createdAt = getLong("createdAt")
)
private fun SavingsGoalEventEntity.toJson() = JSONObject().apply {
    put("id", id); put("goalId", goalId); put("type", type); put("amountCentavos", amountCentavos)
    put("date", date); put("note", note)
}
private fun JSONObject.toSavingsGoalEvent() = SavingsGoalEventEntity(
    id = getString("id"), goalId = getString("goalId"), type = getString("type"),
    amountCentavos = getLong("amountCentavos"), date = getLong("date"), note = optString("note", "")
)

private fun InterestProductEntity.toJson() = JSONObject().apply {
    put("id", id); put("accountId", accountId); put("type", type)
    put("annualRateBasisPoints", annualRateBasisPoints); put("taxRateBasisPoints", taxRateBasisPoints)
    put("compounding", compounding); put("openedAt", openedAt); putNullable("maturityDate", maturityDate)
    putNullable("principalCentavos", principalCentavos); put("notes", notes); put("isArchived", isArchived)
    put("createdAt", createdAt)
}
private fun JSONObject.toInterestProduct() = InterestProductEntity(
    id = getString("id"), accountId = getString("accountId"), type = getString("type"),
    annualRateBasisPoints = getInt("annualRateBasisPoints"), taxRateBasisPoints = getInt("taxRateBasisPoints"),
    compounding = getString("compounding"), openedAt = getLong("openedAt"), maturityDate = longOrNull("maturityDate"),
    principalCentavos = longOrNull("principalCentavos"), notes = optString("notes", ""),
    isArchived = getBoolean("isArchived"), createdAt = getLong("createdAt")
)

private fun InterestCreditEntity.toJson() = JSONObject().apply {
    put("id", id); put("productId", productId); put("journalEntryId", journalEntryId)
    put("grossCentavos", grossCentavos); put("taxCentavos", taxCentavos)
    put("annualRateBasisPoints", annualRateBasisPoints); put("taxRateBasisPoints", taxRateBasisPoints)
    putNullable("taxExpenseCategoryId", taxExpenseCategoryId); put("creditedAt", creditedAt)
    put("notes", notes); putNullable("reversedAt", reversedAt)
}
private fun JSONObject.toInterestCredit() = InterestCreditEntity(
    id = getString("id"), productId = getString("productId"), journalEntryId = getString("journalEntryId"),
    grossCentavos = getLong("grossCentavos"), taxCentavos = getLong("taxCentavos"),
    annualRateBasisPoints = getInt("annualRateBasisPoints"), taxRateBasisPoints = getInt("taxRateBasisPoints"),
    taxExpenseCategoryId = stringOrNull("taxExpenseCategoryId"), creditedAt = getLong("creditedAt"),
    notes = optString("notes", ""), reversedAt = longOrNull("reversedAt")
)

private fun JournalEntryEntity.toJson() = JSONObject().apply {
    put("id", id); put("timestamp", timestamp); put("description", description); put("transactionType", transactionType)
    put("amountCentavos", amountCentavos); put("feeCentavos", feeCentavos); put("primaryAccountId", primaryAccountId)
    putNullable("destinationAccountId", destinationAccountId); putNullable("categoryId", categoryId)
    put("counterparty", counterparty); put("notes", notes); putNullable("reversesEntryId", reversesEntryId)
}
private fun JSONObject.toJournalEntry() = JournalEntryEntity(
    id = getString("id"), timestamp = getLong("timestamp"), description = getString("description"),
    transactionType = getString("transactionType"), amountCentavos = getLong("amountCentavos"),
    feeCentavos = getLong("feeCentavos"), primaryAccountId = getString("primaryAccountId"),
    destinationAccountId = stringOrNull("destinationAccountId"), categoryId = stringOrNull("categoryId"),
    counterparty = getString("counterparty"), notes = getString("notes"), reversesEntryId = stringOrNull("reversesEntryId")
)

private fun JournalLineEntity.toJson() = JSONObject().apply {
    put("id", id); put("journalEntryId", journalEntryId); put("accountId", accountId)
    put("lineType", lineType); put("amountCentavos", amountCentavos)
}
private fun JSONObject.toJournalLine() = JournalLineEntity(
    getString("id"), getString("journalEntryId"), getString("accountId"), getString("lineType"), getLong("amountCentavos")
)

private fun ReceivableEntity.toJson() = JSONObject().apply {
    put("id", id); put("clientName", clientName); put("originalAmountCentavos", originalAmountCentavos)
    put("outstandingAmountCentavos", outstandingAmountCentavos); put("recognitionDate", recognitionDate)
    put("dueDate", dueDate); putNullable("linkedCategoryId", linkedCategoryId); put("description", description); put("status", status)
    put("isArchived", isArchived)
}
private fun JSONObject.toReceivable() = ReceivableEntity(
    id = getString("id"), clientName = getString("clientName"), originalAmountCentavos = getLong("originalAmountCentavos"),
    outstandingAmountCentavos = getLong("outstandingAmountCentavos"), recognitionDate = getLong("recognitionDate"),
    dueDate = getLong("dueDate"), linkedCategoryId = stringOrNull("linkedCategoryId"),
    description = getString("description"), status = getString("status"), isArchived = optBoolean("isArchived", false)
)

private fun ReceivablePaymentEntity.toJson() = JSONObject().apply {
    put("id", id); put("receivableId", receivableId); put("journalEntryId", journalEntryId)
    put("amountCentavos", amountCentavos); put("destinationAccountId", destinationAccountId)
    put("paymentDate", paymentDate); put("notes", notes); putNullable("reversedAt", reversedAt)
}
private fun JSONObject.toPayment() = ReceivablePaymentEntity(
    id = getString("id"), receivableId = getString("receivableId"), journalEntryId = getString("journalEntryId"),
    amountCentavos = getLong("amountCentavos"), destinationAccountId = getString("destinationAccountId"),
    paymentDate = getLong("paymentDate"), notes = getString("notes"), reversedAt = longOrNull("reversedAt")
)

private fun ReceivableWriteOffEntity.toJson() = JSONObject().apply {
    put("id", id); put("receivableId", receivableId); put("journalEntryId", journalEntryId)
    put("amountCentavos", amountCentavos); put("expenseCategoryId", expenseCategoryId)
    put("date", date); put("notes", notes); putNullable("reversedAt", reversedAt)
}
private fun JSONObject.toReceivableWriteOff() = ReceivableWriteOffEntity(
    id = getString("id"), receivableId = getString("receivableId"), journalEntryId = getString("journalEntryId"),
    amountCentavos = getLong("amountCentavos"), expenseCategoryId = getString("expenseCategoryId"),
    date = getLong("date"), notes = getString("notes"), reversedAt = longOrNull("reversedAt")
)

private fun SubscriptionEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("merchant", merchant); put("billingAmountCentavos", billingAmountCentavos)
    put("billingFrequency", billingFrequency); put("assignedAccountId", assignedAccountId); put("nextBillingDate", nextBillingDate)
    putNullable("lastPaymentDate", lastPaymentDate); put("status", status); putNullable("cancellationDate", cancellationDate)
    put("notes", notes); put("isArchived", isArchived)
}
private fun JSONObject.toSubscription() = SubscriptionEntity(
    id = getString("id"), name = getString("name"), merchant = getString("merchant"),
    billingAmountCentavos = getLong("billingAmountCentavos"), billingFrequency = getString("billingFrequency"),
    assignedAccountId = getString("assignedAccountId"), nextBillingDate = getLong("nextBillingDate"),
    lastPaymentDate = longOrNull("lastPaymentDate"), status = getString("status"),
    cancellationDate = longOrNull("cancellationDate"), notes = getString("notes"), isArchived = optBoolean("isArchived", false)
)

private fun ReconciliationEntity.toJson() = JSONObject().apply {
    put("id", id); put("accountId", accountId); put("timestamp", timestamp)
    put("recordedBalanceCentavos", recordedBalanceCentavos); put("actualBalanceCentavos", actualBalanceCentavos)
    put("differenceCentavos", differenceCentavos); put("adjustmentReason", adjustmentReason)
    putNullable("journalEntryId", journalEntryId)
}
private fun JSONObject.toReconciliation() = ReconciliationEntity(
    id = getString("id"), accountId = getString("accountId"), timestamp = getLong("timestamp"),
    recordedBalanceCentavos = getLong("recordedBalanceCentavos"), actualBalanceCentavos = getLong("actualBalanceCentavos"),
    differenceCentavos = getLong("differenceCentavos"), adjustmentReason = getString("adjustmentReason"),
    journalEntryId = stringOrNull("journalEntryId")
)
