package com.spendtracker.app.parser

import com.spendtracker.app.data.isMerchantDuplicate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendParserTest {

    private val hsbcPkg = "uk.co.hsbc.hsbcukmobilebanking"
    private val chasePkg = "com.jpmorgan.chase.uk"
    private val monzoPkg = "com.monzo.android"
    private val smsPkg = "com.google.android.apps.messaging"

    @Test
    fun `pipe format with amount sender bank extracts sender as merchant and money in`() {
        val parsed = SpendParser.parse(hsbcPkg, "HSBC", "£50.00 | John Smith | HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("John Smith", parsed.merchant)
        assertEquals("HSBC", parsed.source)
        assertEquals("Transfers In", parsed.category)
        assertFalse(parsed.merchantIsFallback)
    }

    @Test
    fun `pipe format in title extracts correctly`() {
        val parsed = SpendParser.parse(monzoPkg, "£120.00 | Sarah Jones | Monzo", "")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(120.0, parsed.amount, 0.001)
        assertEquals("Sarah Jones", parsed.merchant)
        assertEquals("Monzo", parsed.source)
        assertEquals("Transfers In", parsed.category)
    }

    @Test
    fun `pipe format payroll extracts salary category`() {
        val parsed = SpendParser.parse(chasePkg, "Chase", "£1,250.50 | TechCorp Payroll | Chase")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(1250.50, parsed.amount, 0.001)
        assertEquals("Techcorp Payroll", parsed.merchant)
        assertEquals("Chase", parsed.source)
        assertEquals("Income & Salary", parsed.category)
    }

    @Test
    fun `pipe format sms is accepted without financial tx verb`() {
        val parsed = SpendParser.parse(smsPkg, "HSBC", "£23.00 | Dave Brown | HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(23.0, parsed.amount, 0.001)
        assertEquals("Dave Brown", parsed.merchant)
        assertEquals("HSBC", parsed.source)
    }

    @Test
    fun `pipe format explicit minus is money out`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "-£8.99 | Netflix | HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
        assertEquals(8.99, parsed.amount, 0.001)
        assertEquals("Netflix", parsed.merchant)
        assertEquals("HSBC", parsed.source)
    }

    @Test
    fun `pipe format with unknown bank captures raw bank name`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "£50.00 | John Smith | Some Credit Union")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("John Smith", parsed.merchant)
        assertEquals("Some Credit Union", parsed.source)
    }

    @Test
    fun `pipe format with win tan is not filtered by ignore`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "£50.00 | Win Tan | HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("Win Tan", parsed.merchant)
        assertEquals("HSBC", parsed.source)
    }

    @Test
    fun `untrusted package whatsapp is rejected`() {
        val parsed = SpendParser.parse("com.whatsapp", "", "£50.00 | John | HSBC")
        assertNull(parsed)
    }

    @Test
    fun `pipe format fallback when segment 2 is not a usable name`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "£50.00 | Your card ending 1234 | HSBC")
        assertNotNull(parsed)
        assertEquals(50.0, parsed!!.amount, 0.001)
    }

    @Test
    fun `pipe format balance scrubbed falls through`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "Balance £1,650.99 | John | HSBC")
        assertNull(parsed)
    }

    @Test
    fun `pipe format split between title and text`() {
        val parsed = SpendParser.parse(hsbcPkg, "£50.00 | John Smith", "HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("John Smith", parsed.merchant)
        assertEquals("HSBC", parsed.source)
    }

    @Test
    fun `pipe format with broken bar separator`() {
        val parsed = SpendParser.parse(hsbcPkg, "", "£50.00 ¦ Sarah Jones ¦ HSBC")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("Sarah Jones", parsed.merchant)
        assertEquals("HSBC", parsed.source)
    }

    @Test
    fun `isMerchantDuplicate correctly detects duplicates and distinguishes different senders`() {
        // Same merchant -> duplicate
        assertTrue(isMerchantDuplicate("John Smith", "John Smith", "HSBC"))
        assertTrue(isMerchantDuplicate("john smith", "JOHN SMITH", "HSBC"))

        // Different merchants -> NOT duplicate
        assertFalse(isMerchantDuplicate("John Smith", "Sarah Jones", "HSBC"))

        // Generic or blank merchant -> matches for enrichment
        assertTrue(isMerchantDuplicate("Card Payment", "John Smith", "HSBC"))
        assertTrue(isMerchantDuplicate(null, "John Smith", "HSBC"))
        assertTrue(isMerchantDuplicate("John Smith", null, "HSBC"))
        assertTrue(isMerchantDuplicate("HSBC", "John Smith", "HSBC"))
        assertTrue(isMerchantDuplicate(null, null, "HSBC"))
    }
}
