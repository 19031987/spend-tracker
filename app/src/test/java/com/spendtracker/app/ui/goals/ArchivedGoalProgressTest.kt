package com.spendtracker.app.ui.goals

import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ArchivedGoalProgressTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun goal(id: Long, createdAt: Long, archived: Boolean, starting: Long = 0L) = GoalEntity(
        id = id,
        name = "Goal $id",
        emoji = "X",
        colorHex = "#A5B4FC",
        targetMinor = 100_000L,
        targetDate = null,
        startingMinor = starting,
        createdAt = createdAt,
        archived = archived
    )

    @Test
    fun `only archived goals are returned, newest first`() {
        val goals = listOf(
            goal(id = 1L, createdAt = 10L, archived = true),
            goal(id = 2L, createdAt = 20L, archived = false),
            goal(id = 3L, createdAt = 30L, archived = true)
        )
        val result = archivedGoalProgress(goals, emptyList(), today, zone)
        assertEquals(listOf(3L, 1L), result.map { it.goalId })
    }

    @Test
    fun `archived goal progress includes its own contributions only`() {
        val goals = listOf(goal(id = 1L, createdAt = 0L, archived = true, starting = 5_000L))
        val contributions = listOf(
            GoalContributionEntity(goalId = 1L, amountMinor = 10_000L, timestamp = 0L, note = null),
            GoalContributionEntity(goalId = 2L, amountMinor = 99_000L, timestamp = 0L, note = null)
        )
        val result = archivedGoalProgress(goals, contributions, today, zone)
        assertEquals(15_000L, result.single().savedMinor)
    }

    @Test
    fun `no archived goals gives an empty list`() {
        val result = archivedGoalProgress(listOf(goal(1L, 0L, archived = false)), emptyList(), today, zone)
        assertTrue(result.isEmpty())
    }
}
