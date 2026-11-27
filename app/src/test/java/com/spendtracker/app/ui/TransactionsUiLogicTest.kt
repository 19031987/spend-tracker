package com.spendtracker.app.ui

import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TransactionsUiLogicTest {

    private fun tx(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String? = "Tesco",
        categoryKey: String? = "GROCERIES",
        source: String = "Monzo",
        exclude: Boolean = false,
        needsReview: Boolean = false,
        amountMinor: Long = 1234
    ) = TransactionItem(
        id = id,
        type = type,
        amountMinor = amountMinor,
        accountId = 1L,
        accountName = source,
        categoryKey = categoryKey,
        merchant = merchant,
        source = source,
        excludeFromSpending = exclude,
        needsReview = needsReview
    )

    private fun cat(key: String, name: String, type: TransactionType = TransactionType.EXPENSE) =
        CategoryEntity(key, 1L, name, "🏷️", "#FED7AA", type, isBuiltIn = false, isHidden = false)

    // C11: never duplicate a merchant rule

    @Test
    fun `existing EQUALS rule is detected case-insensitively`() {
        val rules = listOf(MerchantRuleEntity(1, "EQUALS", " tesco ", "GROCERIES", 0L))
        assertTrue(hasEqualsRule(rules, "Tesco", "GROCERIES"))
        assertFalse(hasEqualsRule(rules, "Tesco", "DINING"))
        assertFalse(hasEqualsRule(listOf(MerchantRuleEntity(2, "CONTAINS", "Tesco", "GROCERIES", 0L)), "Tesco", "GROCERIES"))
    }

    // C13: the created category is found (not a fake key)

    @Test
    fun `created category is the new key with the same name and type`() {
        val before = setOf("GROCERIES", "c_100")
        val after = listOf(
            cat("GROCERIES", "Groceries"),
            cat("c_100", "Garden"),
            cat("c_200", "Garden", TransactionType.INCOME),
            cat("c_300", "Garden")
        )
        assertEquals("c_300", findCreatedCategory(after, before, " garden ", TransactionType.EXPENSE)?.key)
        assertNull(findCreatedCategory(listOf(cat("GROCERIES", "Groceries")), setOf("GROCERIES"), "x", TransactionType.EXPENSE))
    }

    // C15: undo only for rows that re-add without losing data

    @Test
    fun `only plain rows can be restored`() {
        assertTrue(canRestore(tx(1)))
        assertFalse(canRestore(tx(2, type = TransactionType.TRANSFER, categoryKey = "INTERNAL_TRANSFER", exclude = true)))
        assertFalse(canRestore(tx(3, exclude = true)))
    }

    // C12: a transfer re-filed from the quick picker becomes a counted payment

    @Test
    fun `quick category on a transfer turns it into a payment`() {
        val transfer = tx(1, type = TransactionType.TRANSFER, categoryKey = "INTERNAL_TRANSFER", exclude = true)
        val cats = listOf(cat("GROCERIES", "Groceries"), cat("SALARY", "Salary", TransactionType.INCOME))
        val out = quickCategoryTarget(transfer, "GROCERIES", cats)
        assertEquals(TransactionType.EXPENSE, out.type)
        assertFalse(out.excludeFromSpending)
        assertNull(out.destinationAccountId)
        assertEquals(TransactionType.INCOME, quickCategoryTarget(transfer, "SALARY", cats).type)
        // A normal row keeps its own exclusion flag
        val excluded = tx(2, exclude = true)
        assertTrue(quickCategoryTarget(excluded, "GROCERIES", cats).excludeFromSpending)
    }

    // C12 / C19: feed filters

    @Test
    fun `transfers are listed under All and Transfers but not Money out`() {
        val rows = listOf(
            tx(1),
            tx(2, type = TransactionType.TRANSFER, categoryKey = "INTERNAL_TRANSFER", exclude = true, merchant = "Monzo ➔ HSBC"),
            tx(3, type = TransactionType.INCOME, categoryKey = "SALARY", merchant = "ACME")
        )
        assertEquals(listOf(1L, 2L, 3L), filterFeed(rows, "ALL", "").map { it.id })
        assertEquals(listOf(2L), filterFeed(rows, "TRANSFERS", "").map { it.id })
        assertEquals(listOf(1L), filterFeed(rows, "EXPENSE", "").map { it.id })
        assertEquals(listOf(3L), filterFeed(rows, "INCOME", "").map { it.id })
        assertEquals(listOf(1L, 2L, 3L), filterFeed(rows, bankFilterKey("Monzo"), "").map { it.id })
        assertEquals(listOf(3L), filterFeed(rows, "ALL", "acme").map { it.id })
        assertTrue(filterFeed(rows, "ALL", "nothing-matches").isEmpty())
    }

    @Test
    fun `excluded rows have their own filter and a captured transfer leg counts as a transfer`() {
        val rows = listOf(
            tx(1),
            tx(2, exclude = true, merchant = "Refunded lunch"),
            tx(3, type = TransactionType.INCOME, categoryKey = "INTERNAL_TRANSFER", exclude = true, merchant = "Gudipa R P")
        )
        assertEquals(listOf(2L), filterFeed(rows, "EXCLUDED", "").map { it.id })
        assertEquals(listOf(3L), filterFeed(rows, "TRANSFERS", "").map { it.id })
        assertEquals(listOf(1L), filterFeed(rows, "EXPENSE", "").map { it.id })
        assertTrue(filterFeed(rows, "INCOME", "").isEmpty())
    }

    @Test
    fun `blank and generic merchants are unknown`() {
        assertTrue(com.spendtracker.app.ui.components.isUnknownMerchant(null))
        assertTrue(com.spendtracker.app.ui.components.isUnknownMerchant("  "))
        assertTrue(com.spendtracker.app.ui.components.isUnknownMerchant("Card Payment"))
        assertFalse(com.spendtracker.app.ui.components.isUnknownMerchant("PayPal"))
    }

    @Test
    fun `a transfer pair is listed once, by its sending leg`() {
        val sending = tx(10, type = TransactionType.TRANSFER, categoryKey = "INTERNAL_TRANSFER", exclude = true)
            .copy(accountId = 1L, destinationAccountId = 2L)
        val receiving = sending.copy(id = 11L, accountId = 2L, destinationAccountId = 2L)
        val captured = tx(12, type = TransactionType.TRANSFER, categoryKey = "INTERNAL_TRANSFER", exclude = true)
        assertFalse(com.spendtracker.app.ui.components.isTransferDestinationLeg(sending))
        assertTrue(com.spendtracker.app.ui.components.isTransferDestinationLeg(receiving))
        assertFalse(com.spendtracker.app.ui.components.isTransferDestinationLeg(captured))
        assertFalse(com.spendtracker.app.ui.components.isTransferDestinationLeg(tx(13)))
    }

    @Test
    fun `filters are active when searching or not on All`() {
        assertFalse(isFeedFilterActive("ALL", "  "))
        assertTrue(isFeedFilterActive("ALL", "tesco"))
        assertTrue(isFeedFilterActive("TRANSFERS", ""))
    }

    // C14: changing the date keeps the time of day

    @Test
    fun `withDate keeps the original time of day`() {
        val original = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 14, 35, 20)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val moved = Calendar.getInstance().apply { timeInMillis = withDate(original, 2026, Calendar.FEBRUARY, 3) }
        assertEquals(2026, moved.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, moved.get(Calendar.MONTH))
        assertEquals(3, moved.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, moved.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, moved.get(Calendar.MINUTE))
    }

    @Test
    fun `currency symbol follows the locale`() {
        assertEquals("£", localCurrencySymbol(java.util.Locale.UK))
    }
}
