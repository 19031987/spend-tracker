package com.spendtracker.app.parser

import com.spendtracker.app.data.isMerchantDuplicate
import com.spendtracker.app.data.isTransactionDuplicate
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.data.resolvePreferredBankSource
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
    fun `exact photo sample Money In extracts sender and Money In type`() {
        val parsed = SpendParser.parse(hsbcPkg, "Money In", "£50.00 transaction | TRIPURA JHANSI RANCHASE | HSBC ADVANCE")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_IN, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
        assertEquals("Tripura Jhansi Ranchase", parsed.merchant)
        assertEquals("HSBC", parsed.source)
        assertEquals("Money In", parsed.category)
        assertFalse(parsed.merchantIsFallback)
    }

    @Test
    fun `exact counterpart Money Out extracts merchant and Money Out type with expense category`() {
        val parsed = SpendParser.parse(hsbcPkg, "Money Out", "£15.50 transaction | Costa Coffee | HSBC ADVANCE")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
        assertEquals(15.50, parsed.amount, 0.001)
        assertEquals("Costa Coffee", parsed.merchant)
        assertEquals("HSBC", parsed.source)
        assertEquals("Dining & Drinks", parsed.category)
        assertFalse(parsed.merchantIsFallback)
    }

    @Test
    fun `exact counterpart Money Out for groceries extracts Groceries`() {
        val parsed = SpendParser.parse(hsbcPkg, "Money Out", "£35.20 transaction | Tesco | HSBC ADVANCE")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
        assertEquals(35.20, parsed.amount, 0.001)
        assertEquals("Tesco Stores", parsed.merchant)
        assertEquals("HSBC", parsed.source)
        assertEquals("Groceries", parsed.category)
    }

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
        assertTrue(isMerchantDuplicate("Unknown Vendor", "Unknown", "HSBC"))
    }

    @Test
    fun `premier bank pipe format resolves source to HSBC`() {
        val parsed = SpendParser.parse(hsbcPkg, "Money Out", "£15.50 transaction | Costa Coffee | PREMIER")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
        assertEquals(15.50, parsed.amount, 0.001)
        assertEquals("Costa Coffee", parsed.merchant)
        assertEquals("HSBC", parsed.source)
        assertEquals("HSBC", normalizeBankName("Premier"))
        assertEquals("HSBC", normalizeBankName("PREMIER"))
        assertEquals("HSBC", normalizeBankName("HSBC Premier"))
    }

    @Test
    fun `same amount and same vendor by Premier and HSBC is duplicate and saves HSBC`() {
        val ts = 1_700_000_000_000L
        assertTrue(
            isTransactionDuplicate(
                existingAmountMinor = -1550L,
                existingMerchant = "Costa Coffee",
                existingSource = "Premier",
                existingTimestamp = ts,
                newAmountMinor = -1550L,
                newMerchant = "Costa Coffee",
                newSource = "HSBC",
                newTimestamp = ts + 5_000L
            )
        )
        assertEquals("HSBC", resolvePreferredBankSource("Premier", "HSBC"))
        assertEquals("HSBC", resolvePreferredBankSource("HSBC", "Premier"))
    }

    @Test
    fun `unknown transaction with same amount by Premier and HSBC is duplicate and saves HSBC`() {
        val ts = 1_700_000_000_000L
        assertTrue(
            isTransactionDuplicate(
                existingAmountMinor = -2500L,
                existingMerchant = null,
                existingSource = "Premier",
                existingTimestamp = ts,
                newAmountMinor = -2500L,
                newMerchant = "Unknown Vendor",
                newSource = "HSBC",
                newTimestamp = ts + 10_000L
            )
        )
        assertEquals("HSBC", resolvePreferredBankSource("Premier", "HSBC"))
    }

    @Test
    fun `unknown transactions with different amount and different time are not duplicates`() {
        val ts = 1_700_000_000_000L
        // Different amount AND different time -> NOT duplicate
        assertFalse(
            isTransactionDuplicate(
                existingAmountMinor = -1200L,
                existingMerchant = null,
                existingSource = "Premier",
                existingTimestamp = ts,
                newAmountMinor = -3500L,
                newMerchant = null,
                newSource = "HSBC",
                newTimestamp = ts + 3_600_000L
            )
        )
        // Different amount at same time -> NOT duplicate
        assertFalse(
            isTransactionDuplicate(
                existingAmountMinor = -1200L,
                existingMerchant = null,
                existingSource = "Premier",
                existingTimestamp = ts,
                newAmountMinor = -3500L,
                newMerchant = null,
                newSource = "HSBC",
                newTimestamp = ts + 2_000L
            )
        )
        // Same amount at different time -> NOT duplicate
        assertFalse(
            isTransactionDuplicate(
                existingAmountMinor = -1200L,
                existingMerchant = null,
                existingSource = "Premier",
                existingTimestamp = ts,
                newAmountMinor = -1200L,
                newMerchant = null,
                newSource = "HSBC",
                newTimestamp = ts + 3_600_000L
            )
        )
    }

    @Test
    fun `merchant Premier Inn is preserved and not confused with Premier bank`() {
        val parsed = SpendParser.parse(chasePkg, "Chase", "You spent £65.00 at Premier Inn")
        assertNotNull(parsed)
        assertEquals("Premier Inn", parsed!!.merchant)
        assertEquals("Chase", parsed.source)

        val parsedNoPkg = SpendParser.parse("", "Card Alert", "You spent £65.00 at Premier Inn")
        assertNotNull(parsedNoPkg)
        assertEquals("Premier Inn", parsedNoPkg!!.merchant)
        assertEquals("Card Payment", parsedNoPkg.source)
    }

    @Test
    fun `standalone Premier title or package resolves source to HSBC`() {
        val parsed = SpendParser.parse("", "Premier", "You spent £15.50 at Costa Coffee")
        assertNotNull(parsed)
        assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
        assertEquals("Costa Coffee", parsed.merchant)
        assertEquals("HSBC", parsed.source)

        val parsedUnknown = SpendParser.parse("Premier Account", "Premier", "£25.00 card payment")
        assertNotNull(parsedUnknown)
        assertEquals(SpendParser.TYPE_OUT, parsedUnknown!!.type)
        assertEquals("", parsedUnknown.merchant)
        assertTrue(parsedUnknown.merchantIsFallback)
        assertEquals("HSBC", parsedUnknown.source)
    }

    @Test
    fun `pipe format with Unknown vendor or no title keeps expense direction and HSBC source`() {
        val parsedUnknown = SpendParser.parse(hsbcPkg, "HSBC", "£25.00 transaction | Unknown | PREMIER")
        assertNotNull(parsedUnknown)
        assertEquals(SpendParser.TYPE_OUT, parsedUnknown!!.type)
        assertEquals("", parsedUnknown.merchant)
        assertTrue(parsedUnknown.merchantIsFallback)
        assertEquals("HSBC", parsedUnknown.source)

        val parsedExpenseNoTitle = SpendParser.parse("Premier Account", "", "£15.50 transaction | Costa Coffee | PREMIER")
        assertNotNull(parsedExpenseNoTitle)
        assertEquals(SpendParser.TYPE_OUT, parsedExpenseNoTitle!!.type)
        assertEquals("Costa Coffee", parsedExpenseNoTitle.merchant)
        assertEquals("HSBC", parsedExpenseNoTitle.source)
    }
}
