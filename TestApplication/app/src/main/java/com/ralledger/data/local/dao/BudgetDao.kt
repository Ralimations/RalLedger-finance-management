package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC, categoryId ASC")
    fun getAllBudgetsFlow(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC, categoryId ASC")
    suspend fun getAllBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId ORDER BY year, month")
    suspend fun getBudgetsForCategory(categoryId: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND year = :year AND month = :month LIMIT 1")
    suspend fun getBudgetForCategoryPeriod(categoryId: String, year: Int, month: Int): BudgetEntity?

    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId = :categoryId")
    suspend fun countCategoryReferences(categoryId: String): Long

    @Query("UPDATE budgets SET categoryId = :targetCategoryId WHERE categoryId = :sourceCategoryId")
    suspend fun reassignCategory(sourceCategoryId: String, targetCategoryId: String)

    @Query("UPDATE budgets SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgetsForRestore(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()
}
