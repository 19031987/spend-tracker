package com.spendtracker.app.domain

import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

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

/** Income excludes refunds (see the refund rule below): a refund is negative spending, not income. */
fun TransactionItem.isIncome(): Boolean =
    type == TransactionType.INCOME && !excludeFromSpending && !isRefund()

/** The locale's first day of the week; the single source used by the repository and every screen. */
fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

// ---- Refunds ------------------------------------------------------------------------------
//
// Rule (D22): a refund is money IN that gives back earlier spending. It is NOT income: it is
// negative spending. An INCOME transaction is a refund when
//   * its category is a refund category (key contains "REFUND" or name contains "refund"), or
//   * it has no specific income category (null / OTHER_INCOME) and its merchant or note says
//     refund / refunded / chargeback / reversal (the listener stores the alert text in the note).
// Refunds reduce total spend. They reduce a category's spend only when that category is known:
// the category of the most recent spending at the same merchant, on or before the refund
// (see [RefundAttribution]). Category amounts never go below zero.

private val REFUND_TEXT: Regex =
    Regex("\\b(?:refund(?:ed|s)?|chargeback|charge back|reversal|reversed)\\b", RegexOption.IGNORE_CASE)

// Tax and government repayments are income, not negative spending, even when the alert says "refund".
private val GOVERNMENT_REFUND_TEXT: Regex =
    Regex("\\b(?:hmrc|tax refund|council tax|dwp|student loans?)\\b", RegexOption.IGNORE_CASE)

private const val OTHER_INCOME_KEY: String = "OTHER_INCOME"
const val UNCATEGORISED_EXPENSE_KEY: String = "OTHER_EXPENSE"

/** Shared refund test for both [TransactionItem] and raw `TransactionEntity` rows. */
fun isRefundLike(
    type: TransactionType,
    excludeFromSpending: Boolean,
    categoryKey: String?,
    categoryName: String?,
    merchant: String?,
    note: String?
): Boolean {
    if (type != TransactionType.INCOME || excludeFromSpending) return false
    if (categoryKey?.contains("REFUND", ignoreCase = true) == true) return true
    if (categoryName?.contains("refund", ignoreCase = true) == true) return true
    if (categoryKey != null && categoryKey != OTHER_INCOME_KEY) return false
    if (GOVERNMENT_REFUND_TEXT.containsMatchIn(merchant.orEmpty()) || GOVERNMENT_REFUND_TEXT.containsMatchIn(note.orEmpty())) {
        return false
    }
    return REFUND_TEXT.containsMatchIn(merchant.orEmpty()) || REFUND_TEXT.containsMatchIn(note.orEmpty())
}

fun TransactionItem.isRefund(): Boolean =
    isRefundLike(type, excludeFromSpending, categoryKey, categoryName, merchant, note)

/** Signed contribution to spending: +amount for spending, -amount for a refund, 0 otherwise. */
fun TransactionItem.netSpendMinor(): Long = when {
    isSpending() -> amountMinor
    isRefund() -> -amountMinor
    else -> 0L
}

/**
 * Finds the spending category a refund belongs to: the category of the most recent spending at the
 * same merchant (case-insensitive, trimmed) on or before the refund. Build once per list.
 */
class RefundAttribution private constructor(
    private val byMerchant: Map<String, List<Pair<Long, String>>>
) {
    fun categoryFor(merchant: String?, timestamp: Long): String? {
        val key: String = merchantKey(merchant) ?: return null
        val history: List<Pair<Long, String>> = byMerchant[key] ?: return null
        return history.lastOrNull { it.first <= timestamp }?.second
    }

    companion object {
        private fun merchantKey(merchant: String?): String? =
            merchant?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        /** [spending] entries are (merchant, timestamp, categoryKey) of spending transactions only. */
        fun build(spending: List<Triple<String?, Long, String?>>): RefundAttribution {
            val map: MutableMap<String, MutableList<Pair<Long, String>>> = HashMap()
            for ((merchant, timestamp, category) in spending) {
                val key: String = merchantKey(merchant) ?: continue
                map.getOrPut(key) { ArrayList() } += timestamp to (category ?: UNCATEGORISED_EXPENSE_KEY)
            }
            map.values.forEach { list -> list.sortBy { it.first } }
            return RefundAttribution(map)
        }

        fun of(transactions: List<TransactionItem>): RefundAttribution = build(
            transactions.filter { it.isSpending() }.map { Triple(it.merchant, it.timestamp, it.categoryKey) }
        )
    }
}

/**
 * The spending category [this] counts towards: its own category (or OTHER_EXPENSE) for spending,
 * the attributed category for a refund (null when unknown), null for anything else.
 */
fun TransactionItem.spendCategoryKey(attribution: RefundAttribution): String? = when {
    isSpending() -> categoryKey ?: UNCATEGORISED_EXPENSE_KEY
    isRefund() -> attribution.categoryFor(merchant, timestamp)
    else -> null
}

// ---- The same rules for raw Room rows (Analytics / Comparison read TransactionEntity) --------

fun TransactionEntity.isSpending(): Boolean =
    type == TransactionType.EXPENSE && !excludeFromSpending && category != "INTERNAL_TRANSFER"

fun TransactionEntity.isRefund(): Boolean =
    isRefundLike(type, excludeFromSpending, category, null, merchant, note)

fun TransactionEntity.isIncome(): Boolean =
    type == TransactionType.INCOME && !excludeFromSpending && !isRefund()

/** Signed contribution to spending in positive minor units: +spend, -refund, 0 otherwise. */
fun TransactionEntity.netSpendMinor(): Long = when {
    isSpending() -> abs(amount)
    isRefund() -> -abs(amount)
    else -> 0L
}

fun refundAttributionOfEntities(transactions: List<TransactionEntity>): RefundAttribution =
    RefundAttribution.build(
        transactions.filter { it.isSpending() }.map { Triple(it.merchant, it.timestamp, it.category) }
    )

/** Spending category of a raw row: its own for spending, the attributed one for a refund, else null. */
fun TransactionEntity.spendCategoryKey(attribution: RefundAttribution): String? = when {
    isSpending() -> category ?: UNCATEGORISED_EXPENSE_KEY
    isRefund() -> attribution.categoryFor(merchant, timestamp)
    else -> null
}

/** Net spending (spend minus refunds, never below zero) of rows with timestamps in [range]. */
fun netSpendInRange(transactions: List<TransactionEntity>, range: TimeRange): Long =
    maxOf(0L, transactions.filter { it.timestamp >= range.start && it.timestamp < range.end }.sumOf { it.netSpendMinor() })
