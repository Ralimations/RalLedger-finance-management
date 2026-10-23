package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.DebtKind
import com.ralledger.domain.model.DebtStatus
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtManagementInstrumentedTest {
    @Test
    fun billsLoansPaymentsReversalsAndEncryptedRestoreKeepLedgerBalanced() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccount(
                AccountEntity("cash", "Everyday account", "Test Bank", AccountType.TRADITIONAL_BANK.name,
                    "PHP", 0L, "account_balance", "#1565C0", 1L, false, true, null)
            )
            source.categoryDao().insertCategories(
                listOf(
                    CategoryEntity("expense_bill", "Household bills", "EXPENSE", "receipt", "#C62828", false),
                    CategoryEntity("expense_interest", "Interest", "EXPENSE", "percent", "#AD1457", false),
                    CategoryEntity("expense_target", "Finance costs", "EXPENSE", "payments", "#6A1B9A", false),
                    CategoryEntity("income_only", "Income only", "INCOME", "payments", "#2E7D32", false)
                )
            )
            val repository = RalLedgerRepository(source)
            val dueDate = System.currentTimeMillis() + 14L * 86_400_000L
            assertTrue(runCatching {
                repository.createBill("Invalid bill", "", Money(10_000L), dueDate, "income_only")
            }.isFailure)
            assertTrue(source.debtDao().getAllDebts().isEmpty())

            val billId = repository.createBill("Power bill", "Electric company", Money(10_000L), dueDate, "expense_bill")
            val bill = source.debtDao().getDebtById(billId)!!
            assertEquals(DebtKind.BILL.name, bill.kind)
            assertEquals(10_000L, bill.outstandingAmountCentavos)
            assertEquals("PAYABLE_RECOGNITION", source.journalDao().getJournalEntryById(bill.recognitionJournalEntryId)?.transactionType)
            assertTrue(runCatching { repository.setDebtArchived(billId, true) }.isFailure)

            repository.recordDebtPayment(billId, "cash", Money(2_000L), Money(100L), "expense_interest", System.currentTimeMillis())
            val billPayment = source.debtDao().getPaymentsForDebt(billId).single()
            assertEquals(8_000L, source.debtDao().getDebtById(billId)?.outstandingAmountCentavos)
            assertEquals("PAYABLE_PAYMENT", source.journalDao().getJournalEntryById(billPayment.journalEntryId)?.transactionType)
            assertEquals(-2_100L, repository.getAccountById("cash")?.currentLedgerBalance?.centavos)
            assertTrue(runCatching { repository.deleteUnusedCategory("expense_interest") }.isFailure)

            repository.reverseTransaction(billPayment.journalEntryId)
            assertEquals(10_000L, source.debtDao().getDebtById(billId)?.outstandingAmountCentavos)
            assertEquals(0L, repository.getAccountById("cash")?.currentLedgerBalance?.centavos)
            assertFalse(source.debtDao().getPaymentsForDebt(billId).single().reversedAt == null)
            repository.cancelDebt(billId)
            assertEquals(DebtStatus.CANCELLED.name, source.debtDao().getDebtById(billId)?.status)
            assertEquals(0L, source.debtDao().getDebtById(billId)?.outstandingAmountCentavos)
            repository.setDebtArchived(billId, true)
            repository.setDebtArchived(billId, false)

            val loanId = repository.createLoan("Personal loan", "Sample lender", Money(20_000L), dueDate, "cash")
            assertEquals(20_000L, repository.getAccountById("cash")?.currentLedgerBalance?.centavos)
            assertEquals("LOAN_DISBURSEMENT", source.journalDao().getJournalEntryById(source.debtDao().getDebtById(loanId)!!.recognitionJournalEntryId)?.transactionType)
            repository.recordDebtPayment(loanId, "cash", Money(5_000L), Money(400L), "expense_interest", System.currentTimeMillis())
            val loanPayment = source.debtDao().getPaymentsForDebt(loanId).single()
            assertEquals(15_000L, source.debtDao().getDebtById(loanId)?.outstandingAmountCentavos)
            assertEquals(14_600L, repository.getAccountById("cash")?.currentLedgerBalance?.centavos)
            assertTrue(runCatching { repository.cancelDebt(loanId) }.isFailure)

            repository.reverseTransaction(loanPayment.journalEntryId)
            assertEquals(20_000L, source.debtDao().getDebtById(loanId)?.outstandingAmountCentavos)
            assertEquals(20_000L, repository.getAccountById("cash")?.currentLedgerBalance?.centavos)
            repository.recordDebtPayment(loanId, "cash", Money(3_000L), Money.ZERO, null, System.currentTimeMillis())
            assertEquals(DebtStatus.PARTIALLY_PAID.name, source.debtDao().getDebtById(loanId)?.status)

            repository.mergeCategory("expense_interest", "expense_target")
            assertEquals("expense_target", source.debtDao().getPaymentsForDebt(loanId).first { it.interestCentavos > 0L }.interestCategoryId)
            val balanceBeforeBackup = repository.getAccountById("cash")?.currentLedgerBalance?.centavos
            val password = "debt-management-test-password"
            backup = BackupRepository(source).createEncryptedBackup(password)
            val restore = BackupRepository(destination)
            val preview = restore.prepareRestore(backup!!, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(2, preview.debts)
            assertEquals(3, preview.debtPayments)
            restore.restorePreparedBackup()

            assertEquals(source.debtDao().getAllDebts(), destination.debtDao().getAllDebts())
            assertEquals(source.debtDao().getAllPayments(), destination.debtDao().getAllPayments())
            assertEquals(source.journalDao().getAllJournalEntries(), destination.journalDao().getAllJournalEntries())
            assertEquals(source.journalDao().getAllJournalLines(), destination.journalDao().getAllJournalLines())
            assertEquals(balanceBeforeBackup, RalLedgerRepository(destination).getAccountById("cash")?.currentLedgerBalance?.centavos)
            assertEquals(TransactionType.LOAN_REPAYMENT.name, source.journalDao().getJournalEntryById(
                source.debtDao().getPaymentsForDebt(loanId).last { it.reversedAt == null }.journalEntryId
            )?.transactionType)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }
}
