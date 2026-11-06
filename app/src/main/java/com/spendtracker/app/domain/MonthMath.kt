package com.spendtracker.app.domain

import com.spendtracker.app.data.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [1st 00:00, next 1st 00:00) of [month] in [zone], as epoch millis. */
fun monthRange(month: YearMonth, zone: ZoneId): TimeRange {
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return TimeRange(start, end)
}

fun monthOf(timestamp: Long, zone: ZoneId): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(zone))

/** 0 before [month], 1 after it, otherwise the fraction elapsed at the middle of [today]. */
fun monthProgress(month: YearMonth, today: LocalDate): Float {
    val current = YearMonth.from(today)
    return when {
        current.isBefore(month) -> 0f
        current.isAfter(month) -> 1f
        else -> (today.dayOfMonth - 1 + 0.5f) / month.lengthOfMonth()
    }
}

fun TransactionItem.isSpending(): Boolean =
    type == TransactionType.EXPENSE && !excludeFromSpending && categoryKey != "INTERNAL_TRANSFER"

fun TransactionItem.isIncome(): Boolean =
    type == TransactionType.INCOME && !excludeFromSpending
