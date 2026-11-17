package com.spendtracker.app.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetAmountErrorTest {
    @Test
    fun `zero asks for an amount above 0`() {
        assertEquals("Enter an amount above 0", budgetAmountError("0"))
        assertEquals("Enter an amount above 0", budgetAmountError("0.00"))
    }

    @Test
    fun `too many decimals keeps the decimals message`() {
        assertEquals("Enter an amount with up to 2 decimals", budgetAmountError("1.234"))
    }

    @Test
    fun `blank and valid amounts have no error`() {
        assertNull(budgetAmountError(""))
        assertNull(budgetAmountError("150"))
    }
}
