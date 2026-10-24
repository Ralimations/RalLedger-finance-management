package com.ralledger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.data.local.RalLedgerDatabase
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionTemplate
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionTemplateManagementInstrumentedTest {
    @Test
    fun templatesSupportCrudSafeReferencesCategoryMergeAndEncryptedBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, RalLedgerDatabase::class.java).build()
        var backup: ByteArray? = null
        try {
            source.accountDao().insertAccounts(
                listOf(
                    account("template_cash", "Cash"),
                    account("template_bank", "Bank")
                )
            )
            source.categoryDao().insertCategories(
                listOf(
                    category("template_expense_old", "Old supplies", CategoryType.EXPENSE),
                    category("template_expense_target", "Supplies", CategoryType.EXPENSE),
                    category("template_income", "Freelance", CategoryType.INCOME)
                )
            )
            val repository = RalLedgerRepository(source)
            val expenseId = repository.createTransactionTemplate(
                template(
                    name = "Monthly supplies",
                    type = TransactionType.EXPENSE,
                    accountId = "template_cash",
                    categoryId = "template_expense_old",
                    amount = Money(15_250L)
                )
            )
            val transferId = repository.createTransactionTemplate(
                template(
                    name = "Cash to bank",
                    type = TransactionType.TRANSFER,
                    accountId = "template_cash",
                    destinationAccountId = "template_bank",
                    feeCategoryId = "template_expense_old",
                    amount = Money(25_000L),
                    fee = Money(250L)
                )
            )
            val incomeId = repository.createTransactionTemplate(
                template(
                    name = "Freelance invoice",
                    type = TransactionType.INCOME,
                    accountId = "template_bank",
                    categoryId = "template_income",
                    amount = Money(85_000L)
                )
            )

            assertEquals(3, repository.transactionTemplatesFlow.first { it.size == 3 }.size)
            assertTrue(
                runCatching {
                    repository.createTransactionTemplate(
                        template(" monthly   supplies ", TransactionType.EXPENSE, "template_cash", "template_expense_old", amount = Money(100L))
                    )
                }.isFailure
            )
            assertTrue(runCatching { repository.deleteUnusedAccount("template_cash") }.isFailure)
            assertTrue(runCatching { repository.deleteUnusedCategory("template_expense_old") }.isFailure)

            val original = source.transactionTemplateDao().getById(expenseId)!!
            repository.updateTransactionTemplate(original.toDomainModel().copy(name = "Weekly supplies", amount = Money(17_500L)))
            assertEquals("Weekly supplies", source.transactionTemplateDao().getById(expenseId)?.name)
            assertEquals(17_500L, source.transactionTemplateDao().getById(expenseId)?.amountCentavos)

            repository.setTransactionTemplateArchived(incomeId, true)
            assertTrue(source.transactionTemplateDao().getById(incomeId)!!.isArchived)
            repository.setTransactionTemplateArchived(incomeId, false)
            assertFalse(source.transactionTemplateDao().getById(incomeId)!!.isArchived)

            repository.mergeCategory("template_expense_old", "template_expense_target")
            assertEquals("template_expense_target", source.transactionTemplateDao().getById(expenseId)?.categoryId)
            assertEquals("template_expense_target", source.transactionTemplateDao().getById(transferId)?.feeCategoryId)

            val password = "transaction-template-backup"
            backup = BackupRepository(source).createEncryptedBackup(password)
            val destinationBackup = BackupRepository(destination)
            val preview = destinationBackup.prepareRestore(backup!!, password)
            assertTrue(preview.validationErrors.joinToString(), preview.canRestore)
            assertEquals(3, preview.transactionTemplates)
            destinationBackup.restorePreparedBackup()
            assertEquals(source.transactionTemplateDao().getAll(), destination.transactionTemplateDao().getAll())

            repository.deleteTransactionTemplate(transferId)
            assertEquals(2, source.transactionTemplateDao().getAll().size)
        } finally {
            backup?.fill(0)
            source.close()
            destination.close()
        }
    }

    private fun account(id: String, name: String) = AccountEntity(
        id = id,
        name = name,
        institution = "",
        accountType = AccountType.TRADITIONAL_BANK.name,
        currency = "PHP",
        openingBalanceCentavos = 0L,
        iconName = "account_balance",
        colorHex = "#1565C0",
        createdAt = 1L,
        isArchived = false,
        includeInDashboardTotals = true,
        lastReconciledAt = null
    )

    private fun category(id: String, name: String, type: CategoryType) = CategoryEntity(
        id = id,
        name = name,
        type = type.name,
        iconName = "label",
        colorHex = "#2E7D32",
        isDefault = false
    )

    private fun template(
        name: String,
        type: TransactionType,
        accountId: String,
        categoryId: String? = null,
        destinationAccountId: String? = null,
        feeCategoryId: String? = null,
        amount: Money,
        fee: Money = Money.ZERO
    ) = TransactionTemplate(
        id = "draft-id",
        name = name,
        transactionType = type,
        amount = amount,
        fee = fee,
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        feeCategoryId = feeCategoryId,
        counterparty = "Example source",
        description = "Reusable example",
        notes = "Synthetic test data",
        createdAt = 1L,
        updatedAt = 1L
    )
}
