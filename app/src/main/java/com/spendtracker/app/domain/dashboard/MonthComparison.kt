package com.spendtracker.app.domain.dashboard

import com.spendtracker.app.domain.RefundAttribution
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.isSpending
import com.spendtracker.app.domain.netSpendMinor
import com.spendtracker.app.domain.spendCategoryKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One category's spending this month-to-date against the same days of the previous month. */
data class CategoryChange(
    val categoryKey: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val currentMinor: Long,
    val previousMinor: Long
) {
    val deltaMinor: Long get() = currentMinor - previousMinor

    /** Fractional change (0.25 = +25 %), or null when there was nothing last month to compare with. */
    val changeFraction: Float? get() = if (previousMinor > 0L) deltaMinor.toFloat() / previousMinor else null
}

/**
 * Like-for-like comparison: days 1..[dayOfMonth] of [month] against days 1..[dayOfMonth] of
 * [previousMonth] (clamped to that month's length), so mid-month numbers are not compared with a
 * full previous month.
 */
data class MonthComparison(
    val month: YearMonth,
    val previousMonth: YearMonth,
    val dayOfMonth: Int,
    val spentMinor: Long,
    val previousSpentMinor: Long,
    val incomeMinor: Long,
    val previousIncomeMinor: Long,
    /** Categories where spending went up, largest increase first. */
    val increased: List<CategoryChange>,
    /** Categories where spending went down, largest decrease first. */
    val decreased: List<CategoryChange>
) {
    val spentDeltaMinor: Long get() = spentMinor - previousSpentMinor
    val incomeDeltaMinor: Long get() = incomeMinor - previousIncomeMinor
    val savedMinor: Long get() = incomeMinor - spentMinor
    val previousSavedMinor: Long get() = previousIncomeMinor - previousSpentMinor
    val savedDeltaMinor: Long get() = savedMinor - previousSavedMinor

    /** False when the previous period had no activity at all, so a comparison would be meaningless. */
    val hasPrevious: Boolean get() = previousSpentMinor > 0L || previousIncomeMinor > 0L
}

object MonthComparisonCalculator {

    /** Category moves smaller than this (in minor units, i.e. £1) are treated as unchanged. */
    const val MIN_CHANGE_MINOR: Long = 100L

    fun compare(
        transactions: List<TransactionItem>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        limit: Int = 3
    ): MonthComparison {
        val previousMonth: YearMonth = month.minusMonths(1)
        val day: Int = when {
            today.isBefore(month.atDay(1)) -> 0
            YearMonth.from(today) == month -> today.dayOfMonth
            else -> month.lengthOfMonth()
        }
        // A finished month is compared with the whole previous month.
        val previousDay: Int = if (day >= month.lengthOfMonth()) {
            previousMonth.lengthOfMonth()
        } else {
            minOf(day, previousMonth.lengthOfMonth())
        }

        var spent = 0L
        var previousSpent = 0L
        var income = 0L
        var previousIncome = 0L
        val current = LinkedHashMap<String, Long>()
        val previous = LinkedHashMap<String, Long>()
        val labels = HashMap<String, TransactionItem>()
        val attribution: RefundAttribution = RefundAttribution.of(transactions)

        for (tx in transactions) {
            val date: LocalDate = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
            val txMonth: YearMonth = YearMonth.from(date)
            val inCurrent: Boolean = txMonth == month && date.dayOfMonth <= day
            val inPrevious: Boolean = txMonth == previousMonth && date.dayOfMonth <= previousDay
            if (!inCurrent && !inPrevious) continue

            if (tx.isIncome()) {
                if (inCurrent) income += tx.amountMinor else previousIncome += tx.amountMinor
            }
            // Refunds are negative spending: they lower the total, and their category when known.
            val net: Long = tx.netSpendMinor()
            if (net == 0L) continue
            if (inCurrent) spent += net else previousSpent += net
            val key: String = tx.spendCategoryKey(attribution) ?: continue
            if (inCurrent) {
                current[key] = (current[key] ?: 0L) + net
                if (tx.isSpending()) labels[key] = tx
            } else {
                previous[key] = (previous[key] ?: 0L) + net
                if (tx.isSpending() && key !in labels) labels[key] = tx
            }
        }

        val changes: List<CategoryChange> = (current.keys + previous.keys).distinct().mapNotNull { key ->
            val label: TransactionItem = labels[key] ?: return@mapNotNull null
            CategoryChange(
                categoryKey = key,
                name = label.categoryName,
                emoji = label.categoryEmoji,
                colorHex = label.categoryColorHex,
                currentMinor = maxOf(0L, current[key] ?: 0L),
                previousMinor = maxOf(0L, previous[key] ?: 0L)
            )
        }

        return MonthComparison(
            month = month,
            previousMonth = previousMonth,
            dayOfMonth = day,
            spentMinor = maxOf(0L, spent),
            previousSpentMinor = maxOf(0L, previousSpent),
            incomeMinor = income,
            previousIncomeMinor = previousIncome,
            increased = changes
                .filter { it.deltaMinor >= MIN_CHANGE_MINOR }
                .sortedWith(compareByDescending<CategoryChange> { it.deltaMinor }.thenBy { it.name })
                .take(limit),
            decreased = changes
                .filter { it.deltaMinor <= -MIN_CHANGE_MINOR }
                .sortedWith(compareBy<CategoryChange> { it.deltaMinor }.thenBy { it.name })
                .take(limit)
        )
    }
}
