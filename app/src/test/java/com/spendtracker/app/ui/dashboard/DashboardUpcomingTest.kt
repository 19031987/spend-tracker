package com.spendtracker.app.ui.dashboard

import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** D24: last month's unpaid payment must not vanish from Home on the 1st. */
class DashboardUpcomingTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun item(id: Long, key: String, anchor: LocalDate) = RecurringItemEntity(
        id = id,
        merchantKey = key,
        displayName = key,
        categoryKey = "SUBSCRIPTIONS",
        amountMinor = 1000L,
        frequency = RecurringFrequency.MONTHLY.name,
        anchorDate = anchor.atStartOfDay(zone).toInstant().toEpochMilli(),
        status = "APPROVED",
        isIncome = false,
        createdAt = 0L
    )

    @Test
    fun `overdue payment from last month is still listed on the 1st`() {
        val items = listOf(item(1L, "rent", LocalDate.of(2026, 8, 20)), item(2L, "gym", LocalDate.of(2026, 8, 5)))
        val today = LocalDate.of(2026, 12, 1)
        val upcoming = DashboardViewModel.upcomingOccurrences(items, emptyList(), today, zone, limit = 10)
        val rent = upcoming.first { it.displayName == "rent" }
        assertEquals(LocalDate.of(2026, 11, 20), rent.expectedDate)
        assertEquals(OccurrenceStatus.OVERDUE, rent.status)
        // Gym: 5 Nov overdue, and 5 Dec is due within the week.
        assertTrue(upcoming.any { it.displayName == "gym" && it.expectedDate == LocalDate.of(2026, 12, 5) })
        assertTrue(upcoming.any { it.displayName == "gym" && it.status == OccurrenceStatus.OVERDUE })
        // Nothing beyond the 7-day window.
        assertTrue(upcoming.none { it.expectedDate.isAfter(today.plusDays(7)) })
    }
}
