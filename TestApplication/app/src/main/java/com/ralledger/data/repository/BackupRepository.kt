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
    val journalEntries: Int,
    val journalLines: Int,
    val receivables: Int,
    val payments: Int,
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
            val categories = database.categoryDao()
            val journals = database.journalDao()
            val receivables = database.receivableDao()
            val subscriptions = database.subscriptionDao()
            val reconciliations = database.reconciliationDao()

            // Clear child rows first. Room rolls this entire replacement back if any insert or
            // the verification step fails.
            receivables.clearPayments()
            journals.clearJournalLines()
            journals.clearJournalEntries()
            receivables.clearReceivables()
            subscriptions.clearSubscriptions()
            reconciliations.clearReconciliations()
            categories.clearCategories()
            accounts.clearAccounts()

            if (snapshot.accounts.isNotEmpty()) accounts.insertAccounts(snapshot.accounts)
            if (snapshot.categories.isNotEmpty()) categories.insertCategoriesForRestore(snapshot.categories)
            if (snapshot.entries.isNotEmpty()) journals.insertJournalEntries(snapshot.entries)
            if (snapshot.lines.isNotEmpty()) journals.insertJournalLinesForRestore(snapshot.lines)
            if (snapshot.receivables.isNotEmpty()) receivables.insertReceivables(snapshot.receivables)
            if (snapshot.payments.isNotEmpty()) receivables.insertPayments(snapshot.payments)
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
        entries = database.journalDao().getAllJournalEntries(),
        lines = database.journalDao().getAllJournalLines(),
        receivables = database.receivableDao().getAllReceivables(),
        payments = database.receivableDao().getAllPayments(),
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
        val entries: List<JournalEntryEntity>,
        val lines: List<JournalLineEntity>,
        val receivables: List<ReceivableEntity>,
        val payments: List<ReceivablePaymentEntity>,
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
            put("journalEntries", JSONArray().also { array -> entries.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("journalLines", JSONArray().also { array -> lines.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("receivables", JSONArray().also { array -> receivables.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("receivablePayments", JSONArray().also { array -> payments.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("subscriptions", JSONArray().also { array -> subscriptions.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
            put("reconciliations", JSONArray().also { array -> reconciliations.sortedBy { it.id }.forEach { array.put(it.toJson()) } })
        }

        fun preview(): BackupPreview {
            val errors = mutableListOf<String>()
            fun <T> checkUnique(label: String, rows: List<T>, id: (T) -> String) {
                if (rows.map(id).toSet().size != rows.size) errors += "Duplicate $label IDs"
            }
            checkUnique("account", accounts) { it.id }
            checkUnique("category", categories) { it.id }
            checkUnique("journal entry", entries) { it.id }
            checkUnique("journal line", lines) { it.id }
            checkUnique("receivable", receivables) { it.id }
            checkUnique("payment", payments) { it.id }
            checkUnique("subscription", subscriptions) { it.id }
            checkUnique("reconciliation", reconciliations) { it.id }

            val entryIds = entries.map { it.id }.toSet()
            val linesByEntry = lines.groupBy { it.journalEntryId }
            if (lines.any { it.journalEntryId !in entryIds }) errors += "Journal line references a missing journal entry"
            val categoryIds = categories.map { it.id }.toSet()
            val receivableIds = receivables.map { it.id }.toSet()
            val accountIds = accounts.map { it.id }.toSet()
            val validLineAccounts = accountIds + categoryIds + receivableIds
            if (lines.any { it.accountId !in validLineAccounts }) {
                errors += "Journal line references a missing account, category, or receivable"
            }
            if (entries.any { entry ->
                    entry.primaryAccountId !in validLineAccounts ||
                        (entry.destinationAccountId != null && entry.destinationAccountId !in validLineAccounts) ||
                        (entry.categoryId != null && entry.categoryId !in categoryIds) ||
                        (entry.reversesEntryId != null && entry.reversesEntryId !in entryIds)
                }
            ) errors += "Journal entry references a missing related record"
            if (accounts.any { runCatching { com.ralledger.domain.model.AccountType.valueOf(it.accountType) }.isFailure } ||
                categories.any { runCatching { com.ralledger.domain.model.CategoryType.valueOf(it.type) }.isFailure } ||
                receivables.any { runCatching { com.ralledger.domain.model.ReceivableStatus.valueOf(it.status) }.isFailure } ||
                subscriptions.any {
                    runCatching { com.ralledger.domain.model.BillingFrequency.valueOf(it.billingFrequency) }.isFailure ||
                        runCatching { com.ralledger.domain.model.SubscriptionStatus.valueOf(it.status) }.isFailure
                } ||
                entries.any { runCatching { com.ralledger.domain.model.TransactionType.valueOf(it.transactionType) }.isFailure }
            ) errors += "Backup contains an unsupported financial record type"
            val entryById = entries.associateBy { it.id }
            val reversals = entries.filter { it.transactionType == "REVERSAL" }
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
            if (payments.any { it.receivableId !in receivableById || it.journalEntryId !in entryIds }) {
                errors += "Payment history references a missing receivable or journal entry"
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
                val original = java.math.BigInteger.valueOf(receivable.originalAmountCentavos)
                val outstanding = java.math.BigInteger.valueOf(receivable.outstandingAmountCentavos)
                val closedWithoutCollectionBalance = receivable.status == "CANCELLED" || receivable.status == "WRITTEN_OFF"
                if (receivable.originalAmountCentavos <= 0L || receivable.outstandingAmountCentavos < 0L ||
                    activePaymentRows.any { it.amountCentavos <= 0L } ||
                    (!closedWithoutCollectionBalance && outstanding + activePayments != original) ||
                    (closedWithoutCollectionBalance && receivable.outstandingAmountCentavos != 0L)
                ) {
                    errors += "Receivable ${receivable.id} does not reconcile to its active payment history"
                }
            }

            return BackupPreview(
                createdAt = createdAt,
                accounts = accounts.size,
                categories = categories.size,
                journalEntries = entries.size,
                journalLines = lines.size,
                receivables = receivables.size,
                payments = payments.size,
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
                    entries = json.getJSONArray("journalEntries").objects().map { it.toJournalEntry() },
                    lines = json.getJSONArray("journalLines").objects().map { it.toJournalLine() },
                    receivables = json.getJSONArray("receivables").objects().map { it.toReceivable() },
                    payments = json.getJSONArray("receivablePayments").objects().map { it.toPayment() },
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
    put("colorHex", colorHex); put("isDefault", isDefault)
}
private fun JSONObject.toCategory() = CategoryEntity(
    getString("id"), getString("name"), getString("type"), getString("iconName"), getString("colorHex"), getBoolean("isDefault")
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
}
private fun JSONObject.toReceivable() = ReceivableEntity(
    id = getString("id"), clientName = getString("clientName"), originalAmountCentavos = getLong("originalAmountCentavos"),
    outstandingAmountCentavos = getLong("outstandingAmountCentavos"), recognitionDate = getLong("recognitionDate"),
    dueDate = getLong("dueDate"), linkedCategoryId = stringOrNull("linkedCategoryId"),
    description = getString("description"), status = getString("status")
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

private fun SubscriptionEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("merchant", merchant); put("billingAmountCentavos", billingAmountCentavos)
    put("billingFrequency", billingFrequency); put("assignedAccountId", assignedAccountId); put("nextBillingDate", nextBillingDate)
    putNullable("lastPaymentDate", lastPaymentDate); put("status", status); putNullable("cancellationDate", cancellationDate)
    put("notes", notes)
}
private fun JSONObject.toSubscription() = SubscriptionEntity(
    id = getString("id"), name = getString("name"), merchant = getString("merchant"),
    billingAmountCentavos = getLong("billingAmountCentavos"), billingFrequency = getString("billingFrequency"),
    assignedAccountId = getString("assignedAccountId"), nextBillingDate = getLong("nextBillingDate"),
    lastPaymentDate = longOrNull("lastPaymentDate"), status = getString("status"),
    cancellationDate = longOrNull("cancellationDate"), notes = getString("notes")
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
