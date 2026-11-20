package com.spendtracker.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {

    @Test
    fun `new session starts locked with no prompt shown`() {
        val session = AppLockSession()
        assertFalse(session.isUnlocked)
        assertFalse(session.autoPromptShown)
        assertNull(session.errorMessage)
    }

    @Test
    fun `unlocking clears the error and stays unlocked`() {
        val session = AppLockSession()
        session.errorMessage = "Fingerprint not recognised. Please try again."
        session.markUnlocked()
        assertTrue(session.isUnlocked)
        assertNull(session.errorMessage)
    }

    @Test
    fun `auto prompt is recorded once per session`() {
        val session = AppLockSession()
        session.markAutoPromptShown()
        assertTrue(session.autoPromptShown)
        assertEquals(false, session.isUnlocked)
    }
}
