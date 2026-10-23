package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.ExpectedIncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpectedIncomeDao {
    @Query("SELECT * FROM expected_income ORDER BY expectedDate ASC, createdAt DESC")
    fun getAllExpectedIncomeFlow(): Flow<List<ExpectedIncomeEntity>>

    @Query("SELECT * FROM expected_income ORDER BY id ASC")
    suspend fun getAllExpectedIncome(): List<ExpectedIncomeEntity>

    @Query("SELECT * FROM expected_income WHERE id = :id")
    suspend fun getById(id: String): ExpectedIncomeEntity?

    @Query("SELECT COUNT(*) FROM expected_income WHERE incomeCategoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("SELECT COUNT(*) FROM expected_income WHERE counterpartyId = :counterpartyId")
    suspend fun countCounterpartyReferences(counterpartyId: String): Long

    @Query("UPDATE expected_income SET incomeCategoryId = :targetId WHERE incomeCategoryId = :sourceId")
    suspend fun reassignCategory(sourceId: String, targetId: String)

    @Query("UPDATE expected_income SET counterpartyId = :targetId WHERE counterpartyId = :sourceId")
    suspend fun reassignCounterparty(sourceId: String, targetId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ExpectedIncomeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ExpectedIncomeEntity>)

    @Update
    suspend fun update(item: ExpectedIncomeEntity)

    @Query("DELETE FROM expected_income WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM expected_income")
    suspend fun clear()
}
