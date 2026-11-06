package com.spendtracker.app.domain.goals

import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class GoalStatus { COMPLETE, ON_TRACK, BEHIND, NO_TARGET_DATE }

data class GoalProgress(
    val goalId: Long,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val remainingMinor: Long,
    val fraction: Float,
    val targetDate: LocalDate?,
    val monthlyNeededMinor: Long?,
    val averageMonthlyContributionMinor: Long,
    val projectedCompletion: LocalDate?,
    val status: GoalStatus
)

object GoalCalculator {

    private const val AVERAGE_WINDOW_MONTHS: Long = 3L

    fun progress(
        goal: GoalEntity,
        contributions: List<GoalContributionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): GoalProgress {
        val mine: List<GoalContributionEntity> = contributions.filter { it.goalId == goal.id }
        val saved: Long = maxOf(0L, goal.startingMinor + mine.sumOf { it.amountMinor })
        val remaining: Long = maxOf(0L, goal.targetMinor - saved)
        val fraction: Float = when {
            goal.targetMinor <= 0L -> if (saved >= goal.targetMinor) 1f else 0f
            else -> (saved.toDouble() / goal.targetMinor.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        val targetDate: LocalDate? = goal.targetDate?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }

        val windowStart: Long = today.minusMonths(AVERAGE_WINDOW_MONTHS)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd: Long = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowSum: Long = mine
            .filter { it.timestamp in windowStart until windowEnd }
            .sumOf { it.amountMinor }
        val average: Long = maxOf(0L, windowSum / AVERAGE_WINDOW_MONTHS)

        val complete: Boolean = saved >= goal.targetMinor

        val monthlyNeeded: Long? = targetDate?.let {
            if (complete) 0L else ceilDiv(remaining, monthsLeft(today, it))
        }

        val projected: LocalDate? = if (!complete && average > 0L) {
            today.plusMonths(ceilDiv(remaining, average))
        } else {
            null
        }

        val status: GoalStatus = when {
            complete -> GoalStatus.COMPLETE
            targetDate == null -> GoalStatus.NO_TARGET_DATE
            average >= (monthlyNeeded ?: 0L) && average > 0L -> GoalStatus.ON_TRACK
            projected != null && !projected.isAfter(targetDate) -> GoalStatus.ON_TRACK
            else -> GoalStatus.BEHIND
        }

        return GoalProgress(
            goalId = goal.id,
            name = goal.name,
            emoji = goal.emoji,
            colorHex = goal.colorHex,
            targetMinor = goal.targetMinor,
            savedMinor = saved,
            remainingMinor = remaining,
            fraction = fraction,
            targetDate = targetDate,
            monthlyNeededMinor = monthlyNeeded,
            averageMonthlyContributionMinor = average,
            projectedCompletion = projected,
            status = status
        )
    }

    /** Non-archived goals: incomplete first, then complete; each group ordered by createdAt. */
    fun summary(
        goals: List<GoalEntity>,
        contributions: List<GoalContributionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): List<GoalProgress> {
        val byGoal: Map<Long, List<GoalContributionEntity>> = contributions.groupBy { it.goalId }
        return goals
            .filter { !it.archived }
            .sortedBy { it.createdAt }
            .map { goal ->
                progress(goal, byGoal[goal.id].orEmpty(), today, zone)
            }
            .sortedBy { it.status == GoalStatus.COMPLETE }
    }

    private fun monthsLeft(today: LocalDate, target: LocalDate): Long {
        if (!target.isAfter(today)) return 1L
        var months: Long = ChronoUnit.MONTHS.between(today, target)
        if (today.plusMonths(months).isBefore(target)) months += 1L
        return maxOf(1L, months)
    }

    private fun ceilDiv(a: Long, b: Long): Long {
        if (b <= 0L) return a
        return (a + b - 1L) / b
    }
}
