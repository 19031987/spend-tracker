package com.spendtracker.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CurrencyUtilsTest {

    private val us = Locale.US

    // formatMinor

    @Test
    fun `formatMinor formats positive minor amounts with 2 decimals`() {
        assertEquals("$12.50", CurrencyUtils.formatMinor(1250L, us))
        assertEquals("$0.05", CurrencyUtils.formatMinor(5L, us))
        assertEquals("$1,234.56", CurrencyUtils.formatMinor(123456L, us))
    }

    @Test
    fun `formatMinor formats zero`() {
        assertEquals("$0.00", CurrencyUtils.formatMinor(0L, us))
    }

    // formatSignedMinor

    @Test
    fun `formatSignedMinor with explicit sign prefixes plus or unicode minus`() {
        assertEquals("+$12.50", CurrencyUtils.formatSignedMinor(1250L, isPositive = true, locale = us))
        assertEquals("−$5.00", CurrencyUtils.formatSignedMinor(500L, isPositive = false, locale = us))
    }

    @Test
    fun `formatSignedMinor infers sign from value`() {
        assertEquals("+$12.50", CurrencyUtils.formatSignedMinor(1250L, us))
        assertEquals("−$5.00", CurrencyUtils.formatSignedMinor(-500L, us))
        assertEquals("+$0.00", CurrencyUtils.formatSignedMinor(0L, us))
    }

    // minorToDecimalString

    @Test
    fun `minorToDecimalString converts minor units to plain decimal without symbol`() {
        assertEquals("12.50", CurrencyUtils.minorToDecimalString(1250L))
        assertEquals("0.05", CurrencyUtils.minorToDecimalString(5L))
        assertEquals("1234567890123.45", CurrencyUtils.minorToDecimalString(123456789012345L))
        assertEquals("0.00", CurrencyUtils.minorToDecimalString(0L))
    }

    // parseDecimalToMinor

    @Test
    fun `parseDecimalToMinor parses dot and comma decimal separators`() {
        assertEquals(1234L, CurrencyUtils.parseDecimalToMinor("12.34"))
        assertEquals(1234L, CurrencyUtils.parseDecimalToMinor("12,34"))
        assertEquals(50L, CurrencyUtils.parseDecimalToMinor("0.5"))
        assertEquals(1200L, CurrencyUtils.parseDecimalToMinor("12.00"))
        assertEquals(500000L, CurrencyUtils.parseDecimalToMinor("5000"))
    }

    @Test
    fun `parseDecimalToMinor trims surrounding whitespace`() {
        assertEquals(1234L, CurrencyUtils.parseDecimalToMinor("  12.34  "))
    }

    @Test
    fun `parseDecimalToMinor rejects invalid negative zero and over-precise input`() {
        assertNull(CurrencyUtils.parseDecimalToMinor("abc"))
        assertNull(CurrencyUtils.parseDecimalToMinor(""))
        assertNull(CurrencyUtils.parseDecimalToMinor("-5.00"))
        assertNull(CurrencyUtils.parseDecimalToMinor("0"))
        assertNull(CurrencyUtils.parseDecimalToMinor("0.001"))
        assertNull(CurrencyUtils.parseDecimalToMinor("1.234"))
    }

    @Test
    fun `parseDecimalToMinor round-trips minorToDecimalString`() {
        val minor = 98765L
        val parsed = CurrencyUtils.parseDecimalToMinor(CurrencyUtils.minorToDecimalString(minor))
        assertEquals(minor, parsed)
    }

    @Test
    fun `parseDecimalToMinor treats commas as thousands separators or a decimal comma`() {
        assertEquals(100000L, CurrencyUtils.parseDecimalToMinor("1,000"))
        assertEquals(123456L, CurrencyUtils.parseDecimalToMinor("1,234.56"))
        assertEquals(999L, CurrencyUtils.parseDecimalToMinor("9,99"))
        assertEquals(999L, CurrencyUtils.parseDecimalToMinor("9.99"))
        assertEquals(123456700L, CurrencyUtils.parseDecimalToMinor("1,234,567"))
        assertEquals(1200L, CurrencyUtils.parseDecimalToMinor("12"))
        assertEquals(950L, CurrencyUtils.parseDecimalToMinor("9,5"))
        assertNull(CurrencyUtils.parseDecimalToMinor("abc"))
    }

    @Test
    fun `parseDecimalToMinor strips currency symbols and spaces`() {
        assertEquals(123400L, CurrencyUtils.parseDecimalToMinor("£ 1,234"))
        assertEquals(1050L, CurrencyUtils.parseDecimalToMinor("£10.50"))
    }

    @Test
    fun `parseDecimalToMinor rejects malformed comma input`() {
        assertNull(CurrencyUtils.parseDecimalToMinor("1,2345"))
        assertNull(CurrencyUtils.parseDecimalToMinor("12,34,5"))
        assertNull(CurrencyUtils.parseDecimalToMinor("1.234,56"))
        assertNull(CurrencyUtils.parseDecimalToMinor(","))
    }
}
