package com.spendtracker.app.domain.recurring

import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class RecurringFrequency(val label: String, val days: Int) {
    WEEKLY("Weekly", 7),
    BIWEEKLY("Every 2 weeks", 14),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 91),
    YEARLY("Yearly", 365)
}

data class RecurringCandidate(
    val merchantKey: String,
    val displayName: String,
    val categoryKey: String?,
    val typicalAmountMinor: Long,
    val frequency: RecurringFrequency,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val occurrences: Int,
    val amountVaries: Boolean,
    val isIncome: Boolean
)

enum class OccurrenceStatus { PAID, PAID_DIFFERENT_AMOUNT, UPCOMING, OVERDUE }

data class RecurringOccurrence(
    val itemId: Long,
    val displayName: String,
    val categoryKey: String?,
    val expectedDate: LocalDate,
    val expectedAmountMinor: Long,
    val actualAmountMinor: Long?,
    val status: OccurrenceStatus,
    val isIncome: Boolean
)

object RecurringDetector {

    private const val STATUS_APPROVED: String = "APPROVED"

    /** Gap tolerance (inclusive, in days) for each frequency. */
    private fun tolerance(frequency: RecurringFrequency): IntRange = when (frequency) {
        RecurringFrequency.WEEKLY -> 6..8
        RecurringFrequency.BIWEEKLY -> 13..16
        RecurringFrequency.MONTHLY -> 26..35
        RecurringFrequency.QUARTERLY -> 85..98
        RecurringFrequency.YEARLY -> 350..380
    }

    private fun isNoiseToken(token: String): Boolean =
        token.isNotEmpty() && token.all { it.isDigit() || it == '#' || it == '*' || it == '-' || it == '/' || it == '.' }

