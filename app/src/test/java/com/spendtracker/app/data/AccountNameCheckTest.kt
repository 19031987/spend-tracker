package com.spendtracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountNameCheckTest {

    private val accounts = listOf(
        AccountEntity(1, "Chase"),
        AccountEntity(2, "HSBC"),
        AccountEntity(3, "Credit Card"),
        AccountEntity(4, "Joint Pot")
    )

    private fun invalid(name: String, editingId: Long? = null): String {
        val r = checkAccountName(name, accounts, editingId)
        assertTrue("$name should be rejected, got $r", r is AccountNameCheck.Invalid)
        return (r as AccountNameCheck.Invalid).reason
    }

    @Test
    fun `plain new name is title-cased and accepted`() {
        val r = checkAccountName("  holiday   fund ", accounts)
        assertEquals(AccountNameCheck.Valid("Holiday Fund", adjusted = false), r)
    }

    @Test
    fun `blank and reserved names are rejected`() {
        assertTrue(invalid("   ").contains("Enter a name"))
        assertTrue(invalid("Messages").contains("reserved"))
        assertTrue(invalid("other").contains("reserved"))
    }

    @Test
    fun `names that normalise onto an existing bank are rejected`() {
        assertTrue(invalid("Savings pot").contains("\"Chase\""))
        assertTrue(invalid("hsbc premier").contains("HSBC"))
        assertTrue(invalid("joint pot").contains("already exists"))
    }

    @Test
    fun `renaming an account to its own name in another case is allowed`() {
        assertEquals(AccountNameCheck.Valid("Joint Pot", adjusted = false), checkAccountName("JOINT POT", accounts, editingId = 4))
    }

    @Test
    fun `brand aliases are accepted but flagged as adjusted`() {
        val r = checkAccountName("Monzo joint", accounts)
        assertEquals(AccountNameCheck.Valid("Monzo", adjusted = true), r)
    }

    @Test
    fun `protected accounts are the ones recreated at launch`() {
        assertTrue(isProtectedAccount("chase bank"))
        assertTrue(isProtectedAccount("HSBC"))
        assertTrue(isProtectedAccount("credit card"))
        assertFalse(isProtectedAccount("Joint Pot"))
    }

    @Test
    fun `recognised bank names are detected for the rename warning`() {
        assertTrue(isRecognisedBankName("Monzo"))
        assertTrue(isRecognisedBankName("barclaycard"))
        assertTrue(isRecognisedBankName("Credit Card"))
        assertFalse(isRecognisedBankName("Joint Pot"))
        assertFalse(isRecognisedBankName("Wise"))
    }
}
