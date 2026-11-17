package com.spendtracker.app.domain.budget

import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.RefundAttribution
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.monthOf
import com.spendtracker.app.domain.monthProgress
import com.spendtracker.app.domain.netSpendMinor
import com.spendtracker.app.domain.spendCategoryKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class BudgetLine(
    val categoryKey: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val groupId: Long,
    val groupName: String,
    val budgetedMinor: Long,
    val rolloverInMinor: Long,
    val spentMinor: Long
) {
    val availableMinor: Long get() = budgetedMinor + rolloverInMinor - spentMinor
    val progress: Float
        get() = if (budgetedMinor + rolloverInMinor <= 0) {
            if (spentMinor > 0) 1f else 0f
        } else {
            spentMinor.toFloat() / (budgetedMinor + rolloverInMinor)
        }
}

data class BudgetGroupSection(
    val groupId: Long,
    val groupName: String,
    val lines: List<BudgetLine>
)

data class BudgetSummary(
    val month: YearMonth,
    val totalBudgetedMinor: Long,
    val totalSpentBudgetedMinor: Long,
    val unbudgetedSpentMinor: Long,
    val incomeMinor: Long,
    val leftToSpendMinor: Long,
    val monthProgress: Float,
    val sections: List<BudgetGroupSection>,
    val unbudgeted: List<BudgetLine>
)

object BudgetCalculator {

    fun summarize(
        month: YearMonth,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        groups: List<CategoryGroupEntity>,
        transactions: List<TransactionItem>,
        today: LocalDate,
        zone: ZoneId
    ): BudgetSummary {
        val previous: YearMonth = month.minusMonths(1)
        val spentThisMonth: MutableMap<String, Long> = HashMap()
        val spentPrevMonth: MutableMap<String, Long> = HashMap()
        var income = 0L
        val attribution: RefundAttribution = RefundAttribution.of(transactions)
        for (tx in transactions) {
            val txMonth: YearMonth = monthOf(tx.timestamp, zone)
            if (txMonth == month && tx.isIncome()) {
                income += tx.amountMinor
            }
            // Spending adds to its category; a refund subtracts from the category it was attributed to.
            val key: String = tx.spendCategoryKey(attribution) ?: continue
            val net: Long = tx.netSpendMinor()
            when (txMonth) {
                month -> spentThisMonth[key] = (spentThisMonth[key] ?: 0L) + net
                previous -> spentPrevMonth[key] = (spentPrevMonth[key] ?: 0L) + net
                else -> Unit
            }
        }
        spentThisMonth.replaceAll { _, v -> maxOf(0L, v) }
        spentPrevMonth.replaceAll { _, v -> maxOf(0L, v) }
        // Rollover is one month deep and only from a month in which the budget already existed.
        // BudgetEntity has no createdAt, so updatedAt (set on create and on every edit) is the
        // conservative "exists since": a budget created or changed this month carries nothing over.
        val monthStart: Long = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val budgetByKey: Map<String, BudgetEntity> = budgets.associateBy { it.categoryKey }
        val groupById: Map<Long, CategoryGroupEntity> = groups.associateBy { it.id }
        val expenseCategories: List<CategoryEntity> = categories.filter { it.type == TransactionType.EXPENSE }

        val budgetedLines: MutableList<BudgetLine> = ArrayList()
        val unbudgetedLines: MutableList<BudgetLine> = ArrayList()
        var totalBudgeted = 0L
        var totalRollover = 0L
        var totalSpentBudgeted = 0L
        var unbudgetedSpent = 0L

        for (category in expenseCategories) {
            val budget: BudgetEntity? = budgetByKey[category.key]
            val spent: Long = spentThisMonth[category.key] ?: 0L
            if (budget == null && spent == 0L) continue
            val group: CategoryGroupEntity? = groupById[category.groupId]
            val rolloverIn: Long = if (budget != null && budget.rollover && budget.updatedAt < monthStart) {
                budget.amountMinor - (spentPrevMonth[category.key] ?: 0L)
            } else {
                0L
            }
            val line = BudgetLine(
                categoryKey = category.key,
                name = category.name,
                emoji = category.emoji,
                colorHex = category.colorHex,
                groupId = category.groupId,
                groupName = group?.name ?: "Other",
                budgetedMinor = budget?.amountMinor ?: 0L,
                rolloverInMinor = rolloverIn,
                spentMinor = spent
            )
            if (budget != null) {
                budgetedLines += line
                totalBudgeted += budget.amountMinor
                totalRollover += rolloverIn
                totalSpentBudgeted += spent
            } else {
                unbudgetedLines += line
                unbudgetedSpent += spent
            }
        }

        val sections: List<BudgetGroupSection> = budgetedLines
            .groupBy { it.groupId }
            .map { (groupId, lines) ->
                BudgetGroupSection(
                    groupId = groupId,
                    groupName = lines.first().groupName,
                    lines = lines.sortedWith(
                        compareByDescending<BudgetLine> { it.spentMinor }.thenBy { it.name }
                    )
                )
            }
            .sortedWith(
                compareBy<BudgetGroupSection> { groupById[it.groupId]?.sortOrder ?: Int.MAX_VALUE }
                    .thenBy { it.groupId }
            )

        return BudgetSummary(
            month = month,
            totalBudgetedMinor = totalBudgeted,
            totalSpentBudgetedMinor = totalSpentBudgeted,
            unbudgetedSpentMinor = unbudgetedSpent,
            incomeMinor = income,
            leftToSpendMinor = totalBudgeted + totalRollover - totalSpentBudgeted,
            monthProgress = monthProgress(month, today),
            sections = sections,
            unbudgeted = unbudgetedLines.sortedWith(
                compareByDescending<BudgetLine> { it.spentMinor }.thenBy { it.name }
            )
        )
    }
}

/**
 * Suggested monthly budget per expense category: average spend over the 3 months before [month],
 * rounded up to whole major units (multiple of 100 minor). Zeros are omitted.
 */
fun suggestedBudgets(
    categories: List<CategoryEntity>,
    transactions: List<TransactionItem>,
    month: YearMonth,
    zone: ZoneId
): Map<String, Long> {
    val first: YearMonth = month.minusMonths(3)
    val totals: MutableMap<String, Long> = HashMap()
    val attribution: RefundAttribution = RefundAttribution.of(transactions)
    for (tx in transactions) {
        val key: String = tx.spendCategoryKey(attribution) ?: continue
        val m: YearMonth = monthOf(tx.timestamp, zone)
        if (m.isBefore(first) || !m.isBefore(month)) continue
        totals[key] = (totals[key] ?: 0L) + tx.netSpendMinor()
    }
    val result: MutableMap<String, Long> = LinkedHashMap()
    for (category in categories) {
        if (category.type != TransactionType.EXPENSE) continue
        val total: Long = totals[category.key] ?: 0L
        if (total <= 0L) continue
        val averageMinor: Long = (total + 2L) / 3L // ceil(total / 3)
        val rounded: Long = ((averageMinor + 99L) / 100L) * 100L
        if (rounded > 0L) result[category.key] = rounded
    }
    return result
}
