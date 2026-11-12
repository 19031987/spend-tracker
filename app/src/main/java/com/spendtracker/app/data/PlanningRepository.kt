package com.spendtracker.app.data

import kotlinx.coroutines.flow.Flow

class PlanningRepository(private val dao: PlanningDao) {

    // Budgets
    fun observeBudgets(): Flow<List<BudgetEntity>> = dao.observeBudgets()

    suspend fun setBudget(categoryKey: String, amountMinor: Long, rollover: Boolean) {
        dao.upsertBudget(
            BudgetEntity(
                categoryKey = categoryKey,
                amountMinor = amountMinor,
                rollover = rollover,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeBudget(categoryKey: String) {
        dao.deleteBudget(categoryKey)
    }

    // Recurring
    fun observeRecurring(): Flow<List<RecurringItemEntity>> = dao.observeRecurring()

    suspend fun saveRecurring(entity: RecurringItemEntity): Long = dao.upsertRecurring(entity)

    suspend fun updateRecurring(entity: RecurringItemEntity) {
        dao.updateRecurring(entity)
    }

    suspend fun setRecurringStatus(id: Long, status: String) {
        dao.setRecurringStatus(id, status)
    }

    suspend fun deleteRecurring(id: Long) {
        dao.deleteRecurring(id)
    }

    // Goals
    fun observeGoals(): Flow<List<GoalEntity>> = dao.observeGoals()

    suspend fun createGoal(
        name: String,
        emoji: String,
        colorHex: String,
        targetMinor: Long,
        targetDate: Long?,
        startingMinor: Long
    ): Long = dao.insertGoal(
        GoalEntity(
            name = name,
            emoji = emoji,
            colorHex = colorHex,
            targetMinor = targetMinor,
            targetDate = targetDate,
            startingMinor = startingMinor,
            createdAt = System.currentTimeMillis(),
            archived = false
        )
    )

    suspend fun updateGoal(goal: GoalEntity) {
        dao.updateGoal(goal)
    }

    suspend fun deleteGoal(id: Long) {
        dao.deleteGoal(id)
    }

    fun observeContributions(): Flow<List<GoalContributionEntity>> = dao.observeContributions()

    suspend fun addContribution(goalId: Long, amountMinor: Long, note: String?): Long =
        dao.insertContribution(
            GoalContributionEntity(
                goalId = goalId,
                amountMinor = amountMinor,
                timestamp = System.currentTimeMillis(),
                note = note
            )
        )

    suspend fun deleteContribution(id: Long) {
        dao.deleteContribution(id)
    }
}
