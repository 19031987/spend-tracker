package com.spendtracker.app.domain

import com.spendtracker.app.data.BucketTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class ChartBucketingTest {

    private val locale = Locale.US

    @Test
    fun `build for WEEK produces 7 day buckets labelled Mon through Sun`() {
        val today = LocalDate.of(2026, 11, 4) // Wednesday
        val current = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(
            current = current,
            previous = previous,
            currentRows = listOf(
                BucketTotal("2026-11-02", 1000L), // Mon
                BucketTotal("2026-11-03", 2500L), // Tue
                BucketTotal("2026-11-05", 700L)   // Thu
            ),
            previousRows = listOf(BucketTotal("2026-10-26", 4000L)), // Prev Mon
            locale = locale
        )

        assertEquals(7, bars.size)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), bars.map { it.label })
        assertEquals(1000L, bars[0].current)
        assertEquals(4000L, bars[0].previous)
        assertEquals(2500L, bars[1].current)
        assertEquals(0L, bars[1].previous)
        assertEquals(700L, bars[3].current)
    }

    @Test
    fun `build for WEEK accumulates multiple rows into the same day bucket`() {
        val today = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(
            current, previous,
            currentRows = listOf(
                BucketTotal("2026-11-02", 1000L),
                BucketTotal("2026-11-02", 500L),
                BucketTotal("2026-11-02", 250L)
            ),
            previousRows = emptyList(),
            locale = locale
        )

        assertEquals(1750L, bars[0].current)
    }

    @Test
    fun `build for WEEK ignores rows outside the window`() {
        val today = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(
            current, previous,
            currentRows = listOf(
                BucketTotal("2026-11-15", 9000L), // 13 days after week start -> out of range
                BucketTotal("2026-10-25", 8000L)  // before week start -> negative index
            ),
            previousRows = emptyList(),
            locale = locale
        )

        assertEquals(7, bars.size)
        assertEquals(0L, bars.sumOf { it.current })
    }

    @Test
    fun `build for MONTH buckets days into week-of-month slots with W labels`() {
        // November 2026 (30 days) vs October 2026 (31 days) -> longest 31 -> (31+6)/7 = 5 buckets
        val today = LocalDate.of(2026, 11, 15)
        val current = PeriodCalculator.current(Period.MONTH, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(
            current, previous,
            currentRows = listOf(
                BucketTotal("2026-11-03", 1000L), // Days 1-7   -> W1
                BucketTotal("2026-11-10", 2000L), // Days 8-14  -> W2
                BucketTotal("2026-11-29", 4000L)  // Days 29-31 -> W5
            ),
            previousRows = listOf(BucketTotal("2026-10-22", 3000L)), // Days 22-28 -> W4
            locale = locale
        )

        assertEquals(5, bars.size)
        assertEquals(listOf("W1", "W2", "W3", "W4", "W5"), bars.map { it.label })
        assertEquals(1000L, bars[0].current)
        assertEquals(2000L, bars[1].current)
        assertEquals(0L, bars[2].current)
        assertEquals(3000L, bars[3].previous)
        assertEquals(4000L, bars[4].current)
    }

    @Test
    fun `build for YEAR produces 12 month buckets labelled Jan through Dec`() {
        val today = LocalDate.of(2026, 6, 15)
        val current = PeriodCalculator.current(Period.YEAR, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(
            current, previous,
            currentRows = listOf(
                BucketTotal("2026-01", 1000L),
                BucketTotal("2026-03", 2000L),
                BucketTotal("2026-12", 500L)
            ),
            previousRows = listOf(BucketTotal("2025-03", 2500L)),
            locale = locale
        )

        assertEquals(12, bars.size)
        assertEquals("Jan", bars[0].label)
        assertEquals("Dec", bars[11].label)
        assertEquals(1000L, bars[0].current)
        assertEquals(2000L, bars[2].current)
        assertEquals(2500L, bars[2].previous)
        assertEquals(500L, bars[11].current)
    }

    @Test
    fun `build with empty rows returns zero-filled buckets`() {
        val today = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)

        val bars = ChartBucketing.build(current, previous, emptyList(), emptyList(), locale)

        assertEquals(7, bars.size)
        assertEquals(0L, bars.sumOf { it.current })
        assertEquals(0L, bars.sumOf { it.previous })
    }
}
