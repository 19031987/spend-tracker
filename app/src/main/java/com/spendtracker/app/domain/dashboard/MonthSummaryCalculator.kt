package com.spendtracker.app.domain.dashboard

import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.isSpending
import com.spendtracker.app.domain.monthOf
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

    private const val UNCATEGORISED_KEY: String = "OTHER_EXPENSE"

    fun summarize(transactions: List<TransactionItem>, month: YearMonth, zone: ZoneId): MonthSummary {
        var spent = 0L
        var income = 0L
        var priorSpent = 0L
        var priorIncome = 0L
        for (tx in transactions) {
            val txMonth: YearMonth = monthOf(tx.timestamp, zone)
            if (txMonth == month) {
                if (tx.isSpending()) spent += tx.amountMinor
                if (tx.isIncome()) income += tx.amountMinor
            } else if (txMonth.isBefore(month)) {
                if (tx.isSpending()) priorSpent += tx.amountMinor
                if (tx.isIncome()) priorIncome += tx.amountMinor
            }
        }
        return MonthSummary(
            month = month,
            spentMinor = spent,
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
        val spending: List<TransactionItem> = transactions.filter {
            it.isSpending() && monthOf(it.timestamp, zone) == month
        }
        val total: Long = spending.sumOf { it.amountMinor }
        if (total <= 0L || limit <= 0) return emptyList()
        return spending
            .groupBy { it.categoryKey ?: UNCATEGORISED_KEY }
            .map { (key, items) ->
                val first: TransactionItem = items.first()
                val amount: Long = items.sumOf { it.amountMinor }
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
