package com.spendtracker.app.ui

import com.spendtracker.app.data.TransactionCategory
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.AnalyticsSnapshot
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBar
import com.spendtracker.app.domain.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AnalyticsCalculationTest {

    @Test
    fun `calculateDaysElapsed and calculateTotalDaysInPeriod compute accurate horizons`() {
        // Monday Nov 2, 2026 (day 1 of week)
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(1L, AnalyticsViewModel.calculateDaysElapsed(Period.WEEK, monday, DayOfWeek.MONDAY))
        assertEquals(7L, AnalyticsViewModel.calculateTotalDaysInPeriod(Period.WEEK, monday))

        // Thursday Nov 5, 2026 (day 4 of week)
        val thursday = LocalDate.of(2026, 11, 5)
        assertEquals(4L, AnalyticsViewModel.calculateDaysElapsed(Period.WEEK, thursday, DayOfWeek.MONDAY))

        // Sunday Nov 8, 2026 (day 7 of week)
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(7L, AnalyticsViewModel.calculateDaysElapsed(Period.WEEK, sunday, DayOfWeek.MONDAY))

        // Month of November 2026 (30 days)
        val nov15 = LocalDate.of(2026, 11, 15)
        assertEquals(15L, AnalyticsViewModel.calculateDaysElapsed(Period.MONTH, nov15))
        assertEquals(30L, AnalyticsViewModel.calculateTotalDaysInPeriod(Period.MONTH, nov15))

        // February in non-leap year (28 days)
        val feb10 = LocalDate.of(2025, 2, 10)
        assertEquals(10L, AnalyticsViewModel.calculateDaysElapsed(Period.MONTH, feb10))
        assertEquals(28L, AnalyticsViewModel.calculateTotalDaysInPeriod(Period.MONTH, feb10))

        // Year calculation
        val july1 = LocalDate.of(2026, 7, 1)
        assertEquals(182L, AnalyticsViewModel.calculateDaysElapsed(Period.YEAR, july1))
        assertEquals(365L, AnalyticsViewModel.calculateTotalDaysInPeriod(Period.YEAR, july1))
    }

    @Test
    fun `daily average run rate and projected total calculations`() {
        val daysElapsed = 15L
        val totalDays = 30L
        val currentSpendMinor = 150000L // £1500.00

        val dailyAvg = if (daysElapsed > 0) currentSpendMinor / daysElapsed else 0L
        val projected = dailyAvg * totalDays

        assertEquals(10000L, dailyAvg) // £100.00 / day
        assertEquals(300000L, projected) // £3000.00 projected for month

        // Zero spend case
        val zeroSpend = 0L
        val zeroDailyAvg = if (daysElapsed > 0) zeroSpend / daysElapsed else 0L
        val zeroProjected = zeroDailyAvg * totalDays
        assertEquals(0L, zeroDailyAvg)
        assertEquals(0L, zeroProjected)
    }

    @Test
    fun `cash flow net savings and savings rate calculations`() {
        // Surplus scenario: Income £3,000, Spend £1,800 -> Savings £1,200 (40.0%)
        val incomeMinor = 300000L
        val spendMinor = 180000L
        val netSavingsMinor = incomeMinor - spendMinor
        val savingsRate = if (incomeMinor > 0) (netSavingsMinor * 100.0 / incomeMinor) else null

        assertEquals(120000L, netSavingsMinor)
        assertNotNull(savingsRate)
        assertEquals(40.0, savingsRate!!, 0.001)

        // Deficit scenario: Income £1,000, Spend £1,500 -> Savings -£500 (-50.0%)
        val deficitIncome = 100000L
        val deficitSpend = 150000L
        val deficitSavings = deficitIncome - deficitSpend
        val deficitRate = if (deficitIncome > 0) (deficitSavings * 100.0 / deficitIncome) else null

        assertEquals(-50000L, deficitSavings)
        assertNotNull(deficitRate)
        assertEquals(-50.0, deficitRate!!, 0.001)

        // No income scenario: Income £0 -> savingsRate is null
        val noIncomeRate = if (0L > 0) ((-spendMinor) * 100.0 / 0L) else null
        assertNull(noIncomeRate)
    }

    @Test
    fun `generateInsights creates pacing, top merchant, and peak spend insights`() {
        val snapshot = AnalyticsSnapshot(
            period = Period.MONTH,
            currentTotal = 200000L, // £2,000.00
            previousTotal = 180000L, // £1,800.00
            bars = listOf(
                ChartBar("Week 1", current = 40000L, previous = 35000L),
                ChartBar("Week 2", current = 90000L, previous = 60000L), // Peak week
                ChartBar("Week 3", current = 40000L, previous = 45000L),
                ChartBar("Week 4", current = 30000L, previous = 40000L)
            ),
            categories = emptyList()
        )

        val txs = listOf(
            TransactionEntity(
                id = 1, accountId = 1, source = "Chase", merchant = "Sainsbury's",
                category = "GROCERIES", type = TransactionType.EXPENSE,
                amount = -80000L, timestamp = 1000L
            ),
            TransactionEntity(
                id = 2, accountId = 1, source = "Chase", merchant = "Amazon",
                category = "SHOPPING", type = TransactionType.EXPENSE,
                amount = -40000L, timestamp = 2000L
            ),
            TransactionEntity(
                id = 3, accountId = 1, source = "Chase", merchant = "Shell",
                category = "TRANSPORT", type = TransactionType.EXPENSE,
                amount = -20000L, timestamp = 3000L
            ),
            TransactionEntity(
                id = 4, accountId = 1, source = "Chase", merchant = "Transfer",
                category = "INTERNAL_TRANSFER", type = TransactionType.TRANSFER,
                amount = -50000L, timestamp = 4000L
            )
        )

        // Delta = +11.1%
        val delta = 11.11
        val insights = AnalyticsViewModel.generateInsights(Period.MONTH, delta, snapshot, txs)

        assertEquals(3, insights.size)

        // 1. Pacing Insight (> 5% triggers warning)
        val pacing = insights[0]
        assertEquals("Pacing & Run-Rate", pacing.title)
        assertEquals("⚠️", pacing.emoji)
        assertTrue(pacing.description.contains("above previous month"))

        // 2. Top Merchant Insight (Sainsbury's = £800.00, which is 40% of £2,000.00)
        val merchantInsight = insights[1]
        assertEquals("Top Merchant", merchantInsight.title)
        assertEquals("🏆", merchantInsight.emoji)
        assertTrue(merchantInsight.description.contains("Sainsbury's"))
        assertTrue(merchantInsight.description.contains("40%"))

        // 3. Peak Spend Point (Week 2 had £900.00)
        val peakInsight = insights[2]
        assertEquals("Peak Spend Day", peakInsight.title)
        assertEquals("📅", peakInsight.emoji)
        assertTrue(peakInsight.description.contains("Week 2"))
    }

    @Test
    fun `generateInsights produces encouraging pacing insight when spend is down`() {
        val snapshot = AnalyticsSnapshot(
            period = Period.WEEK,
            currentTotal = 15000L,
            previousTotal = 25000L,
            bars = listOf(ChartBar("Mon", 15000L, 25000L)),
            categories = emptyList()
        )
        val delta = -40.0
        val insights = AnalyticsViewModel.generateInsights(Period.WEEK, delta, snapshot, emptyList())

        assertEquals(2, insights.size) // Pacing and Peak Spend
        val pacing = insights[0]
        assertEquals("✨", pacing.emoji)
        assertTrue(pacing.description.contains("lower than previous week"))
        assertTrue(pacing.description.contains("Great pacing!"))
    }

    @Test
    fun `category share preserves custom category name and emoji in displayLabel`() {
        // Built-in category
        val builtInShare = CategoryShare(
            category = TransactionCategory.GROCERIES,
            amountMinor = 15000L,
            percent = 50.0f
        )
        assertEquals("Groceries", builtInShare.displayLabel)

        // Custom category with custom name and emoji
        val customShare = CategoryShare(
            category = TransactionCategory.OTHER_EXPENSE,
            amountMinor = 10000L,
            percent = 33.3f,
            categoryKey = "c_fitness",
            categoryName = "Fitness & Gym",
            categoryEmoji = "🏋️"
        )
        assertEquals("🏋️ Fitness & Gym", customShare.displayLabel)
        assertEquals("c_fitness", customShare.categoryKey)
    }

    // A6: like-for-like delta; D22: refunds reduce spend and are not income.
    @Test
    fun `buildState compares month to date with the same days of last month`() {
        val zone = java.time.ZoneId.of("UTC")
        fun ts(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun spend(id: Long, amount: Long, t: Long, merchant: String = "Shop") = TransactionEntity(
            id = id, accountId = 1L, amount = -amount, category = "SHOPPING", timestamp = t,
            type = TransactionType.EXPENSE, merchant = merchant
        )
        val today = LocalDate.of(2026, 11, 3)
        val txs = listOf(
            spend(1, 10_000, ts(2026, 6, 1)), // old history so the baseline is established
            spend(2, 2_000, ts(2026, 10, 2)), // 1-3 Oct: like-for-like window
            spend(3, 50_000, ts(2026, 10, 20)), // later in October: must not be compared yet
            spend(4, 3_000, ts(2026, 11, 2), merchant = "Zara"),
            TransactionEntity(
                id = 5, accountId = 1L, amount = 1_000L, category = "OTHER_INCOME", note = "Refund from Zara",
                timestamp = ts(2026, 11, 3), type = TransactionType.INCOME, merchant = "Zara"
            ),
            TransactionEntity(
                id = 6, accountId = 1L, amount = 100_000L, category = "SALARY",
                timestamp = ts(2026, 11, 1), type = TransactionType.INCOME
            )
        )
        val snapshot = AnalyticsSnapshot(Period.MONTH, 3_000L, 52_000L, emptyList(), emptyList())
        val state = AnalyticsViewModel.buildState(
            Period.MONTH, today, snapshot, txs, zone, DayOfWeek.MONDAY, nowMillis = ts(2026, 11, 3)
        )
        assertEquals(2_000L, state.currentTotalMinor) // 3000 spend - 1000 refund
        assertEquals(2_000L, state.previousTotalMinor) // 1-3 Oct only
        assertEquals(52_000L, state.previousFullPeriodTotalMinor)
        assertTrue(state.isPartialPeriod)
        assertEquals(0.0, state.deltaPercent!!, 0.001) // not "96% lower than last month"
        assertEquals(100_000L, state.totalIncomeMinor) // refund is not income
    }
}
