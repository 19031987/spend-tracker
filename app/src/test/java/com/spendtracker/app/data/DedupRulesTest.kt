package com.spendtracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupRulesTest {

    private val t0 = 1_700_000_000_000L

    private fun key(
        amount: Long,
        source: String?,
        merchant: String?,
        at: Long = t0,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long? = null,
        isTransfer: Boolean = false
    ) = DedupKey(amount, type, source, accountId, merchant, at, isTransfer)

    // --- Cross-source (wallet + bank) ---

    @Test
    fun `PayPal alert and Chase alert for the same purchase are one transaction, bank kept`() {
        val paypal = key(3000, "PayPal", "Amazon")
        val chase = key(3000, "Chase", "PAYPAL *AMAZON", at = t0 + 90_000L)
        val d1 = dedupDecision(paypal, chase)
        assertEquals(DedupDecision(DedupKind.WALLET_BANK, DedupSurvivor.INCOMING), d1)
        val d2 = dedupDecision(chase, paypal)
        assertEquals(DedupDecision(DedupKind.WALLET_BANK, DedupSurvivor.EXISTING), d2)
    }

    @Test
    fun `Google Pay alert and bank alert are one transaction`() {
        val gpay = key(450, "Google Pay", "Pret")
        val bank = key(450, "Monzo", "PRET A MANGER", at = t0 + 5_000L)
        assertEquals(DedupKind.WALLET_BANK, dedupDecision(bank, gpay)?.kind)
        // Google Wallet is normalised to Google Pay
        assertEquals(DedupKind.WALLET_BANK, dedupDecision(bank, key(450, "Google Wallet", null))?.kind)
    }

    @Test
    fun `wallet merchant replaces a bank merchant that only names the wallet`() {
        val bank = key(1250, "Chase", "PAYPAL *UBER")
        val paypal = key(1250, "PayPal", "Uber")
        assertEquals("Uber", mergedDedupMerchant(bank, paypal, DedupKind.WALLET_BANK))
        assertEquals("Uber", mergedDedupMerchant(key(1250, "Chase", null), paypal, DedupKind.WALLET_BANK))
        // A specific bank merchant is kept
        assertEquals("Uber Eats", mergedDedupMerchant(key(1250, "Chase", "Uber Eats"), paypal, DedupKind.WALLET_BANK))
        // A wallet merchant that is just the wallet's name never replaces anything
        assertEquals("PAYPAL *UBER", mergedDedupMerchant(bank, key(1250, "PayPal", "PayPal"), DedupKind.WALLET_BANK))
    }

    @Test
    fun `wallet and bank with unrelated merchants are not merged`() {
        assertNull(dedupDecision(key(3000, "Chase", "Tesco"), key(3000, "PayPal", "Amazon", at = t0 + 10_000L)))
    }

    @Test
    fun `wallet pair outside the wallet window is not merged`() {
        val bank = key(3000, "Chase", null)
        assertNotNull(dedupDecision(bank, key(3000, "PayPal", "Amazon", at = t0 + WALLET_DEDUP_WINDOW_MILLIS)))
        assertNull(dedupDecision(bank, key(3000, "PayPal", "Amazon", at = t0 + WALLET_DEDUP_WINDOW_MILLIS + 1)))
    }

    @Test
    fun `two rows from the same wallet with different merchants are never merged`() {
        assertNull(dedupDecision(key(1000, "PayPal", "Uber"), key(1000, "PayPal", "Amazon", at = t0 + 5_000L)))
        assertEquals(DedupKind.SAME_BANK, dedupDecision(key(1000, "PayPal", "Uber"), key(1000, "PayPal", "Uber", at = t0 + 5_000L))?.kind)
    }

    @Test
    fun `two different wallets or a wallet and an unknown source are not merged`() {
        assertNull(dedupDecision(key(1000, "PayPal", null), key(1000, "Google Pay", null, at = t0 + 1_000L)))
        assertNull(dedupDecision(key(1000, "Others", null), key(1000, "PayPal", null, at = t0 + 1_000L)))
    }

    @Test
    fun `preferred source is the bank, never the wallet`() {
        assertEquals("Chase", resolvePreferredBankSource("Chase", "PayPal"))
        assertEquals("Chase", resolvePreferredBankSource("PayPal", "Chase"))
        assertEquals("HSBC", resolvePreferredBankSource("Google Pay", "Premier"))
        assertEquals("HSBC", resolvePreferredBankSource("Premier", "HSBC"))
    }

    // --- Direction / transfers ---

    @Test
    fun `card payment and money received of the same amount are never merged`() {
        val spend = key(2500, "HSBC", null, at = t0 + 10_000L)
        val received = key(2500, "HSBC", null, at = t0 + 40_000L, type = TransactionType.INCOME)
        assertNull(dedupDecision(spend, received))
        assertNull(dedupDecision(received, spend))
        assertFalse(isTransactionDuplicate(-2500L, null, "HSBC", t0, 2500L, null, "HSBC", t0 + 30_000L))
    }

    @Test
    fun `transfer rows are never deduplicated`() {
        val leg = key(5000, "HSBC", null, type = TransactionType.TRANSFER)
        assertNull(dedupDecision(leg, key(5000, "HSBC", null, type = TransactionType.TRANSFER, at = t0 + 1_000L)))
        val pairedExpense = key(5000, "HSBC", null, isTransfer = true)
        assertNull(dedupDecision(pairedExpense, key(5000, "HSBC", null, at = t0 + 1_000L)))
    }

    // --- Same bank, one shared window ---

    @Test
    fun `HSBC and Premier alerts up to the shared window apart are one transaction`() {
        val premier = key(1550, "Premier", null)
        assertEquals(DedupKind.SAME_BANK, dedupDecision(premier, key(1550, "HSBC", "Costa Coffee", at = t0 + 150_000L))?.kind)
        assertEquals(DedupKind.SAME_BANK, dedupDecision(premier, key(1550, "HSBC", null, at = t0 + DEDUP_WINDOW_MILLIS))?.kind)
        assertNull(dedupDecision(premier, key(1550, "HSBC", null, at = t0 + DEDUP_WINDOW_MILLIS + 1)))
    }

    @Test
    fun `two identical known purchases a couple of minutes apart stay separate`() {
        val first = key(320, "Monzo", "Pret")
        // A real repeat alert arrives within seconds; a second genuine purchase minutes later.
        assertEquals(DedupKind.SAME_BANK, dedupDecision(first, key(320, "Monzo", "Pret", at = t0 + SAME_MERCHANT_DEDUP_WINDOW_MILLIS))?.kind)
        assertNull(dedupDecision(first, key(320, "Monzo", "Pret", at = t0 + SAME_MERCHANT_DEDUP_WINDOW_MILLIS + 1)))
        assertNull(dedupDecision(first, key(320, "Monzo", "Pret", at = t0 + 120_000L)))
        // Generic vs known keeps the wider window.
        assertEquals(DedupKind.SAME_BANK, dedupDecision(key(320, "Monzo", null), key(320, "Monzo", "Pret", at = t0 + 120_000L))?.kind)
        assertEquals(DedupKind.SAME_BANK, dedupDecision(key(320, "Monzo", "Card Payment"), key(320, "Monzo", "Pret", at = t0 + DEDUP_WINDOW_MILLIS))?.kind)
    }

    @Test
    fun `manual entries are never merged with or into another row`() {
        val manual = key(1000, "HSBC", "Tesco").copy(isManual = true)
        val captured = key(1000, "HSBC", "Tesco", at = t0 + 1_000L)
        assertNull(dedupDecision(manual, captured))
        assertNull(dedupDecision(captured, manual))
        assertNull(dedupDecision(manual, manual.copy(timestamp = t0 + 1_000L)))
        assertNull(dedupDecision(key(1000, "Chase", null).copy(isManual = true), key(1000, "PayPal", "Tesco", at = t0 + 1_000L)))
        // Stored rows: only alert-captured notes are eligible.
        fun entity(note: String?) = TransactionEntity(
            accountId = 1L, amount = -1000L, category = "GROCERIES", note = note, timestamp = t0,
            type = TransactionType.EXPENSE, merchant = "Tesco", source = "HSBC"
        )
        assertTrue(entity("Weekly shop").toDedupKey().isManual)
        assertTrue(entity(null).toDedupKey().isManual)
        assertFalse(entity("Captured from HSBC alert: You paid £10.00 to Tesco").toDedupKey().isManual)
        // View dedup leaves a manual row and a captured row of the same purchase as two rows.
        val rows = listOf(manual, captured)
        assertEquals(2, dedupeRows(rows, key = { it }, merge = { a, _, _ -> a }).size)
    }

    @Test
    fun `same bank different known merchants are not merged`() {
        assertNull(dedupDecision(key(1000, "HSBC", "John Smith", type = TransactionType.INCOME),
            key(1000, "HSBC", "Sarah Jones", at = t0 + 15_000L, type = TransactionType.INCOME)))
    }

    @Test
    fun `pick prefers a same-bank match with known merchants, then the closest`() {
        val incoming = key(1550, "HSBC", "Costa Coffee", at = t0 + 3_000L)
        val candidates = listOf(
            key(1550, "HSBC", null, at = t0 + 2_000L),
            key(1550, "Premier", "Costa Coffee", at = t0),
            key(1550, "PayPal", "Costa Coffee", at = t0 + 3_000L)
        )
        assertEquals(1, pickDedupMatch(candidates, incoming)?.first)
        assertNull(pickDedupMatch(listOf(key(1550, "HSBC", null, type = TransactionType.INCOME)), incoming))
    }

    @Test
    fun `category merge prefers a specific category`() {
        assertEquals("SHOPPING", mergedDedupCategory("OTHER_EXPENSE", "SHOPPING"))
        assertEquals("DINING", mergedDedupCategory("DINING", "SHOPPING"))
        assertEquals("DINING", mergedDedupCategory("DINING", "OTHER_EXPENSE"))
        assertEquals("SHOPPING", mergedDedupCategory(null, "SHOPPING"))
    }

    // --- View-level dedup ---

    private data class Row(val id: Long, val k: DedupKey)

    @Test
    fun `view dedup keeps the bank row and counts the PayPal purchase once`() {
        val rows = listOf(
            Row(3, key(3000, "Chase", "PAYPAL *AMAZON", at = t0 + 60_000L)),
            Row(2, key(3000, "PayPal", "Amazon", at = t0)),
            Row(1, key(500, "Chase", "Tesco", at = t0 - 10_000L))
        )
        val out = dedupeRows(rows, key = { it.k }) { survivor, other, kind ->
            survivor.copy(k = survivor.k.copy(merchant = mergedDedupMerchant(survivor.k, other.k, kind)))
        }
        assertEquals(listOf(3L, 1L), out.map { it.id })
        assertEquals("Amazon", out.first().k.merchant)
        assertEquals(3500L, out.sumOf { it.k.amountMinor })
    }

    @Test
    fun `view dedup keeps the bank row when the wallet row comes first`() {
        val rows = listOf(
            Row(2, key(3000, "PayPal", "Amazon", at = t0 + 60_000L)),
            Row(1, key(3000, "Chase", null, at = t0))
        )
        val out = dedupeRows(rows, key = { it.k }) { survivor, _, _ -> survivor }
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `view dedup does not merge expense with income or transfer legs`() {
        val rows = listOf(
            Row(1, key(2500, "HSBC", null, at = t0)),
            Row(2, key(2500, "HSBC", null, at = t0 + 30_000L, type = TransactionType.INCOME)),
            Row(3, key(2500, "HSBC", null, at = t0 + 40_000L, type = TransactionType.TRANSFER))
        )
        val out = dedupeRows(rows, key = { it.k }) { survivor, _, _ -> survivor }
        assertEquals(3, out.size)
    }

    @Test
    fun `wallet merchant helpers`() {
        assertEquals("UBER", stripWalletMerchantPrefix("PAYPAL *UBER"))
        assertEquals("", stripWalletMerchantPrefix("Google Pay"))
        assertEquals("Tesco", stripWalletMerchantPrefix("Tesco"))
        assertTrue(merchantNamesWallet("PayPal Payment"))
        assertFalse(merchantNamesWallet("Premier Inn"))
        assertTrue(isWalletMerchantCompatible("PAYPAL *UBER TRIP", "Chase", "Uber", "PayPal"))
        assertFalse(isWalletMerchantCompatible("Tesco", "Chase", "Amazon", "PayPal"))
    }
}
