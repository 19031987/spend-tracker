package com.spendtracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPairRulesTest {

    private val t0 = 1_700_000_000_000L

    private fun leg(
        type: TransactionType,
        source: String?,
        amount: Long = 3200,
        at: Long = t0,
        category: String? = if (type == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE",
        excluded: Boolean = false,
        linked: Boolean = false,
        note: String? = "Captured from $source alert: something"
    ) = TransferLeg(amount, type, source, category, at, excluded, linked, note)

    private val out = leg(TransactionType.EXPENSE, "HSBC")
    private val inn = leg(TransactionType.INCOME, "Chase", at = t0 + 20_000L)

    @Test
    fun `money out of HSBC and the same amount into Chase is an own-account transfer, either order`() {
        assertTrue(isOwnTransferPair(out, inn))
        assertTrue(isOwnTransferPair(inn, out))
    }

    @Test
    fun `different amount, same direction or outside the window is not a transfer`() {
        assertFalse(isOwnTransferPair(out, inn.copy(amountMinor = 3201)))
        assertFalse(isOwnTransferPair(out, leg(TransactionType.EXPENSE, "Chase")))
        assertFalse(isOwnTransferPair(out, inn.copy(timestamp = t0 + TRANSFER_PAIR_WINDOW_MILLIS + 1)))
        assertTrue(isOwnTransferPair(out, inn.copy(timestamp = t0 + TRANSFER_PAIR_WINDOW_MILLIS)))
    }

    @Test
    fun `same bank, HSBC and Premier, a wallet or an unknown bank is not a transfer`() {
        assertFalse(isOwnTransferPair(out, inn.copy(source = "HSBC")))
        assertFalse(isOwnTransferPair(out, inn.copy(source = "HSBC Premier")))
        assertFalse(isOwnTransferPair(out, inn.copy(source = "PayPal")))
        assertFalse(isOwnTransferPair(out.copy(source = "Google Pay"), inn))
        assertFalse(isOwnTransferPair(out, inn.copy(source = "Card Payment")))
        assertFalse(isOwnTransferPair(out, inn.copy(source = null)))
    }

    @Test
    fun `a recognised shop or salary is a real payment`() {
        assertFalse(isOwnTransferPair(out.copy(category = "GROCERIES"), inn))
        assertFalse(isOwnTransferPair(out, inn.copy(category = "SALARY")))
    }

    @Test
    fun `manual, excluded, linked or previously matched rows are left alone`() {
        assertFalse(isOwnTransferPair(out.copy(note = "Lunch with Sam"), inn))
        assertFalse(isOwnTransferPair(out.copy(note = null), inn))
        assertFalse(isOwnTransferPair(out.copy(excluded = true), inn))
        assertFalse(isOwnTransferPair(out, inn.copy(linked = true)))
        val matched = transferMatchNote(out.note, "Chase", TransactionType.INCOME)
        assertFalse(isOwnTransferPair(out.copy(note = matched), inn))
    }

    @Test
    fun `match note keeps the alert text and names the other side`() {
        assertEquals(
            "Captured from HSBC alert: x\nInternal transfer: matched with Chase (money in)",
            transferMatchNote("Captured from HSBC alert: x", "Chase", TransactionType.INCOME)
        )
        assertEquals("Internal transfer: matched with HSBC (money out)", transferMatchNote(null, "HSBC", TransactionType.EXPENSE))
    }
}
