package com.spendtracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryKey: String,
    val amountMinor: Long,
    val rollover: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "recurring_items",
    indices = [Index(value = ["merchantKey"], unique = true)]
)
data class RecurringItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,
    val displayName: String,
    val categoryKey: String?,
    val amountMinor: Long,
    /** RecurringFrequency.name */
    val frequency: String,
    /** Epoch ms of a known occurrence. */
    val anchorDate: Long,
    /** "APPROVED" | "IGNORED" */
    val status: String,
    val isIncome: Boolean,
    val createdAt: Long
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val targetMinor: Long,
    val targetDate: Long?,
    val startingMinor: Long,
    val createdAt: Long,
    val archived: Boolean
)

@Entity(
    tableName = "goal_contributions",
    indices = [Index(value = ["goalId"])]
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** May be negative (withdrawal). */
    val amountMinor: Long,
    val timestamp: Long,
    val note: String?
)

@Dao
abstract class PlanningDao {

    @Query("SELECT * FROM budgets")
    abstract fun observeBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryKey = :categoryKey")
    abstract suspend fun deleteBudget(categoryKey: String)

    @Query("SELECT * FROM recurring_items ORDER BY id")
    abstract fun observeRecurring(): Flow<List<RecurringItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRecurring(item: RecurringItemEntity): Long

    @Update
    abstract suspend fun updateRecurring(item: RecurringItemEntity)

    @Query("UPDATE recurring_items SET status = :status WHERE id = :id")
    abstract suspend fun setRecurringStatus(id: Long, status: String)

    @Query("DELETE FROM recurring_items WHERE id = :id")
    abstract suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC, id DESC")
    abstract fun observeGoals(): Flow<List<GoalEntity>>

    @Insert
    abstract suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    abstract suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    abstract suspend fun deleteContributionsForGoal(goalId: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    abstract suspend fun deleteGoalRow(id: Long)

    @Transaction
    open suspend fun deleteGoal(id: Long) {
        deleteContributionsForGoal(id)
        deleteGoalRow(id)
    }

    @Query("SELECT * FROM goal_contributions ORDER BY timestamp DESC, id DESC")
    abstract fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Insert
    abstract suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    abstract suspend fun deleteContribution(id: Long)

    // Backup / restore
    @Query("SELECT * FROM budgets") abstract suspend fun getBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM recurring_items ORDER BY id") abstract suspend fun getRecurring(): List<RecurringItemEntity>
    @Query("SELECT * FROM goals ORDER BY id") abstract suspend fun getGoals(): List<GoalEntity>
    @Query("SELECT * FROM goal_contributions ORDER BY id") abstract suspend fun getContributions(): List<GoalContributionEntity>

    @Insert abstract suspend fun insertBudgets(budgets: List<BudgetEntity>)
    @Insert abstract suspend fun insertRecurringItems(items: List<RecurringItemEntity>)
    @Insert abstract suspend fun insertGoals(goals: List<GoalEntity>)
    @Insert abstract suspend fun insertContributions(contributions: List<GoalContributionEntity>)

    @Query("DELETE FROM budgets") abstract suspend fun clearBudgets()
    @Query("DELETE FROM recurring_items") abstract suspend fun clearRecurring()
    @Query("DELETE FROM goals") abstract suspend fun clearGoals()
    @Query("DELETE FROM goal_contributions") abstract suspend fun clearContributions()
}
