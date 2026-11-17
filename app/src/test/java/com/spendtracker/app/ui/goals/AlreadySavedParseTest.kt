package com.spendtracker.app.ui.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlreadySavedParseTest {
    @Test
    fun `blank and zero mean nothing saved yet`() {
        assertEquals(0L, parseAlreadySaved(""))
        assertEquals(0L, parseAlreadySaved("0"))
        assertEquals(0L, parseAlreadySaved("0.00"))
    }

    @Test
    fun `positive amounts parse and junk is rejected`() {
        assertEquals(12_550L, parseAlreadySaved("125.50"))
        assertNull(parseAlreadySaved("abc"))
        assertNull(parseAlreadySaved("1.234"))
    }
}
