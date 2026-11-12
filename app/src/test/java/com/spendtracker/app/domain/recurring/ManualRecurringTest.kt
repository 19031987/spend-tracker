package com.spendtracker.app.domain.recurring

import com.spendtracker.app.data.RecurringItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ManualRecurringTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 11, 12)
    private val now: Long = 1_000_000L

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun draft(
        name: String = "Netflix",
        amount: Long = 1099L,
        isIncome: Boolean = false,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        date: LocalDate? = LocalDate.of(2026, 11, 20),
        categoryKey: String? = "SUBSCRIPTIONS"
    ): RecurringDraft = RecurringDraft(name, amount, isIncome, frequency, date, categoryKey)

    private fun stored(
        id: Long,
        key: String,
        name: String = key,
        status: String = "APPROVED",
        anchor: LocalDate = LocalDate.of(2026, 10, 1),
        createdAt: Long = 42L
    ): RecurringItemEntity = RecurringItemEntity(
        id = id,
        merchantKey = key,
        displayName = name,
        categoryKey = null,
        amountMinor = 500L,
        frequency = RecurringFrequency.WEEKLY.name,
        anchorDate = millis(anchor),
        status = status,
        isIncome = false,
        createdAt = createdAt
    )

    // ---- validate -----------------------------------------------------------------------

    @Test
    fun `validate accepts a name and a positive amount`() {
        val errors: RecurringFormErrors = ManualRecurring.validate("Rent", "950.00", 95000L)
        assertTrue(errors.isValid)
    }

    @Test
    fun `validate requires a name`() {
        assertEquals("Enter a name", ManualRecurring.validate("   ", "10", 1000L).name)
        assertEquals("Enter a name", ManualRecurring.validate("", "10", 1000L).name)
    }

    @Test
    fun `validate rejects very long names`() {
        val long: String = "x".repeat(ManualRecurring.MAX_NAME_LENGTH + 1)
        assertEquals("Keep the name under 60 characters", ManualRecurring.validate(long, "10", 1000L).name)
    }

    @Test
    fun `validate requires a parseable positive amount`() {
        assertEquals("Enter an amount", ManualRecurring.validate("Gym", " ", null).amount)
        assertEquals("Enter an amount like 9.99", ManualRecurring.validate("Gym", "abc", null).amount)
        assertEquals("Amount must be more than zero", ManualRecurring.validate("Gym", "0", 0L).amount)
        assertEquals("Amount must be more than zero", ManualRecurring.validate("Gym", "-5", -500L).amount)
        assertNull(ManualRecurring.validate("Gym", "25", 2500L).amount)
    }

    @Test
    fun `cleanName trims and collapses whitespace`() {
        assertEquals("Council Tax", ManualRecurring.cleanName("  Council   Tax "))
    }

    // ---- decideAdd ----------------------------------------------------------------------

    @Test
    fun `new item gets the detector merchant key and is approved`() {
        val decision = ManualRecurring.decideAdd(
            draft(name = " NETFLIX.COM 1234 ", date = LocalDate.of(2026, 11, 20)),
            existing = emptyList(), zone = zone, today = today, nowMillis = now
        )
        assertTrue(decision is RecurringSaveDecision.Insert)
        val entity: RecurringItemEntity = (decision as RecurringSaveDecision.Insert).entity
        assertEquals(RecurringDetector.merchantKey("NETFLIX.COM 1234"), entity.merchantKey)
        assertEquals("netflix.com", entity.merchantKey)
        assertEquals("NETFLIX.COM 1234", entity.displayName)
        assertEquals(0L, entity.id)
        assertEquals("APPROVED", entity.status)
        assertEquals("MONTHLY", entity.frequency)
        assertEquals(1099L, entity.amountMinor)
        assertEquals("SUBSCRIPTIONS", entity.categoryKey)
        assertEquals(millis(LocalDate.of(2026, 11, 20)), entity.anchorDate)
        assertEquals(now, entity.createdAt)
        assertEquals(false, entity.isIncome)
    }

    @Test
    fun `new item without a date is anchored today`() {
        val decision = ManualRecurring.decideAdd(draft(date = null), emptyList(), zone, today, now)
        assertEquals(millis(today), (decision as RecurringSaveDecision.Insert).entity.anchorDate)
    }

    @Test
    fun `adding a key that is already approved is rejected`() {
        val existing = listOf(stored(id = 7L, key = "netflix", name = "Netflix"))
        val decision = ManualRecurring.decideAdd(draft(name = "netflix"), existing, zone, today, now)
        assertEquals(RecurringSaveDecision.Reject("You already track Netflix"), decision)
    }

    @Test
    fun `adding a key that was ignored reuses that row`() {
        val existing = listOf(
            stored(id = 3L, key = "spotify"),
            stored(id = 9L, key = "netflix", name = "NETFLIX", status = "IGNORED", createdAt = 77L)
        )
        val decision = ManualRecurring.decideAdd(
            draft(name = "Netflix", isIncome = false, frequency = RecurringFrequency.YEARLY),
            existing, zone, today, now
        )
        assertTrue(decision is RecurringSaveDecision.Update)
        val entity: RecurringItemEntity = (decision as RecurringSaveDecision.Update).entity
        assertEquals(9L, entity.id)
        assertEquals(77L, entity.createdAt)
        assertEquals("APPROVED", entity.status)
        assertEquals("Netflix", entity.displayName)
        assertEquals("netflix", entity.merchantKey)
        assertEquals("YEARLY", entity.frequency)
        assertEquals(1099L, entity.amountMinor)
        assertEquals(millis(LocalDate.of(2026, 11, 20)), entity.anchorDate)
    }

    @Test
    fun `money in is stored as income`() {
        val decision = ManualRecurring.decideAdd(draft(name = "Salary", isIncome = true), emptyList(), zone, today, now)
        assertEquals(true, (decision as RecurringSaveDecision.Insert).entity.isIncome)
    }

    // ---- decideEdit ---------------------------------------------------------------------

    @Test
    fun `edit updates fields but keeps merchant key id status and createdAt`() {
        val existing = listOf(stored(id = 5L, key = "gym", name = "Gym", createdAt = 11L))
        val decision = ManualRecurring.decideEdit(
            id = 5L,
            draft = draft(name = "  My   PureGym ", amount = 2499L, isIncome = true, frequency = RecurringFrequency.MONTHLY,
                date = LocalDate.of(2026, 12, 1), categoryKey = "HEALTH"),
            existing = existing,
            zone = zone
        )
        val entity: RecurringItemEntity = (decision as RecurringSaveDecision.Update).entity
        assertEquals(5L, entity.id)
        assertEquals(11L, entity.createdAt)
        assertEquals("APPROVED", entity.status)
        assertEquals("gym", entity.merchantKey)
        assertEquals("My PureGym", entity.displayName)
        assertEquals(2499L, entity.amountMinor)
        assertEquals(true, entity.isIncome)
        assertEquals("MONTHLY", entity.frequency)
        assertEquals("HEALTH", entity.categoryKey)
        assertEquals(millis(LocalDate.of(2026, 12, 1)), entity.anchorDate)
    }

    @Test
    fun `edit without a new date keeps the stored anchor`() {
        val anchor: LocalDate = LocalDate.of(2026, 9, 3)
        val existing = listOf(stored(id = 5L, key = "gym", anchor = anchor))
        val decision = ManualRecurring.decideEdit(5L, draft(name = "Gym", date = null), existing, zone)
        assertEquals(millis(anchor), (decision as RecurringSaveDecision.Update).entity.anchorDate)
    }

    @Test
    fun `renaming to another row's name keeps own key and is allowed`() {
        val existing = listOf(
            stored(id = 1L, key = "netflix.com", name = "NETFLIX.COM 1234"),
            stored(id = 2L, key = "netflix", name = "Netflix", status = "IGNORED")
        )
        val decision = ManualRecurring.decideEdit(1L, draft(name = "Netflix"), existing, zone)
        val entity: RecurringItemEntity = (decision as RecurringSaveDecision.Update).entity
        assertEquals(1L, entity.id)
        assertEquals("netflix.com", entity.merchantKey)
        assertEquals("Netflix", entity.displayName)
    }

    @Test
    fun `edit with a blank name is rejected`() {
        val existing = listOf(stored(id = 5L, key = "gym"))
        assertTrue(ManualRecurring.decideEdit(5L, draft(name = "  "), existing, zone) is RecurringSaveDecision.Reject)
    }

    @Test
    fun `editing a missing row is rejected`() {
        val decision = ManualRecurring.decideEdit(99L, draft(), emptyList(), zone)
        assertTrue(decision is RecurringSaveDecision.Reject)
    }
}
