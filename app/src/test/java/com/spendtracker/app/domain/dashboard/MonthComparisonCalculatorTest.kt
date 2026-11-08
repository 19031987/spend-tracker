package com.spendtracker.app.domain.dashboard

import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthComparisonCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val november: YearMonth = YearMonth.of(2026, 11)
    private var nextId: Long = 1L

    private fun tx(
        type: TransactionType,
        amount: Long,
        date: LocalDate,
        categoryKey: String? = "GROCERIES",
        name: String = "Groceries",
        exclude: Boolean = false
    ): TransactionItem = TransactionItem(
        id = nextId++,
        type = type,
        amountMinor = amount,
        accountId = 1L,
        accountName = "Chase",
        categoryKey = categoryKey,
        categoryName = name,
        categoryEmoji = "🛒",
        categoryColorHex = "#112233",
        timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L,
        excludeFromSpending = exclude
    )

    private fun spend(amount: Long, date: LocalDate, key: String = "GROCERIES", name: String = "Groceries") =
        tx(TransactionType.EXPENSE, amount, date, key, name)

    @Test
    fun `compares month to date with the same days of last month`() {
        val items = listOf(
            spend(10_000, LocalDate.of(2026, 11, 3)),
            spend(6_000, LocalDate.of(2026, 10, 2)),
            // After day 6 of October, so outside the like-for-like window.
            spend(50_000, LocalDate.of(2026, 10, 20))
        )
        val result = MonthComparisonCalculator.compare(items, november, LocalDate.of(2026, 11, 6), zone)
        assertEquals(6, result.dayOfMonth)
        assertEquals(10_000L, result.spentMinor)
        assertEquals(6_000L, result.previousSpentMinor)
        assertEquals(4_000L, result.spentDeltaMinor)
        assertTrue(result.hasPrevious)
    }

    @Test
    fun `splits categories into increased and decreased ordered by size of change`() {
        val items = listOf(
            spend(30_000, LocalDate.of(2026, 11, 2), "DINING", "Dining"),
            spend(10_000, LocalDate.of(2026, 10, 2), "DINING", "Dining"),
            spend(5_000, LocalDate.of(2026, 11, 2), "TRANSPORT", "Transport"),
            spend(2_000, LocalDate.of(2026, 10, 2), "TRANSPORT", "Transport"),
            spend(1_000, LocalDate.of(2026, 11, 2), "SHOPPING", "Shopping"),
            spend(9_000, LocalDate.of(2026, 10, 2), "SHOPPING", "Shopping"),
            spend(4_000, LocalDate.of(2026, 10, 2), "HEALTH", "Health")
        )
        val result = MonthComparisonCalculator.compare(items, november, LocalDate.of(2026, 11, 6), zone)
        assertEquals(listOf("DINING", "TRANSPORT"), result.increased.map { it.categoryKey })
        assertEquals(listOf("SHOPPING", "HEALTH"), result.decreased.map { it.categoryKey })
        assertEquals(20_000L, result.increased.first().deltaMinor)
        assertEquals(2f, result.increased.first().changeFraction!!, 0.0001f)
        assertEquals(0L, result.decreased.last().currentMinor)
    }

    @Test
    fun `new category has no percentage change`() {
        val items = listOf(spend(5_000, LocalDate.of(2026, 11, 1), "TRAVEL", "Travel"))
        val result = MonthComparisonCalculator.compare(items, november, LocalDate.of(2026, 11, 6), zone)
        assertNull(result.increased.single().changeFraction)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `changes under one pound are ignored`() {
        val items = listOf(
            spend(1_050, LocalDate.of(2026, 11, 1)),
            spend(1_000, LocalDate.of(2026, 10, 1))
        )
        val result = MonthComparisonCalculator.compare(items, november, LocalDate.of(2026, 11, 6), zone)
        assertTrue(result.increased.isEmpty())
        assertTrue(result.decreased.isEmpty())
    }

    @Test
    fun `income and savings are compared and excluded rows ignored`() {
        val items = listOf(
            tx(TransactionType.INCOME, 200_000, LocalDate.of(2026, 11, 1)),
            tx(TransactionType.INCOME, 150_000, LocalDate.of(2026, 10, 1)),
            spend(50_000, LocalDate.of(2026, 11, 2)),
            spend(70_000, LocalDate.of(2026, 10, 2)),
            tx(TransactionType.EXPENSE, 99_000, LocalDate.of(2026, 11, 2), exclude = true),
            tx(TransactionType.EXPENSE, 99_000, LocalDate.of(2026, 11, 2), "INTERNAL_TRANSFER", "Transfer")
        )
        val result = MonthComparisonCalculator.compare(items, november, LocalDate.of(2026, 11, 6), zone)
        assertEquals(50_000L, result.incomeDeltaMinor)
        assertEquals(150_000L, result.savedMinor)
        assertEquals(80_000L, result.previousSavedMinor)
        assertEquals(70_000L, result.savedDeltaMinor)
        assertEquals(-20_000L, result.spentDeltaMinor)
    }

    @Test
    fun `day window is clamped to the shorter previous month`() {
        val march: YearMonth = YearMonth.of(2026, 3)
        val items = listOf(
            spend(1_000, LocalDate.of(2026, 3, 30)),
            spend(2_000, LocalDate.of(2026, 2, 28))
        )
        val result = MonthComparisonCalculator.compare(items, march, LocalDate.of(2026, 3, 30), zone)
        assertEquals(2_000L, result.previousSpentMinor)
    }

    @Test
    fun `a finished month is compared with the whole previous month`() {
        val february: YearMonth = YearMonth.of(2026, 2)
        val items = listOf(
            spend(1_000, LocalDate.of(2026, 2, 10)),
            spend(3_000, LocalDate.of(2026, 1, 31))
        )
        val result = MonthComparisonCalculator.compare(items, february, LocalDate.of(2026, 3, 5), zone)
        assertEquals(28, result.dayOfMonth)
        assertEquals(3_000L, result.previousSpentMinor)
    }
}
