package com.spendtracker.app.domain.dashboard

import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthSummaryCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val november: YearMonth = YearMonth.of(2026, 11)
    private var nextId: Long = 1L

    private fun ts(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

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
        timestamp = ts(date.year, date.monthValue, date.dayOfMonth),
        excludeFromSpending = exclude
    )

    @Test
    fun `summarize splits spend income and carried over`() {
        val items = listOf(
            tx(TransactionType.INCOME, 300_000, LocalDate.of(2026, 10, 1)),
            tx(TransactionType.EXPENSE, 120_000, LocalDate.of(2026, 10, 15)),
            tx(TransactionType.INCOME, 250_000, LocalDate.of(2026, 11, 1)),
            tx(TransactionType.EXPENSE, 40_000, LocalDate.of(2026, 11, 3)),
            tx(TransactionType.EXPENSE, 10_000, LocalDate.of(2026, 11, 30))
        )
        val summary = MonthSummaryCalculator.summarize(items, november, zone)
        assertEquals(50_000L, summary.spentMinor)
        assertEquals(250_000L, summary.incomeMinor)
        assertEquals(180_000L, summary.carriedOverMinor)
        assertEquals(380_000L, summary.netRemainingMinor)
    }

    @Test
    fun `summarize ignores excluded transfers and later months`() {
        val items = listOf(
            tx(TransactionType.EXPENSE, 5_000, LocalDate.of(2026, 11, 2), exclude = true),
            tx(TransactionType.EXPENSE, 7_000, LocalDate.of(2026, 11, 2), categoryKey = "INTERNAL_TRANSFER"),
            tx(TransactionType.TRANSFER, 9_000, LocalDate.of(2026, 11, 2), categoryKey = null),
            tx(TransactionType.INCOME, 8_000, LocalDate.of(2026, 11, 2), exclude = true),
            tx(TransactionType.EXPENSE, 3_000, LocalDate.of(2026, 12, 1))
        )
        val summary = MonthSummaryCalculator.summarize(items, november, zone)
        assertEquals(0L, summary.spentMinor)
        assertEquals(0L, summary.incomeMinor)
        assertEquals(0L, summary.carriedOverMinor)
    }

    @Test
    fun `carried over can be negative`() {
        val items = listOf(
            tx(TransactionType.EXPENSE, 20_000, LocalDate.of(2026, 9, 10)),
            tx(TransactionType.INCOME, 5_000, LocalDate.of(2026, 9, 11))
        )
        val summary = MonthSummaryCalculator.summarize(items, november, zone)
        assertEquals(-15_000L, summary.carriedOverMinor)
        assertEquals(-15_000L, summary.netRemainingMinor)
    }

    @Test
    fun `month boundaries are inclusive of the first and last day`() {
        val items = listOf(
            tx(TransactionType.EXPENSE, 100, LocalDate.of(2026, 11, 1)),
            tx(TransactionType.EXPENSE, 200, LocalDate.of(2026, 11, 30)),
            tx(TransactionType.EXPENSE, 400, LocalDate.of(2026, 10, 31))
        )
        val summary = MonthSummaryCalculator.summarize(items, november, zone)
        assertEquals(300L, summary.spentMinor)
        assertEquals(-400L, summary.carriedOverMinor)
    }

    @Test
    fun `topCategories sorts descending with shares and respects limit`() {
        val items = listOf(
            tx(TransactionType.EXPENSE, 6_000, LocalDate.of(2026, 11, 2), "RENT", "Rent"),
            tx(TransactionType.EXPENSE, 3_000, LocalDate.of(2026, 11, 3), "GROCERIES", "Groceries"),
            tx(TransactionType.EXPENSE, 1_000, LocalDate.of(2026, 11, 4), "GROCERIES", "Groceries"),
            tx(TransactionType.EXPENSE, 500, LocalDate.of(2026, 11, 5), "FUN", "Fun"),
            tx(TransactionType.EXPENSE, 9_999, LocalDate.of(2026, 10, 5), "RENT", "Rent"),
            tx(TransactionType.INCOME, 9_999, LocalDate.of(2026, 11, 5), "SALARY", "Salary")
        )
        val top = MonthSummaryCalculator.topCategories(items, november, zone, limit = 2)
        assertEquals(listOf("RENT", "GROCERIES"), top.map { it.categoryKey })
        assertEquals(6_000L, top[0].amountMinor)
        assertEquals(4_000L, top[1].amountMinor)
        assertEquals(6_000f / 10_500f, top[0].share, 0.0001f)
    }

    @Test
    fun `topCategories is empty without spending and null key falls back`() {
        assertTrue(MonthSummaryCalculator.topCategories(emptyList(), november, zone).isEmpty())
        val items = listOf(tx(TransactionType.EXPENSE, 100, LocalDate.of(2026, 11, 2), categoryKey = null, name = "Other"))
        val top = MonthSummaryCalculator.topCategories(items, november, zone)
        assertEquals("OTHER_EXPENSE", top.single().categoryKey)
        assertEquals(1f, top.single().share, 0.0001f)
    }
}
