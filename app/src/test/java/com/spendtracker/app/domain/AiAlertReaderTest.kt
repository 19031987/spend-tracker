package com.spendtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAlertReaderTest {

    private val alert = "Monzo £12.40 at THE CORNER DELI LTD on your card ending 1234"

    @Test
    fun `reply lines are read, with markdown and case tolerated`() {
        val r = AiAlertReader.parseReply("**MERCHANT:** The Corner Deli Ltd\ndirection: out\nOWN_TRANSFER: No")!!
        assertEquals("The Corner Deli Ltd", r.merchant)
        assertEquals("OUT", r.direction)
        assertFalse(r.ownTransfer)
    }

    @Test
    fun `NONE, UNKNOWN and a reply without the lines give nothing`() {
        val r = AiAlertReader.parseReply("MERCHANT: NONE\nDIRECTION: UNKNOWN\nOWN_TRANSFER: YES")!!
        assertNull(r.merchant)
        assertNull(r.direction)
        assertTrue(r.ownTransfer)
        assertNull(AiAlertReader.parseReply("Sorry, I can't help with that."))
    }

    @Test
    fun `a merchant is trusted only when every word is in the alert`() {
        assertEquals("The Corner Deli", AiAlertReader.validMerchant(alert, "The Corner Deli", "Monzo"))
        assertEquals("the corner deli ltd", AiAlertReader.validMerchant(alert, " the  corner deli ltd ", "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, "Tesco", "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, "Corner Deli Cafe", "Monzo"))
    }

    @Test
    fun `the bank itself, generic words, numbers and long text are refused`() {
        assertNull(AiAlertReader.validMerchant(alert, "Monzo", "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, "card", "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, "1234", "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, null, "Monzo"))
        assertNull(AiAlertReader.validMerchant(alert, "at THE CORNER DELI LTD on your card ending 1234 at", "Monzo"))
    }

    @Test
    fun `prompt carries the alert and is capped`() {
        assertTrue(AiAlertReader.prompt(alert).endsWith(alert))
        assertTrue(AiAlertReader.prompt("x".repeat(5000)).length < 700)
    }
}
