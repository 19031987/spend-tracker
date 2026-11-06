package com.spendtracker.app.domain.goals

import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GoalCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun goal(
        target: Long = 100_000L,
        starting: Long = 0L,
        targetDate: LocalDate? = null,
        id: Long = 1L,
        createdAt: Long = 0L,
        archived: Boolean = false
    ) = GoalEntity(
        id = id,
        name = "Holiday",
        emoji = "X",
        colorHex = "#A5B4FC",
        targetMinor = target,
        targetDate = targetDate?.let { millis(it) },
        startingMinor = starting,
        createdAt = createdAt,
        archived = archived
    )

    private fun contribution(amount: Long, date: LocalDate, goalId: Long = 1L) =
        GoalContributionEntity(goalId = goalId, amountMinor = amount, timestamp = millis(date), note = null)

    private fun calc(g: GoalEntity, c: List<GoalContributionEntity> = emptyList()) =
        GoalCalculator.progress(g, c, today, zone)

    @Test
    fun `saved is starting plus contributions and remaining and fraction follow`() {
        val p = calc(goal(starting = 10_000L), listOf(contribution(15_000L, today)))
        assertEquals(25_000L, p.savedMinor)
        assertEquals(75_000L, p.remainingMinor)
        assertEquals(0.25f, p.fraction, 0.0001f)
    }

    @Test
    fun `withdrawals reduce saved`() {
        val p = calc(goal(), listOf(contribution(30_000L, today), contribution(-10_000L, today)))
        assertEquals(20_000L, p.savedMinor)
    }

    @Test
    fun `saved is clamped at zero`() {
        val p = calc(goal(starting = 1_000L), listOf(contribution(-5_000L, today)))
        assertEquals(0L, p.savedMinor)
        assertEquals(0f, p.fraction, 0.0001f)
        assertEquals(100_000L, p.remainingMinor)
    }

    @Test
    fun `fraction and remaining clamp when over target`() {
        val p = calc(goal(starting = 150_000L))
        assertEquals(1f, p.fraction, 0.0001f)
        assertEquals(0L, p.remainingMinor)
        assertEquals(GoalStatus.COMPLETE, p.status)
    }

    @Test
    fun `contributions of other goals are ignored`() {
        val p = calc(goal(), listOf(contribution(5_000L, today, goalId = 2L)))
        assertEquals(0L, p.savedMinor)
    }

    @Test
    fun `monthly needed is null without target date`() {
        assertNull(calc(goal()).monthlyNeededMinor)
    }

    @Test
    fun `monthly needed divides remaining by months left rounding up`() {
        val p = calc(goal(target = 100_000L, targetDate = LocalDate.of(2026, 12, 15)))
        // 6 months left: 100000 / 6 = 16666.67 -> 16667
        assertEquals(16_667L, p.monthlyNeededMinor)
    }

    @Test
    fun `partial month counts as a full month`() {
        val p = calc(goal(target = 30_000L, targetDate = LocalDate.of(2026, 8, 20)))
        // 2 months and 5 days -> 3 months
        assertEquals(10_000L, p.monthlyNeededMinor)
    }

    @Test
    fun `past target date uses a single month`() {
        val p = calc(goal(target = 50_000L, targetDate = LocalDate.of(2026, 1, 1)))
        assertEquals(50_000L, p.monthlyNeededMinor)
        assertEquals(GoalStatus.BEHIND, p.status)
    }

    @Test
    fun `average uses last three months divided by three`() {
        val p = calc(
            goal(),
            listOf(
                contribution(9_000L, today.minusMonths(1)),
                contribution(6_000L, today.minusDays(5)),
                contribution(50_000L, today.minusMonths(4))
            )
        )
        assertEquals(5_000L, p.averageMonthlyContributionMinor)
    }

    @Test
    fun `projection is today plus remaining over average months`() {
        val p = calc(goal(target = 100_000L), listOf(contribution(30_000L, today.minusDays(10))))
        // avg 10000, remaining 70000 -> 7 months
        assertEquals(LocalDate.of(2027, 1, 15), p.projectedCompletion)
    }

    @Test
    fun `no projection without recent contributions`() {
        assertNull(calc(goal()).projectedCompletion)
    }

    @Test
    fun `status no target date`() {
        assertEquals(GoalStatus.NO_TARGET_DATE, calc(goal()).status)
    }

    @Test
    fun `status on track when average covers monthly needed`() {
        val p = calc(
            goal(target = 100_000L, targetDate = LocalDate.of(2026, 12, 15)),
            listOf(contribution(60_000L, today.minusDays(1)))
        )
        assertEquals(GoalStatus.ON_TRACK, p.status)
    }

    @Test
    fun `status behind when average is too low`() {
        val p = calc(
            goal(target = 100_000L, targetDate = LocalDate.of(2026, 12, 15)),
            listOf(contribution(3_000L, today.minusDays(1)))
        )
        assertEquals(GoalStatus.BEHIND, p.status)
    }

    @Test
    fun `status behind with no contributions and a future date`() {
        val p = calc(goal(targetDate = LocalDate.of(2026, 12, 15)))
        assertEquals(GoalStatus.BEHIND, p.status)
    }

    @Test
    fun `status complete wins over target date`() {
        val p = calc(goal(starting = 100_000L, targetDate = LocalDate.of(2026, 1, 1)))
        assertEquals(GoalStatus.COMPLETE, p.status)
        assertEquals(0L, p.monthlyNeededMinor)
    }

    @Test
    fun `summary excludes archived and puts complete goals last`() {
        val goals = listOf(
            goal(id = 1L, createdAt = 1L, starting = 100_000L),
            goal(id = 2L, createdAt = 2L),
            goal(id = 3L, createdAt = 3L, archived = true),
            goal(id = 4L, createdAt = 0L)
        )
        val result = GoalCalculator.summary(goals, emptyList(), today, zone)
        assertEquals(listOf(4L, 2L, 1L), result.map { it.goalId })
    }
}
