package com.spendtracker.app.domain.recurring

import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class RecurringDetectorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 11, 6)
    private var nextId: Long = 1L

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun tx(
        merchant: String?,
        date: LocalDate,
        amount: Long = 1000L,
        type: TransactionType = TransactionType.EXPENSE,
        excluded: Boolean = false
    ): TransactionItem = TransactionItem(
        id = nextId++,
        type = type,
        amountMinor = amount,
        accountId = 1L,
        accountName = "Chase",
        categoryKey = "SUBSCRIPTIONS",
        merchant = merchant,
        timestamp = millis(date).plus(12L * 60L * 60L * 1000L),
        excludeFromSpending = excluded
    )

    private fun item(
        id: Long,
        key: String,
        anchor: LocalDate,
        amount: Long = 1000L,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        status: String = "APPROVED",
        isIncome: Boolean = false
    ): RecurringItemEntity = RecurringItemEntity(
        id = id,
        merchantKey = key,
        displayName = key,
        categoryKey = "SUBSCRIPTIONS",
        amountMinor = amount,
        frequency = frequency.name,
        anchorDate = millis(anchor),
        status = status,
        isIncome = isIncome,
        createdAt = 0L
    )

    @Test
    fun `monthly subscription is detected with the next date`() {
        val txs = listOf(
            tx("Netflix", LocalDate.of(2026, 8, 15), 1599L),
            tx("Netflix", LocalDate.of(2026, 9, 15), 1599L),
            tx("Netflix", LocalDate.of(2026, 10, 15), 1599L)
        )
        val result = RecurringDetector.detect(txs, today, zone)
        assertEquals(1, result.size)
        val c = result.first()
        assertEquals("netflix", c.merchantKey)
        assertEquals(RecurringFrequency.MONTHLY, c.frequency)
        assertEquals(LocalDate.of(2026, 10, 15), c.lastDate)
        assertEquals(LocalDate.of(2026, 11, 15), c.nextDate)
        assertEquals(3, c.occurrences)
        assertEquals(1599L, c.typicalAmountMinor)
        assertFalse(c.amountVaries)
        assertFalse(c.isIncome)
    }

    @Test
    fun `weekly payment is detected`() {
        val txs = listOf(
            tx("Corner Cafe Club", LocalDate.of(2026, 10, 12)),
            tx("Corner Cafe Club", LocalDate.of(2026, 10, 19)),
            tx("Corner Cafe Club", LocalDate.of(2026, 10, 26)),
            tx("Corner Cafe Club", LocalDate.of(2026, 11, 2))
        )
        val c = RecurringDetector.detect(txs, today, zone).single()
        assertEquals(RecurringFrequency.WEEKLY, c.frequency)
        assertEquals(LocalDate.of(2026, 11, 9), c.nextDate)
    }

    @Test
    fun `yearly payment needs only two occurrences`() {
        val txs = listOf(
            tx("Domain Registrar", LocalDate.of(2024, 12, 10), 1200L),
            tx("Domain Registrar", LocalDate.of(2025, 12, 10), 1200L)
        )
        val c = RecurringDetector.detect(txs, today, zone).single()
        assertEquals(RecurringFrequency.YEARLY, c.frequency)
        assertEquals(2, c.occurrences)
        assertEquals(LocalDate.of(2026, 12, 10), c.nextDate)
    }

    @Test
    fun `two monthly occurrences are not enough`() {
        val txs = listOf(
            tx("Gym", LocalDate.of(2026, 9, 15)),
            tx("Gym", LocalDate.of(2026, 10, 15))
        )
        assertTrue(RecurringDetector.detect(txs, today, zone).isEmpty())
    }

    @Test
    fun `irregular merchant is not detected`() {
        val txs = listOf(
            tx("Corner Shop", LocalDate.of(2026, 8, 1)),
            tx("Corner Shop", LocalDate.of(2026, 8, 11)),
            tx("Corner Shop", LocalDate.of(2026, 9, 30)),
            tx("Corner Shop", LocalDate.of(2026, 10, 23))
        )
        assertTrue(RecurringDetector.detect(txs, today, zone).isEmpty())
    }

    @Test
    fun `stale series is not detected`() {
        val txs = listOf(
            tx("Old Magazine", LocalDate.of(2026, 1, 15)),
            tx("Old Magazine", LocalDate.of(2026, 2, 15)),
            tx("Old Magazine", LocalDate.of(2026, 3, 15))
        )
        assertTrue(RecurringDetector.detect(txs, today, zone).isEmpty())
    }

    @Test
    fun `varying amounts set the flag and use the median`() {
        val txs = listOf(
            tx("Energy Co", LocalDate.of(2026, 8, 20), 1000L),
            tx("Energy Co", LocalDate.of(2026, 9, 20), 1300L),
            tx("Energy Co", LocalDate.of(2026, 10, 20), 1100L)
        )
        val c = RecurringDetector.detect(txs, today, zone).single()
        assertTrue(c.amountVaries)
        assertEquals(1100L, c.typicalAmountMinor)
    }

    @Test
    fun `amounts within ten percent do not vary`() {
        val txs = listOf(
            tx("Phone Co", LocalDate.of(2026, 8, 20), 2000L),
            tx("Phone Co", LocalDate.of(2026, 9, 20), 2100L),
            tx("Phone Co", LocalDate.of(2026, 10, 20), 2200L)
        )
        assertFalse(RecurringDetector.detect(txs, today, zone).single().amountVaries)
    }

    @Test
    fun `merchantKey normalises case spaces and reference tails`() {
        assertEquals("netflix.com", RecurringDetector.merchantKey("Netflix.com 1234"))
        assertEquals("netflix.com", RecurringDetector.merchantKey("  NETFLIX.COM   "))
        assertEquals("tfl travel ch", RecurringDetector.merchantKey("TFL TRAVEL CH*82"))
        assertEquals("spotify", RecurringDetector.merchantKey("Spotify #4471"))
        assertEquals("amazon prime", RecurringDetector.merchantKey("Amazon   Prime"))
        assertEquals("2024", RecurringDetector.merchantKey("2024"))
    }

    @Test
    fun `merchant variants with reference numbers group together`() {
        val txs = listOf(
            tx("Netflix.com 1111", LocalDate.of(2026, 8, 15)),
            tx("NETFLIX.COM 2222", LocalDate.of(2026, 9, 15)),
            tx("Netflix.com 3333", LocalDate.of(2026, 10, 15))
        )
        assertEquals(1, RecurringDetector.detect(txs, today, zone).size)
    }

    @Test
    fun `income and expense with the same merchant stay separate`() {
        val dates = listOf(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25))
        val txs = dates.map { tx("Acme", it, 300000L, TransactionType.INCOME) } +
            dates.map { tx("Acme", it, 5000L, TransactionType.EXPENSE) }
        val result = RecurringDetector.detect(txs, today, zone)
        assertEquals(2, result.size)
        assertEquals(1, result.count { it.isIncome })
        assertEquals(300000L, result.first { it.isIncome }.typicalAmountMinor)
        assertEquals(5000L, result.first { !it.isIncome }.typicalAmountMinor)
    }

    @Test
    fun `excluded and merchantless transactions are ignored`() {
        val dates = listOf(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15))
        val excluded = dates.map { tx("Rent Share", it, excluded = true) }
        val unknown = dates.map { tx(null, it) }
        assertTrue(RecurringDetector.detect(excluded + unknown, today, zone).isEmpty())
    }

    @Test
    fun `nextDate steps from the anchor and clamps month ends`() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), RecurringDetector.nextDate(anchor, RecurringFrequency.MONTHLY, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), RecurringDetector.nextDate(anchor, RecurringFrequency.MONTHLY, LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 4, 30), RecurringDetector.nextDate(anchor, RecurringFrequency.MONTHLY, LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `nextDate returns the anchor itself when it is on or after the target`() {
        val anchor = LocalDate.of(2026, 11, 6)
        assertEquals(anchor, RecurringDetector.nextDate(anchor, RecurringFrequency.WEEKLY, LocalDate.of(2026, 11, 6)))
        assertEquals(LocalDate.of(2026, 11, 13), RecurringDetector.nextDate(anchor, RecurringFrequency.WEEKLY, LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun `nextDate handles every frequency`() {
        val anchor = LocalDate.of(2026, 1, 10)
        val target = LocalDate.of(2026, 11, 6)
        assertEquals(LocalDate.of(2026, 11, 7), RecurringDetector.nextDate(anchor, RecurringFrequency.WEEKLY, target))
        assertEquals(LocalDate.of(2026, 11, 14), RecurringDetector.nextDate(anchor, RecurringFrequency.BIWEEKLY, target))
        assertEquals(LocalDate.of(2026, 11, 10), RecurringDetector.nextDate(anchor, RecurringFrequency.MONTHLY, target))
        assertEquals(LocalDate.of(2027, 1, 10), RecurringDetector.nextDate(anchor, RecurringFrequency.QUARTERLY, target))
        assertEquals(LocalDate.of(2027, 1, 10), RecurringDetector.nextDate(anchor, RecurringFrequency.YEARLY, target))
    }

    @Test
    fun `occurrence statuses cover paid different amount upcoming and overdue`() {
        val items = listOf(
            item(1L, "paid", LocalDate.of(2026, 10, 3), 1000L),
            item(2L, "diff", LocalDate.of(2026, 10, 4), 1000L),
            item(3L, "soon", LocalDate.of(2026, 10, 20), 1000L),
            item(4L, "late", LocalDate.of(2026, 10, 1), 1000L)
        )
        val txs = listOf(
            tx("Paid", LocalDate.of(2026, 11, 3), 1020L),
            tx("Diff", LocalDate.of(2026, 11, 5), 1500L)
        )
        val result = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 11), txs, today, zone)
        assertEquals(4, result.size)
        val byId = result.associateBy { it.itemId }
        assertEquals(OccurrenceStatus.PAID, byId.getValue(1L).status)
        assertEquals(1020L, byId.getValue(1L).actualAmountMinor)
        assertEquals(OccurrenceStatus.PAID_DIFFERENT_AMOUNT, byId.getValue(2L).status)
        assertEquals(OccurrenceStatus.UPCOMING, byId.getValue(3L).status)
        assertNull(byId.getValue(3L).actualAmountMinor)
        assertEquals(OccurrenceStatus.OVERDUE, byId.getValue(4L).status)
        assertEquals(listOf(4L, 1L, 2L, 3L), result.map { it.itemId })
    }

    @Test
    fun `occurrences ignore stopped items and wrong direction payments`() {
        val items = listOf(
            item(1L, "gym", LocalDate.of(2026, 10, 15), status = "IGNORED"),
            item(2L, "acme", LocalDate.of(2026, 10, 3), 5000L)
        )
        val txs = listOf(tx("Acme", LocalDate.of(2026, 11, 3), 5000L, TransactionType.INCOME))
        val result = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 11), txs, today, zone)
        assertEquals(1, result.size)
        assertEquals(OccurrenceStatus.OVERDUE, result.single().status)
    }

    @Test
    fun `weekly matching window is two days`() {
        val items = listOf(item(1L, "cafe", LocalDate.of(2026, 10, 5), frequency = RecurringFrequency.WEEKLY))
        val txs = listOf(tx("Cafe", LocalDate.of(2026, 11, 5)))
        val result = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 11), txs, today, zone)
        // Mondays: 2, 9, 16, 23, 30. The Thursday payment is 3 days from Mon 2 and 4 from Mon 9: no match.
        assertEquals(5, result.size)
        assertTrue(result.none { it.actualAmountMinor != null })
    }

    @Test
    fun `month end clamping applies to occurrences`() {
        val items = listOf(item(1L, "rent", LocalDate.of(2026, 1, 31), 90000L))
        val feb = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 2), emptyList(), today, zone)
        assertEquals(1, feb.size)
        assertEquals(LocalDate.of(2026, 2, 28), feb.single().expectedDate)
        val mar = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 3), emptyList(), today, zone)
        assertEquals(LocalDate.of(2026, 3, 31), mar.single().expectedDate)
    }

    @Test
    fun `months before the anchor only list matched payments`() {
        val items = listOf(item(1L, "gym", LocalDate.of(2026, 10, 15)))
        val txs = listOf(tx("Gym", LocalDate.of(2026, 9, 15)))
        val sep = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 9), txs, today, zone)
        assertEquals(OccurrenceStatus.PAID, sep.single().status)
        val aug = RecurringDetector.occurrencesInMonth(items, YearMonth.of(2026, 8), txs, today, zone)
        assertTrue(aug.isEmpty())
        assertNotNull(sep.single().actualAmountMinor)
    }
}
