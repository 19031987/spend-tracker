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
}
