package com.spendtracker.app.ui

import com.spendtracker.app.data.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultAccountTest {

    private val all = listOf(
        AccountEntity(id = 1L, name = "Chase"),
        AccountEntity(id = 2L, name = "Monzo"),
        AccountEntity(id = 3L, name = "HSBC"),
        AccountEntity(id = 4L, name = "Monzo Bank")
    )
    private val dedup = deduplicateAccounts(all)

    @Test
    fun `deduplicated list is one entry per bank sorted by name`() {
        assertEquals(listOf("Chase", "HSBC", "Monzo"), dedup.map { it.name })
    }

    @Test
    fun `user selection wins`() {
        assertEquals(3L, pickDefaultAccount(all, dedup, selectedId = 3L, preferredId = 2L)?.id)
    }

    @Test
    fun `last used account is preselected`() {
        assertEquals(2L, pickDefaultAccount(all, dedup, selectedId = null, preferredId = 2L)?.id)
    }

    @Test
    fun `last used duplicate maps to the merged entry of the same bank`() {
        assertEquals("Monzo", pickDefaultAccount(all, dedup, selectedId = null, preferredId = 4L)?.name)
    }

    @Test
    fun `without history the first account is used, not a hard-coded bank`() {
        val accounts = listOf(AccountEntity(id = 7L, name = "Starling"), AccountEntity(id = 8L, name = "Barclays"))
        val d = deduplicateAccounts(accounts)
        assertEquals("Barclays", pickDefaultAccount(accounts, d, selectedId = null, preferredId = null)?.name)
    }

    @Test
    fun `unknown ids fall back to the first account and empty list gives null`() {
        assertEquals("Chase", pickDefaultAccount(all, dedup, selectedId = 99L, preferredId = 98L)?.name)
        assertNull(pickDefaultAccount(emptyList(), emptyList(), selectedId = null, preferredId = null))
    }
}
