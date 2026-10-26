package com.ralledger.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ralledger.data.repository.BackupPreview
import com.ralledger.data.repository.BackupRepository
import com.ralledger.data.importing.CsvColumnMapping
import com.ralledger.data.importing.CsvDateOrder
import com.ralledger.data.importing.CsvImportPreview
import com.ralledger.data.importing.FinancialImportFormat
import com.ralledger.data.importing.SmartFinancialImportReader
import com.ralledger.data.importing.ParsedCsvDocument
import com.ralledger.data.importing.TransactionCsvImporter
import com.ralledger.data.export.TransactionCsvExporter
import com.ralledger.data.repository.RalLedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class CsvImportUiState(
    val headers: List<String>,
    val mapping: CsvColumnMapping,
    val preview: CsvImportPreview? = null,
    val isImporting: Boolean = false,
    val sourceFormat: FinancialImportFormat = FinancialImportFormat.CSV,
    val warnings: List<String> = emptyList(),
    val accounts: List<com.ralledger.domain.model.Account> = emptyList(),
    val categories: List<com.ralledger.domain.model.Category> = emptyList(),
    val tags: List<com.ralledger.domain.model.TransactionTag> = emptyList()
)

private data class CsvImportReferenceData(
    val accounts: List<com.ralledger.domain.model.Account>,
    val categories: List<com.ralledger.domain.model.Category>,
    val tags: List<com.ralledger.domain.model.TransactionTag>,
    val transactions: List<com.ralledger.domain.model.Transaction>
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val repository: RalLedgerRepository,
    context: Context
) : ViewModel() {
    private val financialImportReader = SmartFinancialImportReader(context)

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)
    val restorePreview: StateFlow<BackupPreview?> = _restorePreview.asStateFlow()

    private val _csvStatus = MutableStateFlow<String?>(null)
    val csvStatus: StateFlow<String?> = _csvStatus.asStateFlow()

    private val _csvImportState = MutableStateFlow<CsvImportUiState?>(null)
    val csvImportState: StateFlow<CsvImportUiState?> = _csvImportState.asStateFlow()
    private var pendingCsv: ParsedCsvDocument? = null
    private var pendingCsvReferences: CsvImportReferenceData? = null
    private val csvPreviewGeneration = AtomicLong(0L)

    fun exportTransactionsCsv(writeDocument: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            try {
                val transactions = repository.transactionsFlow.first()
                val currencies = repository.accountsFlow.first().associate { it.id to it.currency }
                val bytes = TransactionCsvExporter.encode(transactions, currencies)
                try {
                    writeDocument(bytes)
                } finally {
                    bytes.fill(0)
                }
                _csvStatus.value = "CSV export saved with ${transactions.size} journal entries."
            } catch (error: Exception) {
                _csvStatus.value = "CSV export failed: ${error.localizedMessage ?: "The file could not be saved."}"
            }
        }
    }

    fun inspectFinancialFile(fileName: String?, mimeType: String?, readDocument: suspend () -> ByteArray) {
        val requestId = csvPreviewGeneration.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = readDocument()
                val document = try {
                    withContext(Dispatchers.Default) { financialImportReader.parse(fileName, mimeType, bytes) }
                } finally {
                    bytes.fill(0)
                }
                val references = CsvImportReferenceData(
                    accounts = repository.accountsFlow.first(),
                    categories = repository.categoriesFlow.first(),
                    tags = repository.transactionTagsFlow.first(),
                    transactions = repository.transactionsFlow.first()
                )
                val mapping = TransactionCsvImporter.suggestMapping(document.table.headers)
                val preview = withContext(Dispatchers.Default) {
                    TransactionCsvImporter.preview(
                        document.table, mapping, references.accounts, references.categories,
                        references.tags, references.transactions
                    )
                }.let { it.copy(warnings = document.warnings + it.warnings) }
                if (requestId != csvPreviewGeneration.get()) return@launch
                pendingCsv = document.table
                pendingCsvReferences = references
                _csvImportState.value = CsvImportUiState(
                    headers = document.table.headers,
                    mapping = mapping,
                    preview = preview,
                    sourceFormat = document.format,
                    warnings = document.warnings,
                    accounts = references.accounts,
                    categories = references.categories,
                    tags = references.tags
                )
                _csvStatus.value = null
            } catch (error: Exception) {
                if (requestId == csvPreviewGeneration.get()) {
                    pendingCsv = null
                    pendingCsvReferences = null
                    _csvStatus.value = "File could not be opened: ${error.localizedMessage ?: "Check the file and try again."}"
                }
            }
        }
    }

    fun updateCsvImportMapping(mapping: CsvColumnMapping) {
        if (_csvImportState.value?.isImporting == true) return
        val document = pendingCsv ?: return
        val references = pendingCsvReferences ?: return
        val current = _csvImportState.value ?: return
        val requestId = csvPreviewGeneration.incrementAndGet()
        _csvImportState.value = current.copy(mapping = mapping, preview = null)
        viewModelScope.launch(Dispatchers.Default) {
            val preview = TransactionCsvImporter.preview(
                document, mapping, references.accounts, references.categories,
                references.tags, references.transactions
            )
            if (requestId != csvPreviewGeneration.get()) return@launch
            _csvImportState.value = _csvImportState.value?.let { state ->
                if (state.mapping == mapping && !state.isImporting) {
                    state.copy(preview = preview.copy(warnings = state.warnings + preview.warnings))
                } else state
            }
        }
    }

    fun confirmCsvImport() {
        val preview = _csvImportState.value?.preview ?: return
        val rows = preview.transactions
        if (rows.isEmpty()) return
        csvPreviewGeneration.incrementAndGet()
        _csvImportState.value = _csvImportState.value?.copy(isImporting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.importCsvTransactions(rows)
                val errorSummary = result.failedRows.take(5).joinToString("; ") { (line, message) -> "row $line: $message" }
                _csvStatus.value = buildString {
                    append("Import complete: ${result.imported} added, ${result.duplicates} duplicates skipped")
                    if (result.failedRows.isNotEmpty()) append(", ${result.failedRows.size} rows failed ($errorSummary)")
                    append('.')
                }
                cancelCsvImport()
            } catch (error: Exception) {
                _csvStatus.value = "Import stopped: ${error.localizedMessage ?: "The rows could not be imported."}"
                _csvImportState.value = _csvImportState.value?.copy(isImporting = false)
            }
        }
    }

    fun cancelCsvImport() {
        csvPreviewGeneration.incrementAndGet()
        pendingCsv = null
        pendingCsvReferences = null
        _csvImportState.value = null
    }

    fun createEncryptedBackup(password: String, writeDocument: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            try {
                val preview = backupRepository.writeEncryptedBackupToDocument(password, writeDocument)
                _backupStatus.value = "Encrypted backup saved. ${preview.accounts} accounts, ${preview.counterparties} clients/counterparties, ${preview.transactionTemplates} transaction templates, ${preview.recurringTransactions} recurring transactions, ${preview.expectedIncome} expected-income records, ${preview.plannedPayments} planned payments with ${preview.plannedPaymentEvents} payment-history entries, ${preview.debts} bills and loans, ${preview.journalEntries} journal entries, and ${preview.receivables} receivables included."
            } catch (e: Exception) {
                _backupStatus.value = "Backup failed: ${e.localizedMessage}"
            }
        }
    }

    fun inspectBackup(bytes: ByteArray, password: String) {
        val privateCopy = bytes.copyOf()
        viewModelScope.launch {
            try {
                val preview = backupRepository.prepareRestore(privateCopy, password)
                _restorePreview.value = preview
                _backupStatus.value = if (preview.canRestore) {
                    "Backup opened and passed integrity checks. Review its contents before restoring."
                } else {
                    "Backup opened, but integrity checks found problems. Restore is disabled."
                }
            } catch (e: Exception) {
                _backupStatus.value = "Backup could not be opened: ${e.localizedMessage}"
                _restorePreview.value = null
            } finally {
                privateCopy.fill(0)
            }
        }
    }

    fun confirmRestore() {
        viewModelScope.launch {
            try {
                val preview = backupRepository.restorePreparedBackup()
                _backupStatus.value = "Restore complete. ${preview.accounts} accounts, ${preview.counterparties} clients/counterparties, ${preview.financialProjects} projects, ${preview.projectAssignments} project assignments, ${preview.customReports} saved reports, ${preview.transactionTemplates} transaction templates, ${preview.recurringTransactions} recurring transactions, ${preview.expectedIncome} expected-income records, ${preview.plannedPayments} planned payments with ${preview.plannedPaymentEvents} payment-history entries, ${preview.debts} bills and loans, ${preview.journalEntries} journal entries, and ${preview.receivables} receivables restored."
                _restorePreview.value = null
            } catch (e: Exception) {
                _backupStatus.value = "Restore failed; existing data was retained: ${e.localizedMessage}"
            }
        }
    }

    fun cancelRestore() {
        _restorePreview.value = null
        backupRepository.cancelPreparedRestore()
    }

    class Factory(
        private val backupRepository: BackupRepository,
        private val repository: RalLedgerRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(backupRepository, repository, context) as T
        }
    }
}
