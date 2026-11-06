package com.spendtracker.app.domain

import com.spendtracker.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthMathTest {

    private val zone = ZoneId.of("UTC")

    private fun item(
        type: TransactionType = TransactionType.EXPENSE,
        categoryKey: String? = "GROCERIES",
        excludeFromSpending: Boolean = false
    ) = TransactionItem(
        id = 1L,
        type = type,
        amountMinor = 1000L,
        accountId = 1L,
        accountName = "Chase",
        categoryKey = categoryKey,
        excludeFromSpending = excludeFromSpending
    )

    @Test
    fun `monthRange spans first to next first`() {
        val range = monthRange(YearMonth.of(2026, 2), zone)
        assertEquals(LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(), range.start)
        assertEquals(LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli(), range.end)
    }

    @Test
    fun `monthRange rolls over the year end`() {
        val range = monthRange(YearMonth.of(2026, 12), zone)
        assertEquals(LocalDate.of(2027, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(), range.end)
    }

    @Test
    fun `monthOf maps boundaries to the right month`() {
        val range = monthRange(YearMonth.of(2026, 5), zone)
        assertEquals(YearMonth.of(2026, 5), monthOf(range.start, zone))
        assertEquals(YearMonth.of(2026, 5), monthOf(range.end - 1, zone))
        assertEquals(YearMonth.of(2026, 6), monthOf(range.end, zone))
    }

    @Test
    fun `monthProgress is zero before the month`() {
        assertEquals(0f, monthProgress(YearMonth.of(2026, 5), LocalDate.of(2026, 4, 30)), 0.0001f)
    }

    @Test
    fun `monthProgress is one after the month`() {
        assertEquals(1f, monthProgress(YearMonth.of(2026, 5), LocalDate.of(2026, 6, 1)), 0.0001f)
    }

    @Test
    fun `monthProgress inside the month uses the middle of the day`() {
        // 30-day month, 15th day: (14 + 0.5) / 30
        assertEquals(14.5f / 30f, monthProgress(YearMonth.of(2026, 4), LocalDate.of(2026, 4, 15)), 0.0001f)
        assertEquals(0.5f / 30f, monthProgress(YearMonth.of(2026, 4), LocalDate.of(2026, 4, 1)), 0.0001f)
    }

    @Test
    fun `isSpending is true for a normal expense`() {
        assertTrue(item().isSpending())
        assertFalse(item().isIncome())
    }

    @Test
    fun `isSpending is false when excluded`() {
        assertFalse(item(excludeFromSpending = true).isSpending())
    }

    @Test
    fun `isSpending is false for internal transfer category`() {
        assertFalse(item(categoryKey = "INTERNAL_TRANSFER").isSpending())
    }

    @Test
    fun `isSpending is false for income and transfer types`() {
        assertFalse(item(type = TransactionType.INCOME).isSpending())
        assertFalse(item(type = TransactionType.TRANSFER).isSpending())
    }

    @Test
    fun `isIncome is true for income and false when excluded`() {
        assertTrue(item(type = TransactionType.INCOME, categoryKey = "SALARY").isIncome())
        assertFalse(item(type = TransactionType.INCOME, excludeFromSpending = true).isIncome())
        assertFalse(item(type = TransactionType.EXPENSE).isIncome())
    }
}
