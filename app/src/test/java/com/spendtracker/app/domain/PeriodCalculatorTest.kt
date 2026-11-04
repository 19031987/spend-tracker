package com.spendtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class PeriodCalculatorTest {

    private val utc = ZoneId.of("UTC")

    // current()

    @Test
    fun `current WEEK window starts on configured first day and spans 7 days`() {
        // 2026-11-04 is a Wednesday
        val today = LocalDate.of(2026, 11, 4)

        val mondayStart = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 2), mondayStart.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), mondayStart.endExclusive)

        val sundayStart = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayStart.startDate)
        assertEquals(LocalDate.of(2026, 11, 8), sundayStart.endExclusive)
    }

    @Test
    fun `current WEEK includes first day itself when today is the first day`() {
        val monday = LocalDate.of(2026, 11, 2)
        val window = PeriodCalculator.current(Period.WEEK, monday, DayOfWeek.MONDAY)
        assertEquals(monday, window.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), window.endExclusive)
    }

    @Test
    fun `current MONTH window spans calendar month`() {
        val today = LocalDate.of(2026, 11, 15)
        val window = PeriodCalculator.current(Period.MONTH, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 1), window.startDate)
        assertEquals(LocalDate.of(2026, 12, 1), window.endExclusive)
    }

    @Test
    fun `current YEAR window spans calendar year`() {
        val today = LocalDate.of(2026, 7, 4)
        val window = PeriodCalculator.current(Period.YEAR, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 1, 1), window.startDate)
        assertEquals(LocalDate.of(2027, 1, 1), window.endExclusive)
    }

    // previous()

    @Test
    fun `previous WEEK window is the 7 days before current window`() {
        val today = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.WEEK, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)
        assertEquals(LocalDate.of(2026, 10, 26), previous.startDate)
        assertEquals(current.startDate, previous.endExclusive)
    }

    @Test
    fun `previous MONTH window spans previous calendar month including leap February`() {
        val today = LocalDate.of(2024, 3, 10) // 2024 is a leap year
        val current = PeriodCalculator.current(Period.MONTH, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)
        assertEquals(LocalDate.of(2024, 2, 1), previous.startDate)
        assertEquals(LocalDate.of(2024, 3, 1), previous.endExclusive)
    }

    @Test
    fun `previous YEAR window spans previous calendar year`() {
        val today = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.YEAR, today, DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)
        assertEquals(LocalDate.of(2025, 1, 1), previous.startDate)
        assertEquals(LocalDate.of(2026, 1, 1), previous.endExclusive)
    }

    // toRange()

    @Test
    fun `toRange converts window boundaries to UTC epoch millis at start of day`() {
        val window = PeriodWindow(
            period = Period.WEEK,
            startDate = LocalDate.of(2026, 11, 2),
            endExclusive = LocalDate.of(2026, 11, 9)
        )
        val range = window.toRange(utc)
        assertEquals(1793577600000L, range.start) // 2026-11-02T00:00:00Z
        assertEquals(1793577600000L + 7L * 24 * 60 * 60 * 1000, range.end)
    }

    @Test
    fun `toRange end is exclusive so half-open ranges concatenate cleanly`() {
        val current = PeriodCalculator.current(Period.MONTH, LocalDate.of(2026, 11, 15), DayOfWeek.MONDAY)
        val previous = PeriodCalculator.previous(current)
        val curRange = current.toRange(utc)
        val prevRange = previous.toRange(utc)
        assertEquals(curRange.start, prevRange.end)
    }
}
