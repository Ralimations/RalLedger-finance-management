package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.DebtEntity
import com.ralledger.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY dueDate ASC, createdAt DESC")
    fun getAllDebtsFlow(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY id ASC")
    suspend fun getAllDebts(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: String): DebtEntity?

    @Query("SELECT * FROM debt_payments ORDER BY paymentDate DESC, id ASC")
    fun getAllPaymentsFlow(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments ORDER BY id ASC")
    suspend fun getAllPayments(): List<DebtPaymentEntity>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY paymentDate DESC, id ASC")
    suspend fun getPaymentsForDebt(debtId: String): List<DebtPaymentEntity>

    @Query("SELECT * FROM debt_payments WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getPaymentByJournalEntryId(journalEntryId: String): DebtPaymentEntity?

    @Query("SELECT COUNT(*) FROM debt_payments WHERE paymentAccountId = :accountId")
    suspend fun countPaymentsForAccount(accountId: String): Long

    @Query("SELECT COUNT(*) FROM debts WHERE fundingAccountId = :accountId")
    suspend fun countDebtsForFundingAccount(accountId: String): Long

    @Query("SELECT COUNT(*) FROM debts WHERE categoryId = :categoryId")
    suspend fun countDebtsForCategory(categoryId: String): Long

    @Query("SELECT COUNT(*) FROM debt_payments WHERE interestCategoryId = :categoryId")
    suspend fun countPaymentsForCategory(categoryId: String): Long

    @Query("UPDATE debts SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignDebtCategory(sourceId: String, targetId: String)

    @Query("UPDATE debt_payments SET interestCategoryId = :targetId WHERE interestCategoryId = :sourceId")
    suspend fun reassignPaymentCategory(sourceId: String, targetId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: DebtEntity)

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: DebtPaymentEntity)

    @Update
    suspend fun updatePayment(payment: DebtPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebts(debts: List<DebtEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayments(payments: List<DebtPaymentEntity>)

    @Query("DELETE FROM debt_payments")
    suspend fun clearPayments()

    @Query("DELETE FROM debts")
    suspend fun clearDebts()
}
