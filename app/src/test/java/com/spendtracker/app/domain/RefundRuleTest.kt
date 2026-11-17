package com.spendtracker.app.domain

import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** D22: refunds are negative spending, never income. D25: one week start. */
class RefundRuleTest {

    private val zone = ZoneId.of("UTC")

    private fun ts(day: Int): Long = LocalDate.of(2026, 11, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun item(
        type: TransactionType,
        amount: Long,
        categoryKey: String?,
        merchant: String? = "Amazon",
        note: String? = null,
        categoryName: String = "Other",
        day: Int = 10
    ) = TransactionItem(
        id = 1L, type = type, amountMinor = amount, accountId = 1L, accountName = "Chase",
        categoryKey = categoryKey, categoryName = categoryName, merchant = merchant, note = note,
        timestamp = ts(day)
    )

    @Test
    fun `income whose alert text says refund is a refund not income`() {
        val refund = item(
            TransactionType.INCOME, 2000L, "OTHER_INCOME",
            note = "Captured from Monzo alert: Refund of £20.00 from Amazon"
        )
        assertTrue(refund.isRefund())
        assertFalse(refund.isIncome())
        assertFalse(refund.isSpending())
        assertEquals(-2000L, refund.netSpendMinor())
    }

    @Test
    fun `refund category marks a refund whatever the text`() {
        assertTrue(item(TransactionType.INCOME, 500L, "CUSTOM_REFUNDS", merchant = "Shop").isRefund())
        assertTrue(item(TransactionType.INCOME, 500L, "CUSTOM_1", categoryName = "Refunds").isRefund())
    }

    @Test
    fun `explicit income categories and plain income are never refunds`() {
        val salary = item(TransactionType.INCOME, 300000L, "SALARY", note = "salary incl. expense refund")
        assertFalse(salary.isRefund())
        assertTrue(salary.isIncome())
        val plain = item(TransactionType.INCOME, 1000L, "OTHER_INCOME", merchant = "John", note = "John sent you £10")
        assertFalse(plain.isRefund())
        assertTrue(plain.isIncome())
        assertEquals(0L, plain.netSpendMinor())
    }

    @Test
    fun `expenses and excluded rows are never refunds`() {
        assertFalse(item(TransactionType.EXPENSE, 1000L, "SHOPPING", note = "refund").isRefund())
        val excluded = item(TransactionType.INCOME, 1000L, null, note = "refund").copy(excludeFromSpending = true)
        assertFalse(excluded.isRefund())
        assertEquals(1000L, item(TransactionType.EXPENSE, 1000L, "SHOPPING").netSpendMinor())
    }

    @Test
    fun `refund is attributed to the latest earlier spending category at the same merchant`() {
        val txs = listOf(
            item(TransactionType.EXPENSE, 5000L, "GROCERIES", merchant = "Amazon", day = 1),
            item(TransactionType.EXPENSE, 3000L, "SHOPPING", merchant = " amazon ", day = 5),
            item(TransactionType.EXPENSE, 3000L, "ENTERTAINMENT", merchant = "Amazon", day = 20)
        )
        val attribution = RefundAttribution.of(txs)
        val refund = item(TransactionType.INCOME, 3000L, "OTHER_INCOME", merchant = "AMAZON", note = "refund", day = 10)
        assertEquals("SHOPPING", refund.spendCategoryKey(attribution))
        // No earlier spending at that merchant: category unknown, only the total is reduced.
        val unknown = item(TransactionType.INCOME, 3000L, "OTHER_INCOME", merchant = "Argos", note = "refund", day = 10)
        assertNull(unknown.spendCategoryKey(attribution))
        // Ordinary income has no spending category.
        assertNull(item(TransactionType.INCOME, 100L, "SALARY").spendCategoryKey(attribution))
    }

    @Test
    fun `entity rules match the item rules`() {
        val refund = TransactionEntity(
            accountId = 1L, amount = 2500L, category = "OTHER_INCOME", note = "Captured from alert: refund issued",
            timestamp = ts(3), type = TransactionType.INCOME, merchant = "Zara"
        )
        val spend = TransactionEntity(
            accountId = 1L, amount = -4000L, category = "SHOPPING", timestamp = ts(2),
            type = TransactionType.EXPENSE, merchant = "Zara"
        )
        val salary = TransactionEntity(
            accountId = 1L, amount = 100000L, category = "SALARY", timestamp = ts(1), type = TransactionType.INCOME
        )
        assertTrue(refund.isRefund())
        assertFalse(refund.isIncome())
        assertTrue(salary.isIncome())
        assertEquals(-2500L, refund.netSpendMinor())
        assertEquals(4000L, spend.netSpendMinor())
        assertEquals("SHOPPING", refund.spendCategoryKey(refundAttributionOfEntities(listOf(spend, refund))))
        val range = TimeRange(ts(1) - 1, ts(30))
        assertEquals(1500L, netSpendInRange(listOf(spend, refund, salary), range))
        // Never below zero.
        assertEquals(0L, netSpendInRange(listOf(refund), range))
    }

    @Test
    fun `first day of week follows the locale`() {
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(Locale.US))
    }
}
