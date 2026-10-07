package com.spendtracker.app.domain

import com.spendtracker.app.data.BucketTotal
import com.spendtracker.app.data.TransactionCategory
import com.spendtracker.app.data.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class TimeRange(val start: Long, val end: Long)

data class PeriodWindow(val period: Period, val startDate: LocalDate, val endExclusive: LocalDate) {
    fun toRange(zone: ZoneId) = TimeRange(
        start = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    )
}

object PeriodCalculator {
    fun current(period: Period, today: LocalDate, firstDay: DayOfWeek): PeriodWindow = when (period) {
        Period.WEEK -> {
            val s = today.with(TemporalAdjusters.previousOrSame(firstDay))
            PeriodWindow(period, s, s.plusDays(7))
        }
        Period.MONTH -> {
            val s = today.withDayOfMonth(1)
            PeriodWindow(period, s, s.plusMonths(1))
        }
        Period.YEAR -> {
            val s = today.withDayOfYear(1)
            PeriodWindow(period, s, s.plusYears(1))
        }
    }

    fun previous(w: PeriodWindow): PeriodWindow = when (w.period) {
        Period.WEEK -> PeriodWindow(w.period, w.startDate.minusDays(7), w.startDate)
        Period.MONTH -> PeriodWindow(w.period, w.startDate.minusMonths(1), w.startDate)
        Period.YEAR -> PeriodWindow(w.period, w.startDate.minusYears(1), w.startDate)
    }
}

data class ChartBar(val label: String, val current: Long, val previous: Long)

data class CategoryShare(
    val category: TransactionCategory,
    val amountMinor: Long,
    val percent: Float
)

data class AnalyticsSnapshot(
    val period: Period,
    val currentTotal: Long,
    val previousTotal: Long,
    val bars: List<ChartBar>,
    val categories: List<CategoryShare>
)

sealed interface NewTransaction {
    /** EXPENSE or INCOME. [amountMinor] is always positive; the repository applies the sign. */
    data class Entry(
        val type: TransactionType,
        val accountId: Long,
        val amountMinor: Long,
        val category: TransactionCategory,
        val note: String?,
        val timestamp: Long
    ) : NewTransaction

    data class Transfer(
        val sourceAccountId: Long,
        val destinationAccountId: Long,
        val amountMinor: Long,
        val note: String?,
        val timestamp: Long
    ) : NewTransaction
}

object ChartBucketing {

    fun build(
        current: PeriodWindow,
        previous: PeriodWindow,
        currentRows: List<BucketTotal>,
        previousRows: List<BucketTotal>,
        locale: Locale
    ): List<ChartBar> {
        val count = when (current.period) {
            Period.WEEK -> 7
            Period.MONTH -> {
                val longest = maxOf(
                    current.startDate.lengthOfMonth(),
                    previous.startDate.lengthOfMonth()
                )
                (longest + 6) / 7
            }
            Period.YEAR -> 12
        }
        val cur = aggregate(current, currentRows, count)
        val prev = aggregate(previous, previousRows, count)
        return (0 until count).map { i ->
            ChartBar(label(current, i, locale), cur[i], prev[i])
        }
    }

    private fun aggregate(window: PeriodWindow, rows: List<BucketTotal>, count: Int): LongArray {
        val out = LongArray(count)
        rows.forEach { row ->
            val idx = index(window, row.bucket)
            if (idx in 0 until count) out[idx] += row.total
        }
        return out
    }

    private fun index(window: PeriodWindow, key: String): Int = runCatching {
        when (window.period) {
            Period.WEEK -> ChronoUnit.DAYS.between(window.startDate, LocalDate.parse(key)).toInt()
            Period.MONTH -> (LocalDate.parse(key).dayOfMonth - 1) / 7
            Period.YEAR -> YearMonth.parse(key).monthValue - 1
        }
    }.getOrDefault(0)

    private fun label(window: PeriodWindow, i: Int, locale: Locale): String = when (window.period) {
        Period.WEEK -> window.startDate.plusDays(i.toLong()).dayOfWeek
            .getDisplayName(TextStyle.SHORT, locale)
        Period.MONTH -> "W${i + 1}"
        Period.YEAR -> window.startDate.plusMonths(i.toLong()).month
            .getDisplayName(TextStyle.SHORT, locale)
    }
}