    /**
     * Lowercase, trimmed, whitespace-collapsed merchant name with trailing reference tokens
     * (digits, `#`, `*`) removed: "Netflix.com 1234" -> "netflix.com", "TFL TRAVEL CH*82" -> "tfl travel ch".
     */
    fun merchantKey(merchant: String): String {
        val normalised: String = merchant.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalised.isEmpty()) return normalised
        val tokens: MutableList<String> = normalised.split(' ').toMutableList()
        while (tokens.size > 1 && isNoiseToken(tokens.last())) {
            tokens.removeAt(tokens.lastIndex)
        }
        val last: String = tokens.last()
        val cut: Int = last.indexOfFirst { it == '*' || it == '#' }
        if (cut > 0) {
            tokens[tokens.lastIndex] = last.substring(0, cut).trimEnd('-', '/', '.', ' ')
        }
        val result: String = tokens.filter { it.isNotEmpty() }.joinToString(" ")
        return result.ifEmpty { normalised }
    }

    fun detect(transactions: List<TransactionItem>, today: LocalDate, zone: ZoneId): List<RecurringCandidate> {
        val grouped: Map<Pair<String, Boolean>, List<TransactionItem>> = transactions
            .asSequence()
            .filter { !it.excludeFromSpending }
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
            .filter { !it.merchant.isNullOrBlank() }
            .groupBy { merchantKey(it.merchant.orEmpty()) to (it.type == TransactionType.INCOME) }

        val result: MutableList<RecurringCandidate> = mutableListOf()
        for ((groupKey, items) in grouped) {
            val key: String = groupKey.first
            if (key.isEmpty()) continue
            val isIncome: Boolean = groupKey.second
            // One occurrence per calendar day.
            val byDay: List<Pair<LocalDate, TransactionItem>> = items
                .sortedBy { it.timestamp }
                .map { toDate(it.timestamp, zone) to it }
                .distinctBy { it.first }
            if (byDay.size < 2) continue

            val dates: List<LocalDate> = byDay.map { it.first }
            val gaps: List<Long> = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
            val medianGap: Double = median(gaps.map { it.toDouble() })
            val frequency: RecurringFrequency = RecurringFrequency.values().firstOrNull {
                val range: IntRange = tolerance(it)
                medianGap >= range.first && medianGap <= range.last
            } ?: continue

            val needed: Int = if (frequency == RecurringFrequency.YEARLY) 2 else 3
            if (byDay.size < needed) continue

            val range: IntRange = tolerance(frequency)
            val inTolerance: Int = gaps.count { it >= range.first && it <= range.last }
            if (inTolerance.toDouble() / gaps.size < 0.70) continue

            val lastDate: LocalDate = dates.last()
            if (ChronoUnit.DAYS.between(lastDate, today) > 2L * frequency.days) continue

            val amounts: List<Long> = byDay.map { it.second.amountMinor }
            val typical: Long = median(amounts.map { it.toDouble() }).toLong()
            val minAmount: Long = amounts.minOrNull() ?: 0L
            val maxAmount: Long = amounts.maxOrNull() ?: 0L
            val varies: Boolean = minAmount > 0 && maxAmount.toDouble() / minAmount > 1.10

            val latest: TransactionItem = byDay.last().second
            val next: LocalDate = nextDate(lastDate, frequency, maxOf(today, lastDate.plusDays(1)))
            result += RecurringCandidate(
                merchantKey = key,
                displayName = latest.merchant.orEmpty().trim(),
                categoryKey = latest.categoryKey,
                typicalAmountMinor = typical,
                frequency = frequency,
                lastDate = lastDate,
                nextDate = next,
                occurrences = byDay.size,
                amountVaries = varies,
                isIncome = isIncome
            )
        }
        return result.sortedWith(compareBy<RecurringCandidate> { it.nextDate }.thenBy { it.displayName })
    }

    /** First occurrence of the series started at [anchor] that falls on or after [onOrAfter]. */
    fun nextDate(anchor: LocalDate, frequency: RecurringFrequency, onOrAfter: LocalDate): LocalDate {
        var n: Long = firstIndexOnOrAfter(anchor, frequency, onOrAfter)
        if (n < 0L) n = 0L
        var candidate: LocalDate = stepDate(anchor, frequency, n)
        while (candidate.isBefore(onOrAfter)) {
            n++
            candidate = stepDate(anchor, frequency, n)
        }
        return candidate
    }

    /**
     * Expected occurrences of every APPROVED item that fall in [month], with their payment status
     * measured against [transactions]. Dates before an item's anchor are only listed when a matching
     * payment exists (so months before tracking began do not show phantom overdue payments).
     */
    fun occurrencesInMonth(
        items: List<RecurringItemEntity>,
        month: YearMonth,
        transactions: List<TransactionItem>,
        today: LocalDate,
        zone: ZoneId
    ): List<RecurringOccurrence> {
        val first: LocalDate = month.atDay(1)
        val last: LocalDate = month.atEndOfMonth()

        // Index payments by (merchantKey, direction) once.
        val payments: Map<Pair<String, Boolean>, List<Pair<LocalDate, TransactionItem>>> = transactions
            .asSequence()
            .filter { !it.excludeFromSpending }
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
            .filter { !it.merchant.isNullOrBlank() }
            .map { Triple(merchantKey(it.merchant.orEmpty()), it.type == TransactionType.INCOME, it) }
            .groupBy({ it.first to it.second }, { toDate(it.third.timestamp, zone) to it.third })

        val result: MutableList<RecurringOccurrence> = mutableListOf()
        for (item in items) {
            if (item.status != STATUS_APPROVED) continue
            val frequency: RecurringFrequency = parseFrequency(item.frequency)
            val anchor: LocalDate = toDate(item.anchorDate, zone)
            val candidates: List<Pair<LocalDate, TransactionItem>> = payments[item.merchantKey to item.isIncome].orEmpty()
            val window: Long = if (frequency == RecurringFrequency.WEEKLY) 2L else 4L

            var n: Long = firstIndexOnOrAfter(anchor, frequency, first)
            var expected: LocalDate = stepDate(anchor, frequency, n)
            while (expected.isBefore(first)) {
                n++
                expected = stepDate(anchor, frequency, n)
            }
            while (!expected.isAfter(last)) {
                val match: Pair<LocalDate, TransactionItem>? = candidates
                    .filter { abs(ChronoUnit.DAYS.between(expected, it.first)) <= window }
                    .minWithOrNull(
                        compareBy<Pair<LocalDate, TransactionItem>> { abs(ChronoUnit.DAYS.between(expected, it.first)) }
                            .thenBy { it.first }
                    )
                if (match != null || !expected.isBefore(anchor)) {
                    val actual: Long? = match?.second?.amountMinor
                    val status: OccurrenceStatus = when {
                        actual != null -> {
                            val tolerance: Long = maxOf(item.amountMinor / 20, 100L)
                            if (abs(actual - item.amountMinor) > tolerance) {
                                OccurrenceStatus.PAID_DIFFERENT_AMOUNT
                            } else {
                                OccurrenceStatus.PAID
                            }
                        }
                        expected.isBefore(today) -> OccurrenceStatus.OVERDUE
                        else -> OccurrenceStatus.UPCOMING
                    }
                    result += RecurringOccurrence(
                        itemId = item.id,
                        displayName = item.displayName,
                        categoryKey = item.categoryKey,
                        expectedDate = expected,
                        expectedAmountMinor = item.amountMinor,
                        actualAmountMinor = actual,
                        status = status,
                        isIncome = item.isIncome
                    )
                }
                n++
                expected = stepDate(anchor, frequency, n)
            }
        }
        return result.sortedWith(compareBy<RecurringOccurrence> { it.expectedDate }.thenBy { it.displayName })
    }

    fun parseFrequency(name: String): RecurringFrequency =
        RecurringFrequency.values().firstOrNull { it.name == name } ?: RecurringFrequency.MONTHLY

    // ---- helpers -------------------------------------------------------------------------

    private fun toDate(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted: List<Double> = values.sorted()
        val mid: Int = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** The n-th occurrence counted from [anchor]; always computed from the anchor so day-of-month clamping never drifts. */
    private fun stepDate(anchor: LocalDate, frequency: RecurringFrequency, n: Long): LocalDate = when (frequency) {
        RecurringFrequency.WEEKLY -> anchor.plusWeeks(n)
        RecurringFrequency.BIWEEKLY -> anchor.plusWeeks(2L * n)
        RecurringFrequency.MONTHLY -> anchor.plusMonths(n)
        RecurringFrequency.QUARTERLY -> anchor.plusMonths(3L * n)
        RecurringFrequency.YEARLY -> anchor.plusYears(n)
    }

    /** A safe under-estimate of the index of the first occurrence on or after [target] (may be negative). */
    private fun firstIndexOnOrAfter(anchor: LocalDate, frequency: RecurringFrequency, target: LocalDate): Long {
        val units: Long = when (frequency) {
            RecurringFrequency.WEEKLY -> ChronoUnit.WEEKS.between(anchor, target)
            RecurringFrequency.BIWEEKLY -> ChronoUnit.WEEKS.between(anchor, target) / 2L
            RecurringFrequency.MONTHLY -> ChronoUnit.MONTHS.between(anchor, target)
            RecurringFrequency.QUARTERLY -> ChronoUnit.MONTHS.between(anchor, target) / 3L
            RecurringFrequency.YEARLY -> ChronoUnit.YEARS.between(anchor, target)
        }
        return units - 1L
    }
}
