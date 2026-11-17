package com.spendtracker.app.domain.dashboard

import com.spendtracker.app.domain.RefundAttribution
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.isSpending
import com.spendtracker.app.domain.monthOf
import com.spendtracker.app.domain.netSpendMinor
import com.spendtracker.app.domain.spendCategoryKey
import java.time.YearMonth
import java.time.ZoneId

data class MonthSummary(
    val month: YearMonth,
    val spentMinor: Long,
    val incomeMinor: Long,
    val carriedOverMinor: Long
) {
    val netRemainingMinor: Long get() = carriedOverMinor + incomeMinor - spentMinor
}

data class TopCategory(
    val categoryKey: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val amountMinor: Long,
    val share: Float
)

object MonthSummaryCalculator {

    fun summarize(transactions: List<TransactionItem>, month: YearMonth, zone: ZoneId): MonthSummary {
        var spent = 0L
        var income = 0L
        var priorSpent = 0L
        var priorIncome = 0L
        for (tx in transactions) {
            val txMonth: YearMonth = monthOf(tx.timestamp, zone)
            // Refunds are negative spending (netSpendMinor), never income.
            if (txMonth == month) {
                spent += tx.netSpendMinor()
                if (tx.isIncome()) income += tx.amountMinor
            } else if (txMonth.isBefore(month)) {
                priorSpent += tx.netSpendMinor()
                if (tx.isIncome()) priorIncome += tx.amountMinor
            }
        }
        return MonthSummary(
            month = month,
            spentMinor = maxOf(0L, spent),
            incomeMinor = income,
            carriedOverMinor = priorIncome - priorSpent
        )
    }

    /** Largest spending categories of [month], descending; share is the fraction of the month's total spend. */
    fun topCategories(
        transactions: List<TransactionItem>,
        month: YearMonth,
        zone: ZoneId,
        limit: Int = 5
    ): List<TopCategory> {
        if (limit <= 0) return emptyList()
        val attribution: RefundAttribution = RefundAttribution.of(transactions)
        val inMonth: List<TransactionItem> = transactions.filter { monthOf(it.timestamp, zone) == month }
        // Net per category: spending minus refunds attributed to that category, never below zero.
        val amounts: MutableMap<String, Long> = LinkedHashMap()
        val labels: MutableMap<String, TransactionItem> = HashMap()
        for (tx in inMonth) {
            val key: String = tx.spendCategoryKey(attribution) ?: continue
            amounts[key] = (amounts[key] ?: 0L) + tx.netSpendMinor()
            if (tx.isSpending() && key !in labels) labels[key] = tx
        }
        val positive: Map<String, Long> = amounts.filter { (key, amount) -> amount > 0L && key in labels }
        val total: Long = positive.values.sum()
        if (total <= 0L) return emptyList()
        return positive
            .map { (key, amount) ->
                val first: TransactionItem = labels.getValue(key)
                TopCategory(
                    categoryKey = key,
                    name = first.categoryName,
                    emoji = first.categoryEmoji,
                    colorHex = first.categoryColorHex,
                    amountMinor = amount,
                    share = amount.toFloat() / total.toFloat()
                )
            }
            .sortedWith(compareByDescending<TopCategory> { it.amountMinor }.thenBy { it.name })
            .take(limit)
    }
}
