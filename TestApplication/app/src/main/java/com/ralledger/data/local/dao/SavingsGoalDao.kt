package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.SavingsGoalEntity
import com.ralledger.data.local.entity.SavingsGoalEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY isArchived ASC, targetDate ASC, createdAt DESC")
    fun getGoalsFlow(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    suspend fun getAllGoals(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoalById(id: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goal_events ORDER BY date DESC, id ASC")
    fun getEventsFlow(): Flow<List<SavingsGoalEventEntity>>

    @Query("SELECT * FROM savings_goal_events ORDER BY id ASC")
    suspend fun getAllEvents(): List<SavingsGoalEventEntity>

    @Query("SELECT COUNT(*) FROM savings_goal_events WHERE goalId = :goalId")
    suspend fun countEvents(goalId: String): Long

    @Query("SELECT COALESCE(SUM(allocatedAmountCentavos), 0) FROM savings_goals")
    suspend fun getTotalAllocatedCentavos(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: SavingsGoalEntity)

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: SavingsGoalEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoals(goals: List<SavingsGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvents(events: List<SavingsGoalEventEntity>)

    @Query("DELETE FROM savings_goal_events")
    suspend fun clearEvents()

    @Query("DELETE FROM savings_goals")
    suspend fun clearGoals()

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: String)
}
