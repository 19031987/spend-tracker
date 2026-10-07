package com.spendtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizationEngineTest {

    private val validCategories = setOf(
        "GROCERIES", "DINING", "TRANSPORT", "SUBSCRIPTIONS", "UTILITIES", "SALARY", "HARDWARE"
    )

    @Test
    fun `user custom rule takes precedence over dictionary and history`() {
        val rules = listOf(
            MerchantRule(1, MatchType.CONTAINS, "Tesco", "DINING") // User mapped Tesco to Dining!
        )
        val history = listOf(
            HistoryEntry("tesco", "GROCERIES", 10)
        )

        val result = CategorizationEngine.classify("Tesco Express", rules, history, validCategories)
        assertTrue(result is Classification.Matched)
        val matched = result as Classification.Matched
        assertEquals("DINING", matched.categoryKey)
        assertEquals(ClassificationSource.RULE, matched.source)
    }

    @Test
    fun `history takes precedence over built-in dictionary`() {
        val rules = emptyList<MerchantRule>()
        val history = listOf(
            HistoryEntry("shell", "UTILITIES", 5) // User usually categorized Shell as Utilities
        )

        val result = CategorizationEngine.classify("Shell Petrol", rules, history, validCategories)
        assertTrue(result is Classification.Matched)
        val matched = result as Classification.Matched
        assertEquals("UTILITIES", matched.categoryKey)
        assertEquals(ClassificationSource.HISTORY, matched.source)
    }

    @Test
    fun `built-in dictionary correctly identifies merchants`() {
        val rules = emptyList<MerchantRule>()
        val history = emptyList<HistoryEntry>()

        val cases = mapOf(
            "Tesco Superstore" to "GROCERIES",
            "Starbucks Coffee #104" to "DINING",
            "Uber Trip London" to "TRANSPORT",
            "Netflix Subscription" to "SUBSCRIPTIONS",
            "British Gas Direct Debit" to "UTILITIES",
            "Shell Garage" to "TRANSPORT",
            "Pret A Manger" to "DINING"
        )

        for ((merchant, expectedCat) in cases) {
            val res = CategorizationEngine.classify(merchant, rules, history, validCategories)
            assertTrue("Failed for $merchant", res is Classification.Matched)
            assertEquals(expectedCat, (res as Classification.Matched).categoryKey)
            assertEquals(ClassificationSource.DICTIONARY, res.source)
        }
    }

    @Test
    fun `word boundary prevents false matches for short tokens`() {
        val rules = emptyList<MerchantRule>()
        val history = emptyList<HistoryEntry>()

        // "bp" shouldn't match "Subpoena" or "Bolton"
        val res1 = CategorizationEngine.classify("Subpoena Services", rules, history, validCategories)
        assertTrue(res1 is Classification.Unknown)

        // "Apex Hardware" is completely unknown
        val res2 = CategorizationEngine.classify("Apex Hardware", rules, history, validCategories)
        assertTrue(res2 is Classification.Unknown)
    }

    @Test
    fun `longer keywords take priority over substrings`() {
        val rules = emptyList<MerchantRule>()
        val history = emptyList<HistoryEntry>()

        // "uber eats" should match DINING, not TRANSPORT ("uber")
        val res = CategorizationEngine.classify("Uber Eats order", rules, history, validCategories)
        assertTrue(res is Classification.Matched)
        assertEquals("DINING", (res as Classification.Matched).categoryKey)
    }

    @Test
    fun `export formatter generates valid json and csv`() {
        val rows = listOf(
            ExportRow(
                id = 1L,
                date = "2026-10-07 12:00:00",
                type = "EXPENSE",
                account = "Account #1",
                merchant = "Tesco",
                category = "Groceries",
                group = "Food & Dining",
                amountMinor = -1480L,
                note = "Lunch"
            )
        )
        val exportRules = listOf(
            ExportRule("CONTAINS", "Tesco", "Groceries")
        )

        val csv = ExportFormatter.toCsv(rows)
        assertTrue(csv.contains("Tesco"))
        assertTrue(csv.contains("-14.80"))

        val json = ExportFormatter.toJson("2026-10-07", rows, exportRules)
        assertTrue(json.contains("\"merchant\": \"Tesco\""))
        assertTrue(json.contains("\"pattern\": \"Tesco\""))
    }
}
