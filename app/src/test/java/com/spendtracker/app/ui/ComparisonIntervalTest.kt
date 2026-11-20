package com.spendtracker.app.ui

import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ComparisonIntervalTest {

    private val zone = ZoneId.of("UTC")
    // UK weeks start on Monday (the week start now follows the locale: D25).
    private val locale = Locale.UK
    private val vm = ComparisonViewModel()

    private fun createTx(
        id: Long = 1L,
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = -1500L, // £15.00 in minor units
        date: LocalDate,
        category: String? = "GROCERIES",
        merchant: String? = "Tesco",
        excludeFromSpending: Boolean = false
    ): TransactionEntity {
        val ts = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        return TransactionEntity(
            id = id,
            accountId = 1L,
            source = "Chase",
            merchant = merchant,
            category = category,
            type = type,
            amount = amount,
            note = null,
            timestamp = ts,
            excludeFromSpending = excludeFromSpending
        )
    }

    @Test
    fun `calculateIntervalComparison for WEEK aggregates 7 days from Mon to Sun with real expenses`() {
        // Wednesday Nov 4, 2026
        val today = LocalDate.of(2026, 11, 4)
        // Current week Monday is Nov 2, previous week Monday is Oct 26
        val txs = listOf(
            createTx(id = 1, amount = -1500, date = LocalDate.of(2026, 11, 2)), // Cur Mon: £15.00
            createTx(id = 2, amount = -2550, date = LocalDate.of(2026, 11, 3)), // Cur Tue: £25.50
            createTx(id = 3, amount = -5000, date = LocalDate.of(2026, 11, 4)), // Cur Wed: £50.00
            createTx(id = 4, amount = -3000, date = LocalDate.of(2026, 10, 26)) // Prev Mon: £30.00
        )

        val points = vm.calculateIntervalComparison(
            period = ComparisonPeriod.WEEK,
            transactions = txs,
            today = today,
            zone = zone,
            locale = locale
        )

        assertEquals(7, points.size)
        // Mon
        assertEquals("Mon", points[0].label)
        assertEquals(15.00, points[0].currentValue, 0.001)
        assertEquals(30.00, points[0].previousValue, 0.001)

        // Tue
        assertEquals("Tue", points[1].label)
        assertEquals(25.50, points[1].currentValue, 0.001)
        assertEquals(0.00, points[1].previousValue, 0.001)

        // Wed
        assertEquals("Wed", points[2].label)
        assertEquals(50.00, points[2].currentValue, 0.001)
        assertEquals(0.00, points[2].previousValue, 0.001)

        // Thu..Sun zero
        for (i in 3 until 7) {
            assertEquals(0.00, points[i].currentValue, 0.001)
            assertEquals(0.00, points[i].previousValue, 0.001)
        }
    }

    @Test
    fun `calculateIntervalComparison excludes non-spending transactions and internal transfers`() {
        val today = LocalDate.of(2026, 11, 4)
        val txs = listOf(
            createTx(id = 1, amount = -2000, date = LocalDate.of(2026, 11, 2)), // Real: £20.00
            createTx(id = 2, amount = -5000, date = LocalDate.of(2026, 11, 2), excludeFromSpending = true), // Excluded
            createTx(id = 3, amount = -10000, date = LocalDate.of(2026, 11, 2), category = "INTERNAL_TRANSFER"), // Transfer excluded
            createTx(id = 4, type = TransactionType.INCOME, amount = 15000, date = LocalDate.of(2026, 11, 2)), // Income excluded
            createTx(id = 5, type = TransactionType.TRANSFER, amount = -8000, date = LocalDate.of(2026, 11, 2)) // Transfer type excluded
        )

        val points = vm.calculateIntervalComparison(
            period = ComparisonPeriod.WEEK,
            transactions = txs,
            today = today,
            zone = zone,
            locale = locale
        )

        assertEquals(20.00, points[0].currentValue, 0.001)
    }

    @Test
    fun `calculateIntervalComparison for MONTH aggregates 4 weeks accurately across month boundaries`() {
        val today = LocalDate.of(2026, 11, 15) // November 2026 (30 days)
        val txs = listOf(
            createTx(id = 1, amount = -4000, date = LocalDate.of(2026, 11, 3)),  // Week 1 (Days 1-7): £40.00
            createTx(id = 2, amount = -6000, date = LocalDate.of(2026, 11, 10)), // Week 2 (Days 8-14): £60.00
            createTx(id = 3, amount = -8000, date = LocalDate.of(2026, 11, 18)), // Week 3 (Days 15-21): £80.00
            createTx(id = 4, amount = -10000, date = LocalDate.of(2026, 11, 28)), // Week 4 (Days 22-30): £100.00
            createTx(id = 5, amount = -3500, date = LocalDate.of(2026, 10, 5))   // Prev Month Week 1: £35.00
        )

        val points = vm.calculateIntervalComparison(
            period = ComparisonPeriod.MONTH,
            transactions = txs,
            today = today,
            zone = zone,
            locale = locale
        )

        assertEquals(4, points.size)
        assertEquals("Week 1", points[0].label)
        assertEquals(40.00, points[0].currentValue, 0.001)
        assertEquals(35.00, points[0].previousValue, 0.001)

        assertEquals("Week 2", points[1].label)
        assertEquals(60.00, points[1].currentValue, 0.001)

        assertEquals("Week 3", points[2].label)
        assertEquals(80.00, points[2].currentValue, 0.001)

        assertEquals("Week 4", points[3].label)
        assertEquals(100.00, points[3].currentValue, 0.001)
    }

    @Test
    fun `calculateIntervalComparison for YEAR aggregates 12 calendar months`() {
        val today = LocalDate.of(2026, 6, 15)
        val txs = listOf(
            createTx(id = 1, amount = -12000, date = LocalDate.of(2026, 1, 10)), // Jan: £120.00
            createTx(id = 2, amount = -18000, date = LocalDate.of(2026, 3, 20)), // Mar: £180.00
            createTx(id = 3, amount = -25000, date = LocalDate.of(2025, 3, 15))  // Prev Year Mar: £250.00
        )

        val points = vm.calculateIntervalComparison(
            period = ComparisonPeriod.YEAR,
            transactions = txs,
            today = today,
            zone = zone,
            locale = locale
        )

        assertEquals(12, points.size)
        // Jan
        assertEquals("Jan", points[0].label)
        assertEquals(120.00, points[0].currentValue, 0.001)
        assertEquals(0.00, points[0].previousValue, 0.001)

        // Mar
        assertEquals("Mar", points[2].label)
        assertEquals(180.00, points[2].currentValue, 0.001)
        assertEquals(250.00, points[2].previousValue, 0.001)
    }

    @Test
    fun `calculateIntervalComparison with empty transactions returns zero-filled data points`() {
        val today = LocalDate.of(2026, 11, 4)

        val weekPoints = vm.calculateIntervalComparison(ComparisonPeriod.WEEK, emptyList(), today, zone, locale)
        assertEquals(7, weekPoints.size)
        assertTrue(weekPoints.all { it.currentValue == 0.0 && it.previousValue == 0.0 })

        val monthPoints = vm.calculateIntervalComparison(ComparisonPeriod.MONTH, emptyList(), today, zone, locale)
        assertEquals(4, monthPoints.size)
        assertTrue(monthPoints.all { it.currentValue == 0.0 && it.previousValue == 0.0 })

        val yearPoints = vm.calculateIntervalComparison(ComparisonPeriod.YEAR, emptyList(), today, zone, locale)
        assertEquals(12, yearPoints.size)
        assertTrue(yearPoints.all { it.currentValue == 0.0 && it.previousValue == 0.0 })
    }

    // D25: the week starts on the locale's first day (Sunday in the US), like the repository.
    @Test
    fun `calculateIntervalComparison WEEK starts on the locale first day`() {
        val today = LocalDate.of(2026, 11, 4) // Wednesday
        val txs = listOf(createTx(id = 1, amount = -1000, date = LocalDate.of(2026, 11, 1))) // Sunday
        val points = vm.calculateIntervalComparison(ComparisonPeriod.WEEK, txs, today, zone, Locale.US)
        assertEquals("Sun", points[0].label)
        assertEquals(10.00, points[0].currentValue, 0.001)
    }

    // D22: a refund lowers the day's spend instead of being ignored.
    @Test
    fun `calculateIntervalComparison subtracts refunds`() {
        val today = LocalDate.of(2026, 11, 4)
        val txs = listOf(
            createTx(id = 1, amount = -3000, date = LocalDate.of(2026, 11, 2), merchant = "Zara"),
            createTx(
                id = 2, type = TransactionType.INCOME, amount = 1000, date = LocalDate.of(2026, 11, 2),
                category = "OTHER_INCOME", merchant = "Zara"
            ).copy(note = "refund")
        )
        val points = vm.calculateIntervalComparison(ComparisonPeriod.WEEK, txs, today, zone, locale)
        assertEquals(20.00, points[0].currentValue, 0.001)
    }

    // A5 / A6: no sample data, and totals compare like-for-like.
    @Test
    fun `computeComparison has no data flag and no sample data when empty`() {
        val result = vm.computeComparison(
            ComparisonPeriod.MONTH, ComparisonMode.CATEGORIES, emptyList(), emptyMap(),
            LocalDate.of(2026, 11, 3), zone, locale
        )
        assertTrue(result.breakdown.isEmpty())
        assertEquals(false, result.hasData)
        assertEquals(0.0, result.currentTotal, 0.001)
    }

    @Test
    fun `computeComparison totals use the same elapsed days of the previous period`() {
        val today = LocalDate.of(2026, 11, 3)
        val txs = listOf(
            createTx(id = 1, amount = -2000, date = LocalDate.of(2026, 10, 2)),
            createTx(id = 2, amount = -50000, date = LocalDate.of(2026, 10, 25), category = "RENT"),
            createTx(id = 3, amount = -1500, date = LocalDate.of(2026, 11, 2))
        )
        for (mode in ComparisonMode.values()) {
            val result = vm.computeComparison(ComparisonPeriod.MONTH, mode, txs, emptyMap(), today, zone, locale)
            assertEquals(15.00, result.currentTotal, 0.001)
            assertEquals(20.00, result.previousTotal, 0.001)
            assertTrue(result.hasData)
        }
        val categories = vm.computeComparison(
            ComparisonPeriod.MONTH, ComparisonMode.CATEGORIES, txs, emptyMap(), today, zone, locale
        ).breakdown
        // Rent was paid later in October, so it is not part of the 1-3 Oct comparison.
        assertEquals(1, categories.size)
        assertEquals(20.00, categories.single().previousValue, 0.001)
    }
}
