package com.spendtracker.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonCalculationTest {

    @Test
    fun `data point calculates difference and percentage change accurately`() {
        val dp = DataPoint(label = "Mon", currentValue = 120.0, previousValue = 100.0)
        assertEquals(20.0, dp.difference, 0.001)
        assertEquals(20.0, dp.percentageChange, 0.001)

        val dpDecrease = DataPoint(label = "Tue", currentValue = 80.0, previousValue = 100.0)
        assertEquals(-20.0, dpDecrease.difference, 0.001)
        assertEquals(-20.0, dpDecrease.percentageChange, 0.001)
    }

    @Test
    fun `data point handles zero previous value safely without dividing by zero`() {
        val dpZeroPrev = DataPoint(label = "Wed", currentValue = 50.0, previousValue = 0.0)
        assertEquals(50.0, dpZeroPrev.difference, 0.001)
        assertEquals(100.0, dpZeroPrev.percentageChange, 0.001)

        val dpZeroBoth = DataPoint(label = "Thu", currentValue = 0.0, previousValue = 0.0)
        assertEquals(0.0, dpZeroBoth.difference, 0.001)
        assertEquals(0.0, dpZeroBoth.percentageChange, 0.001)
    }

    @Test
    fun `comparison period provides appropriate labels and reference contexts`() {
        assertEquals("Week", ComparisonPeriod.WEEK.label)
        assertEquals("vs last week", ComparisonPeriod.WEEK.referenceContext)

        assertEquals("Month", ComparisonPeriod.MONTH.label)
        assertEquals("vs last month", ComparisonPeriod.MONTH.referenceContext)

        assertEquals("Year", ComparisonPeriod.YEAR.label)
        assertEquals("vs last year", ComparisonPeriod.YEAR.referenceContext)
    }

    @Test
    fun `baseline guard verifies historical data spans minimum required days`() {
        val vm = ComparisonViewModel()
        // Week requires 7 days
        assertTrue("3 days is initial period for WEEK", vm.isInitialBaseline(3L, ComparisonPeriod.WEEK))
        assertFalse("7 days has sufficient baseline for WEEK", vm.isInitialBaseline(7L, ComparisonPeriod.WEEK))
        assertFalse("10 days has sufficient baseline for WEEK", vm.isInitialBaseline(10L, ComparisonPeriod.WEEK))

        // Month requires 30 days
        assertTrue("5 days is initial period for MONTH", vm.isInitialBaseline(5L, ComparisonPeriod.MONTH))
        assertTrue("20 days is initial period for MONTH", vm.isInitialBaseline(20L, ComparisonPeriod.MONTH))
        assertFalse("30 days has sufficient baseline for MONTH", vm.isInitialBaseline(30L, ComparisonPeriod.MONTH))
        assertFalse("45 days has sufficient baseline for MONTH", vm.isInitialBaseline(45L, ComparisonPeriod.MONTH))

        // Year requires 30 days minimum baseline
        assertTrue("15 days is initial period for YEAR", vm.isInitialBaseline(15L, ComparisonPeriod.YEAR))
        assertFalse("30 days has sufficient baseline for YEAR", vm.isInitialBaseline(30L, ComparisonPeriod.YEAR))
    }

    @Test
    fun `initial period guards against misleading percentage and surfaces onboarding state`() {
        val vm = ComparisonViewModel()
        vm.setInitialPeriod(true)
        vm.selectPeriod(ComparisonPeriod.MONTH)

        val state = vm.uiState.value
        assertTrue(state.isInitialPeriod)
        assertEquals(0.0, state.percentageChange, 0.001)
        assertEquals(
            "Building your baseline: comparisons will appear after your first week/month",
            state.onboardingMessage
        )
    }

    @Test
    fun `established baseline period calculates non-zero percentage change accurately`() {
        val vm = ComparisonViewModel()
        vm.setInitialPeriod(false)
        vm.selectPeriod(ComparisonPeriod.WEEK)

        val state = vm.uiState.value
        assertFalse(state.isInitialPeriod)
        assertTrue("Percentage change should be calculated when baseline is established", state.percentageChange > 0.0)
    }

    @Test
    fun `calculateHistoryDays handles empty transaction list safely`() {
        val vm = ComparisonViewModel()
        assertEquals(0L, vm.calculateHistoryDays(emptyList()))
    }
}
