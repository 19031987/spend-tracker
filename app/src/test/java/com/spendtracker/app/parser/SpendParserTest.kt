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

    @Test
    fun `domain merchant before amount is used as vendor`() {
        // Regression: "your-saving.com £45.50 with HSBC Premier Credit Card" showed Unknown Vendor.
        val text = "your-saving.com £45.50 with HSBC Premier Credit Card \u2022\u20221737"
        for (title in listOf(null, "HSBC")) {
            val parsed = SpendParser.parse(hsbcPkg, title, text)
            assertNotNull(parsed)
            assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
            assertEquals(45.50, parsed.amount, 0.001)
            assertEquals("your-saving.com", parsed.merchant)
            assertEquals("HSBC", parsed.source)
            assertFalse(parsed.merchantIsFallback)
        }
        assertEquals(
            "shop.example.co.uk",
            SpendParser.parse(hsbcPkg, null, "shop.example.co.uk £12.00 with HSBC Premier Credit Card \u2022\u20221737")!!.merchant
        )
        assertEquals(
            "Netflix",
            SpendParser.parse(hsbcPkg, null, "netflix.com £8.99 with HSBC Premier Credit Card \u2022\u20221737")!!.merchant
        )
    }

    @Test
    fun `PayPal purchase from company name uses the cleaned merchant`() {
        // Regression: "Your purchase from UBER PAYMENTS UK LIMITED ..." showed Unknown Vendor.
        val paypalPkg = "com.paypal.android.p2pmobile"
        val text = "Your purchase from UBER PAYMENTS UK LIMITED You authorised a payment of £4.99. Tap for more details."
        for (title in listOf(null, "PayPal")) {
            val parsed = SpendParser.parse(paypalPkg, title, text)
            assertNotNull(parsed)
            assertEquals(SpendParser.TYPE_OUT, parsed!!.type)
            assertEquals(4.99, parsed.amount, 0.001)
            assertEquals("Uber", parsed.merchant)
            assertEquals("Transport & Fuel", parsed.category)
            assertFalse(parsed.merchantIsFallback)
        }
        assertEquals("Uber", SpendParser.parse("", "", text)!!.merchant)
        assertEquals("Etsy", SpendParser.parse(paypalPkg, null, "Your order from Etsy Ltd. You paid £12.00.")!!.merchant)
    }

    // --- Evaluation fixes (v2.3.0); mirrored in test_spend_parser.py ---

    private val paypalPkg = "com.paypal.android.p2pmobile"
    private val walletPkg = "com.google.android.apps.walletnfcrel"
    private val revolutPkg = "com.revolut.revolut"

    private fun assertTx(parsed: ParsedExpense?, type: String, amount: Double, merchant: String? = null, category: String? = null) {
        assertNotNull(parsed)
        assertEquals(type, parsed!!.type)
        assertEquals(amount, parsed.amount, 0.001)
        if (merchant != null) assertEquals(merchant, parsed.merchant)
        if (category != null) assertEquals(category, parsed.category)
    }

    @Test
    fun `star card merchants use the name after the processor prefix`() {
        val card = " with HSBC Premier Credit Card \u2022\u20221737"
        assertTx(SpendParser.parse(hsbcPkg, "HSBC UK", "PAYPAL *NETFLIX £10.99$card"), SpendParser.TYPE_OUT, 10.99, "Netflix", "Bills & Subscriptions")
        assertTx(SpendParser.parse(hsbcPkg, "HSBC UK", "AMZN Mktp UK*AB12CD3E4 £24.99$card"), SpendParser.TYPE_OUT, 24.99, "Amazon", "Shopping")
        assertTx(SpendParser.parse(hsbcPkg, null, "UBER *TRIP £9.87$card"), SpendParser.TYPE_OUT, 9.87, "Uber", "Transport & Fuel")
        assertTx(SpendParser.parse(walletPkg, "SumUp *Cafe Nero", "£4.10 with Visa \u2022\u20221234"), SpendParser.TYPE_OUT, 4.10, "Cafe Nero", "Dining & Drinks")
        assertTx(SpendParser.parse(walletPkg, "SQ *THE COFFEE HOUSE", "£3.75 with Visa \u2022\u20221234"), SpendParser.TYPE_OUT, 3.75, "The Coffee House", "Dining & Drinks")
        assertEquals("Cafe Nero", SpendParser.cleanMerchantName("SumUp *Cafe Nero"))
        assertEquals("The Coffee House", SpendParser.cleanMerchantName("SQ *The Coffee House"))
        assertTx(SpendParser.parse(hsbcPkg, null, "your-saving.com £45.50$card"), SpendParser.TYPE_OUT, 45.50, "your-saving.com")
    }

    @Test
    fun `title and body are matched separately`() {
        assertTx(SpendParser.parse(paypalPkg, "Payment sent", "You sent £30.00 GBP to Tom Jones."), SpendParser.TYPE_OUT, 30.00, "Tom Jones")
        assertTx(SpendParser.parse(paypalPkg, "You've got money", "Jane Doe sent you £25.00 GBP."), SpendParser.TYPE_IN, 25.00, "Jane Doe")
        assertTx(SpendParser.parse(revolutPkg, "Payment from Sarah", "You received £15 from Sarah"), SpendParser.TYPE_IN, 15.0, "Sarah")
        assertTx(SpendParser.parse(revolutPkg, "Paid €8.90 at Lidl", "Spent abroad on your card"), SpendParser.TYPE_OUT, 8.90, "Lidl", "Groceries")
        assertTx(SpendParser.parse(chasePkg, "Interest paid", "We've paid you £4.12 in interest"), SpendParser.TYPE_IN, 4.12, "")
        assertTx(SpendParser.parse(monzoPkg, "Salary", "ACME LTD paid you £2,000.00"), SpendParser.TYPE_IN, 2000.00, "Acme", "Income & Salary")
    }

    @Test
    fun `moves between own accounts are ignored`() {
        assertNull(SpendParser.parse(chasePkg, "Round-up", "We've moved £0.55 to your Round-up account"))
        assertNull(SpendParser.parse(chasePkg, "Money moved", "£200.00 moved from your Current account to your Saver account"))
    }

    @Test
    fun `money moved from your account to someone else is still a payment`() {
        // Regression: "from your" alone must not mark a payment to another person as an internal move.
        assertTx(SpendParser.parse(chasePkg, "Payment sent", "You've moved £50.00 from your Current account to Tom Smith"),
            SpendParser.TYPE_OUT, 50.00, "Tom Smith")
        assertEquals(SpendParser.TYPE_OUT, SpendParser.parse(chasePkg, "Payment sent", "Money moved to Tom's account: £20.00")?.type)
    }

    @Test
    fun `from name stops at into`() {
        assertTx(
            SpendParser.parse(hsbcPkg, "HSBC UK", "You've received £2,450.00 from ACME LTD SALARY into your account ending 4321."),
            SpendParser.TYPE_IN, 2450.00, "Acme Salary", "Income & Salary"
        )
    }

    @Test
    fun `TfL travel charge title is the merchant`() {
        assertTx(SpendParser.parse(walletPkg, "TfL Travel Charge", "£2.80 with Visa \u2022\u20221234"), SpendParser.TYPE_OUT, 2.80, "TfL", "Transport & Fuel")
    }

    @Test
    fun `trailing company suffixes are stripped`() {
        assertTx(SpendParser.parse(hsbcPkg, "HSBC UK", "Contactless payment of £4.50 at GREGGS PLC."), SpendParser.TYPE_OUT, 4.50, "Greggs", "Dining & Drinks")
        assertTx(
            SpendParser.parse("com.americanexpress.android.acctsvcs.uk", "American Express", "A charge of £89.00 at JOHN LEWIS PLC was made on your Card ending 1001."),
            SpendParser.TYPE_OUT, 89.00, "John Lewis", "Shopping"
        )
        assertTx(SpendParser.parse(paypalPkg, "Your automatic payment to Spotify", "You paid £11.99 GBP to Spotify AB."), SpendParser.TYPE_OUT, 11.99, "Spotify")
        assertTx(SpendParser.parse(paypalPkg, "Your purchase from Vinted", "You paid £15.50 to Vinted UAB."), SpendParser.TYPE_OUT, 15.50, "Vinted")
        assertEquals("Ab", SpendParser.cleanMerchantName("AB"))
    }
}
