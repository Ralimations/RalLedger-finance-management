package com.ralledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.SavingsGoal
import com.ralledger.domain.model.SavingsGoalEvent
import com.ralledger.domain.model.SavingsGoalEventType

@Entity(tableName = "savings_goals", indices = [Index("targetDate"), Index("isArchived")])
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmountCentavos: Long,
    val targetDate: Long?,
    val notes: String,
    val allocatedAmountCentavos: Long,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "savings_goal_events",
    foreignKeys = [ForeignKey(
        entity = SavingsGoalEntity::class,
        parentColumns = ["id"],
        childColumns = ["goalId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("goalId"), Index("date")]
)
data class SavingsGoalEventEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val type: String,
    val amountCentavos: Long,
    val date: Long,
    val note: String
)

fun SavingsGoalEntity.toDomainModel(events: List<SavingsGoalEvent>) = SavingsGoal(
    id = id,
    name = name,
    targetAmount = Money(targetAmountCentavos),
    targetDate = targetDate,
    notes = notes,
    allocatedAmount = Money(allocatedAmountCentavos),
    isArchived = isArchived,
    createdAt = createdAt,
    events = events
)

fun SavingsGoalEventEntity.toDomainModel() = SavingsGoalEvent(
    id = id,
    goalId = goalId,
    type = SavingsGoalEventType.valueOf(type),
    amount = Money(amountCentavos),
    date = date,
    note = note
)
