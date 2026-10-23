package com.ralledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ralledger.data.local.dao.AccountDao
import com.ralledger.data.local.dao.CategoryDao
import com.ralledger.data.local.dao.JournalDao
import com.ralledger.data.local.dao.ReceivableDao
import com.ralledger.data.local.dao.ReconciliationDao
import com.ralledger.data.local.dao.SubscriptionDao
import com.ralledger.data.local.entity.AccountEntity
import com.ralledger.data.local.entity.CategoryEntity
import com.ralledger.data.local.entity.JournalEntryEntity
import com.ralledger.data.local.entity.JournalLineEntity
import com.ralledger.data.local.entity.ReceivableEntity
import com.ralledger.data.local.entity.ReceivablePaymentEntity
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
        JournalEntryEntity::class,
        JournalLineEntity::class,
        CategoryEntity::class,
        ReceivableEntity::class,
        ReceivablePaymentEntity::class,
        SubscriptionEntity::class,
        ReconciliationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RalLedgerDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun journalDao(): JournalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun receivableDao(): ReceivableDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun reconciliationDao(): ReconciliationDao

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
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

        @Volatile
        private var INSTANCE: RalLedgerDatabase? = null

        fun getInstance(context: Context): RalLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RalLedgerDatabase::class.java,
                    "ralledger_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
