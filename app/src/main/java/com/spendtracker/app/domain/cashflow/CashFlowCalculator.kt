package com.spendtracker.app.domain.cashflow

import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.isSpending
import com.spendtracker.app.domain.monthOf
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class CashFlowMonth(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long) {
    val savingsMinor: Long get() = incomeMinor - expenseMinor
    val savingsRate: Float? get() = if (incomeMinor > 0) savingsMinor.toFloat() / incomeMinor else null
}

enum class BreakdownDimension { CATEGORY, GROUP, MERCHANT }

data class BreakdownRow(
    val key: String,
    val label: String,
    val emoji: String,
    val colorHex: String,
    val amountMinor: Long,
    val share: Float,
    val transactionCount: Int
)

data class CashFlowReport(
    val months: List<CashFlowMonth>,
    val selected: CashFlowMonth,
    val expenseBreakdown: List<BreakdownRow>,
    val incomeBreakdown: List<BreakdownRow>,
    val averageMonthlyExpenseMinor: Long,
    val averageMonthlyIncomeMinor: Long
)

object CashFlowCalculator {

    private const val UNKNOWN_MERCHANT_KEY = "unknown"
    private const val UNKNOWN_MERCHANT_LABEL = "Unknown vendor"
    private const val OTHER_KEY = "other"

    /** Oldest to newest, zero-filled, [count] months ending at [endMonth] (inclusive). */
    fun monthly(
        transactions: List<TransactionItem>,
        endMonth: YearMonth,
        count: Int,
        zone: ZoneId
    ): List<CashFlowMonth> {
        if (count <= 0) return emptyList()
        val start = endMonth.minusMonths((count - 1).toLong())
        val income = HashMap<YearMonth, Long>()
        val expense = HashMap<YearMonth, Long>()
        for (tx in transactions) {
            val spending = tx.isSpending()
            val inc = tx.isIncome()
            if (!spending && !inc) continue
            val m = monthOf(tx.timestamp, zone)
            if (m.isBefore(start) || m.isAfter(endMonth)) continue
            if (spending) expense[m] = (expense[m] ?: 0L) + tx.amountMinor
            else income[m] = (income[m] ?: 0L) + tx.amountMinor
        }
        return (0 until count).map { i ->
            val m = start.plusMonths(i.toLong())
            CashFlowMonth(m, income[m] ?: 0L, expense[m] ?: 0L)
        }
    }

    fun breakdown(
        transactions: List<TransactionItem>,
        month: YearMonth,
        dimension: BreakdownDimension,
        income: Boolean,
        categories: List<CategoryEntity>,
        groups: List<CategoryGroupEntity>,
        zone: ZoneId
    ): List<BreakdownRow> {
        val categoryByKey = categories.associateBy { it.key }
        val groupById = groups.associateBy { it.id }
        val acc = LinkedHashMap<String, Acc>()
        for (tx in matching(transactions, month, income, zone)) {
            val descriptor = describe(tx, dimension, categoryByKey, groupById)
            val a = acc.getOrPut(descriptor.key) {
                Acc(descriptor.key, descriptor.label, descriptor.emoji, descriptor.colorHex)
            }
            a.amount += tx.amountMinor
            a.count += 1
        }
        val total = acc.values.sumOf { it.amount }
        return acc.values
            .sortedWith(compareByDescending<Acc> { it.amount }.thenBy { it.label })
            .map {
                BreakdownRow(
                    key = it.key,
                    label = it.label,
                    emoji = it.emoji,
                    colorHex = it.colorHex,
                    amountMinor = it.amount,
                    share = if (total > 0) it.amount.toFloat() / total else 0f,
                    transactionCount = it.count
                )
            }
    }

