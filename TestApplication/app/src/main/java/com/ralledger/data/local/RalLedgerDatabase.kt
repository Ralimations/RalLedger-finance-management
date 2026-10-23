package com.ralledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ralledger.data.local.dao.AccountDao
import com.ralledger.data.local.dao.BudgetDao
import com.ralledger.data.local.dao.CategoryDao
import com.ralledger.data.local.dao.ExpectedIncomeDao
import com.ralledger.data.local.dao.DebtDao
import com.ralledger.data.local.dao.JournalDao
import com.ralledger.data.local.dao.ReceivableDao
import com.ralledger.data.local.dao.ReconciliationDao
import com.ralledger.data.local.dao.SubscriptionDao
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
import com.ralledger.data.local.entity.SubscriptionEntity
import com.ralledger.domain.model.AccountCategory
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        BudgetEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        CategoryEntity::class,
        ReceivableEntity::class,
        ReceivablePaymentEntity::class,
        ReceivableWriteOffEntity::class,
        ExpectedIncomeEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        SubscriptionEntity::class,
        ReconciliationEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class RalLedgerDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun journalDao(): JournalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expectedIncomeDao(): ExpectedIncomeDao
    abstract fun debtDao(): DebtDao
    abstract fun receivableDao(): ReceivableDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun reconciliationDao(): ReconciliationDao

    companion object {
        const val CURRENT_SCHEMA_VERSION = 9
        const val SYSTEM_OPENING_EQUITY_ACCOUNT_ID = "sys_opening_equity"
        const val SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID = "sys_reconcile_equity"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN reversesEntryId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_reversesEntryId ON journal_entries(reversesEntryId)")
                db.execSQL("ALTER TABLE receivable_payments ADD COLUMN reversedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receivable_payments_journalEntryId ON receivable_payments(journalEntryId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentCategoryId TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentCategoryId ON categories(parentCategoryId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS budgets (
                        id TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        month INTEGER NOT NULL,
                        limitCentavos INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id),
                        FOREIGN KEY(categoryId) REFERENCES categories(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_year_month_categoryId ON budgets(year, month, categoryId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receivables ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS receivable_write_offs (
                        id TEXT NOT NULL,
                        receivableId TEXT NOT NULL,
                        journalEntryId TEXT NOT NULL,
                        amountCentavos INTEGER NOT NULL,
                        expenseCategoryId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        reversedAt INTEGER,
                        PRIMARY KEY(id),
                        FOREIGN KEY(receivableId) REFERENCES receivables(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receivable_write_offs_receivableId ON receivable_write_offs(receivableId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receivable_write_offs_journalEntryId ON receivable_write_offs(journalEntryId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS expected_income (
                        id TEXT NOT NULL,
                        description TEXT NOT NULL,
                        counterparty TEXT NOT NULL,
                        amountCentavos INTEGER NOT NULL,
                        expectedDate INTEGER NOT NULL,
                        incomeCategoryId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        convertedReceivableId TEXT,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(incomeCategoryId) REFERENCES categories(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(convertedReceivableId) REFERENCES receivables(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expected_income_incomeCategoryId ON expected_income(incomeCategoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expected_income_convertedReceivableId ON expected_income(convertedReceivableId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expected_income_expectedDate ON expected_income(expectedDate)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS debts (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        counterparty TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        originalAmountCentavos INTEGER NOT NULL,
                        outstandingAmountCentavos INTEGER NOT NULL,
                        dueDate INTEGER NOT NULL,
                        categoryId TEXT,
                        fundingAccountId TEXT,
                        recognitionJournalEntryId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(recognitionJournalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(fundingAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debts_recognitionJournalEntryId ON debts(recognitionJournalEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_categoryId ON debts(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_fundingAccountId ON debts(fundingAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_dueDate ON debts(dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_kind_status ON debts(kind, status)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS debt_payments (
                        id TEXT NOT NULL,
                        debtId TEXT NOT NULL,
                        journalEntryId TEXT NOT NULL,
                        paymentAccountId TEXT NOT NULL,
                        principalCentavos INTEGER NOT NULL,
                        interestCentavos INTEGER NOT NULL,
                        interestCategoryId TEXT,
                        paymentDate INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        reversedAt INTEGER,
                        PRIMARY KEY(id),
                        FOREIGN KEY(debtId) REFERENCES debts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(paymentAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(interestCategoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_debtId ON debt_payments(debtId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debt_payments_journalEntryId ON debt_payments(journalEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_paymentAccountId ON debt_payments(paymentAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_interestCategoryId ON debt_payments(interestCategoryId)")
            }
        }

        @Volatile
        private var INSTANCE: RalLedgerDatabase? = null

        fun getInstance(context: Context): RalLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RalLedgerDatabase::class.java,
                    "ralledger_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDatabase(database)
                    }
                }
            }
        }

        private suspend fun seedDatabase(db: RalLedgerDatabase) = db.withTransaction {
            // Seed System Equity Accounts for Double-Entry
            val systemEquityAccount = AccountEntity(
                id = SYSTEM_OPENING_EQUITY_ACCOUNT_ID,
                name = "Opening Balance Equity",
                institution = "System",
                accountType = AccountType.EQUITY.name,
                currency = "PHP",
                openingBalanceCentavos = 0L,
                iconName = "account_balance",
                colorHex = "#9E9E9E",
                createdAt = System.currentTimeMillis(),
                isArchived = true,
                includeInDashboardTotals = false,
                lastReconciledAt = null
            )

            val systemReconcileAccount = AccountEntity(
                id = SYSTEM_RECONCILIATION_EQUITY_ACCOUNT_ID,
                name = "Reconciliation Equity",
                institution = "System",
                accountType = AccountType.EQUITY.name,
                currency = "PHP",
                openingBalanceCentavos = 0L,
                iconName = "tune",
                colorHex = "#9E9E9E",
                createdAt = System.currentTimeMillis(),
                isArchived = true,
                includeInDashboardTotals = false,
                lastReconciledAt = null
            )

            db.accountDao().insertSystemAccountIfMissing(systemEquityAccount)
            db.accountDao().insertSystemAccountIfMissing(systemReconcileAccount)

            // Seed Default Categories
            val defaultCategories = listOf(
                CategoryEntity("cat_salary", "Salary & Wages", CategoryType.INCOME.name, "payments", "#4CAF50", true),
                CategoryEntity("cat_freelance", "Freelance & Consulting", CategoryType.INCOME.name, "work", "#2196F3", true),
                CategoryEntity("cat_investments", "Investment Returns", CategoryType.INCOME.name, "trending_up", "#009688", true),
                CategoryEntity("cat_other_income", "Other Income", CategoryType.INCOME.name, "attach_money", "#8BC34A", true),

                CategoryEntity("cat_food", "Food & Dining", CategoryType.EXPENSE.name, "restaurant", "#FF5722", true),
                CategoryEntity("cat_utilities", "Utilities & Bills", CategoryType.EXPENSE.name, "bolt", "#FF9800", true),
                CategoryEntity("cat_housing", "Rent & Housing", CategoryType.EXPENSE.name, "home", "#795548", true),
                CategoryEntity("cat_subscriptions", "Subscriptions & Software", CategoryType.EXPENSE.name, "subscriptions", "#9C27B0", true),
                CategoryEntity("cat_transport", "Transportation & Fuel", CategoryType.EXPENSE.name, "directions_car", "#00BCD4", true),
                CategoryEntity("cat_shopping", "Shopping & Supplies", CategoryType.EXPENSE.name, "shopping_cart", "#E91E63", true),
                CategoryEntity("cat_health", "Health & Wellness", CategoryType.EXPENSE.name, "medical_services", "#F44336", true),
                CategoryEntity("cat_other_expense", "General Expense", CategoryType.EXPENSE.name, "receipt_long", "#607D8B", true),

                CategoryEntity("cat_transfer", "Account Transfer", CategoryType.TRANSFER.name, "swap_horiz", "#3F51B5", true),
                CategoryEntity("cat_system", "System Posting", CategoryType.SYSTEM.name, "settings", "#9E9E9E", true)
            )

            db.categoryDao().insertSeedCategoriesIfMissing(defaultCategories)
        }
    }
}
