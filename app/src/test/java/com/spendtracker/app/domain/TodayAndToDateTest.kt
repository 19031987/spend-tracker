package com.spendtracker.app.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** D26 (fresh "today") and A6 (like-for-like previous window). */
class TodayAndToDateTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `next date check waits until just after midnight`() {
        val now = ZonedDateTime.of(2026, 11, 17, 23, 59, 30, 0, utc)
        assertEquals(30_050L, millisUntilNextDateCheck(now))
    }

    @Test
    fun `next date check is capped so sleep cannot leave the date stale for long`() {
        val now = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, utc)
        assertEquals(TODAY_MAX_CHECK_INTERVAL_MS, millisUntilNextDateCheck(now))
    }

    @Test
    fun `todayFlow emits the clock's date immediately`() {
        val clock = Clock.fixed(ZonedDateTime.of(2026, 3, 1, 0, 0, 1, 0, utc).toInstant(), utc)
        assertEquals(LocalDate.of(2026, 3, 1), runBlocking { todayFlow(clock).first() })
    }

    @Test
    fun `previous month to date covers the same days on the 3rd`() {
        val today = LocalDate.of(2026, 3, 3)
        val current = PeriodCalculator.current(Period.MONTH, today, DayOfWeek.MONDAY)
        val prev = PeriodCalculator.previousToDate(current, today)
        assertEquals(LocalDate.of(2026, 2, 1), prev.startDate)
        assertEquals(LocalDate.of(2026, 2, 4), prev.endExclusive)
        val cur = PeriodCalculator.currentToDate(current, today)
        assertEquals(LocalDate.of(2026, 3, 4), cur.endExclusive)
    }

    @Test
    fun `previous month to date is clamped to a shorter previous month`() {
        val today = LocalDate.of(2026, 3, 30)
        val current = PeriodCalculator.current(Period.MONTH, today, DayOfWeek.MONDAY)
        val prev = PeriodCalculator.previousToDate(current, today)
        assertEquals(LocalDate.of(2026, 3, 1), prev.endExclusive) // all of February
    }

    @Test
    fun `previous week to date uses the same weekdays`() {
        val wednesday = LocalDate.of(2026, 11, 4)
        val current = PeriodCalculator.current(Period.WEEK, wednesday, DayOfWeek.MONDAY)
        val prev = PeriodCalculator.previousToDate(current, wednesday)
        assertEquals(LocalDate.of(2026, 10, 26), prev.startDate)
        assertEquals(LocalDate.of(2026, 10, 29), prev.endExclusive) // Mon..Wed
    }

    @Test
    fun `previous year to date matches the day of year`() {
        val today = LocalDate.of(2026, 2, 10)
        val current = PeriodCalculator.current(Period.YEAR, today, DayOfWeek.MONDAY)
        val prev = PeriodCalculator.previousToDate(current, today)
        assertEquals(LocalDate.of(2025, 1, 1), prev.startDate)
        assertEquals(LocalDate.of(2025, 2, 11), prev.endExclusive)
    }
}