    /**
     * [endMonth] is the last month of the chart window and defaults to [selectedMonth];
     * pass it to keep the window stable while the selection moves.
     */
    fun report(
        transactions: List<TransactionItem>,
        selectedMonth: YearMonth,
        monthsBack: Int,
        dimension: BreakdownDimension,
        categories: List<CategoryEntity>,
        groups: List<CategoryGroupEntity>,
        zone: ZoneId,
        endMonth: YearMonth = selectedMonth
    ): CashFlowReport {
        val months = monthly(transactions, endMonth, monthsBack, zone)
        val selected = months.firstOrNull { it.month == selectedMonth }
            ?: monthly(transactions, selectedMonth, 1, zone).first()
        val active = months.filter { it.incomeMinor > 0 || it.expenseMinor > 0 }
        val avgExpense = if (active.isEmpty()) 0L else active.sumOf { it.expenseMinor } / active.size
        val avgIncome = if (active.isEmpty()) 0L else active.sumOf { it.incomeMinor } / active.size
        return CashFlowReport(
            months = months,
            selected = selected,
            expenseBreakdown = breakdown(transactions, selectedMonth, dimension, false, categories, groups, zone),
            incomeBreakdown = breakdown(transactions, selectedMonth, dimension, true, categories, groups, zone),
            averageMonthlyExpenseMinor = avgExpense,
            averageMonthlyIncomeMinor = avgIncome
        )
    }

    /** Transactions behind one breakdown row, newest first. */
    fun transactionsFor(
        transactions: List<TransactionItem>,
        month: YearMonth,
        dimension: BreakdownDimension,
        key: String,
        income: Boolean,
        categories: List<CategoryEntity>,
        zone: ZoneId
    ): List<TransactionItem> {
        val categoryByKey = categories.associateBy { it.key }
        return matching(transactions, month, income, zone)
            .filter { tx ->
                when (dimension) {
                    BreakdownDimension.CATEGORY -> (tx.categoryKey ?: OTHER_KEY) == key
                    BreakdownDimension.GROUP -> {
                        val gid = tx.categoryKey?.let { categoryByKey[it]?.groupId }
                        (gid?.toString() ?: OTHER_KEY) == key
                    }
                    BreakdownDimension.MERCHANT -> merchantKey(tx) == key
                }
            }
            .sortedByDescending { it.timestamp }
    }

    private class Acc(val key: String, val label: String, val emoji: String, val colorHex: String) {
        var amount: Long = 0L
        var count: Int = 0
    }

    private class Descriptor(val key: String, val label: String, val emoji: String, val colorHex: String)

    private fun matching(
        transactions: List<TransactionItem>,
        month: YearMonth,
        income: Boolean,
        zone: ZoneId
    ): List<TransactionItem> = transactions.filter {
        (if (income) it.isIncome() else it.isSpending()) && monthOf(it.timestamp, zone) == month
    }

    private fun merchantKey(tx: TransactionItem): String {
        val m = tx.merchant?.trim().orEmpty()
        return if (m.isEmpty()) UNKNOWN_MERCHANT_KEY else m.lowercase(Locale.ROOT)
    }

    private fun describe(
        tx: TransactionItem,
        dimension: BreakdownDimension,
        categoryByKey: Map<String, CategoryEntity>,
        groupById: Map<Long, CategoryGroupEntity>
    ): Descriptor {
        val category = tx.categoryKey?.let { categoryByKey[it] }
        return when (dimension) {
            BreakdownDimension.CATEGORY -> Descriptor(
                key = tx.categoryKey ?: OTHER_KEY,
                label = category?.name ?: tx.categoryName,
                emoji = category?.emoji ?: tx.categoryEmoji,
                colorHex = category?.colorHex ?: tx.categoryColorHex
            )
            BreakdownDimension.GROUP -> {
                val group = category?.let { groupById[it.groupId] }
                if (group != null) {
                    Descriptor(
                        key = group.id.toString(),
                        label = group.name,
                        emoji = group.emoji,
                        colorHex = category?.colorHex ?: tx.categoryColorHex
                    )
                } else {
                    Descriptor(OTHER_KEY, "Other", tx.categoryEmoji, tx.categoryColorHex)
                }
            }
            BreakdownDimension.MERCHANT -> {
                val key = merchantKey(tx)
                Descriptor(
                    key = key,
                    label = if (key == UNKNOWN_MERCHANT_KEY) UNKNOWN_MERCHANT_LABEL else tx.merchant!!.trim(),
                    emoji = category?.emoji ?: tx.categoryEmoji,
                    colorHex = category?.colorHex ?: tx.categoryColorHex
                )
            }
        }
    }
}
