package com.spendtracker.app.domain.budget

import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val nov: YearMonth = YearMonth.of(2026, 11)
    private val today: LocalDate = LocalDate.of(2026, 11, 15)

    private val groups: List<CategoryGroupEntity> = listOf(
        CategoryGroupEntity(id = 1, name = "Food", emoji = "F", sortOrder = 2, isBuiltIn = true),
        CategoryGroupEntity(id = 2, name = "Living", emoji = "L", sortOrder = 1, isBuiltIn = true),
        CategoryGroupEntity(id = 5, name = "Income", emoji = "I", sortOrder = 3, isBuiltIn = true)
    )

    private fun category(
        key: String,
        groupId: Long,
        type: TransactionType = TransactionType.EXPENSE,
        hidden: Boolean = false
    ): CategoryEntity = CategoryEntity(
        key = key, groupId = groupId, name = key.lowercase().replaceFirstChar { it.uppercase() },
        emoji = "x", colorHex = "#FFFFFF", type = type, isBuiltIn = true, isHidden = hidden
    )

    private val categories: List<CategoryEntity> = listOf(
        category("GROCERIES", 1),
        category("DINING", 1),
        category("RENT", 2),
        category("SHOPPING", 1),
        category("SALARY", 5, TransactionType.INCOME)
    )

    private fun ts(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private var nextId: Long = 1

    private fun expense(
        key: String,
        amount: Long,
        year: Int = 2026,
        month: Int = 11,
        day: Int = 5,
        excluded: Boolean = false
    ): TransactionItem = TransactionItem(
        id = nextId++, type = TransactionType.EXPENSE, amountMinor = amount, accountId = 1,
        accountName = "Chase", categoryKey = key, timestamp = ts(year, month, day),
        excludeFromSpending = excluded
    )

    private fun income(amount: Long, month: Int = 11): TransactionItem = TransactionItem(
        id = nextId++, type = TransactionType.INCOME, amountMinor = amount, accountId = 1,
        accountName = "Chase", categoryKey = "SALARY", timestamp = ts(2026, month, 1)
    )

    private fun budget(key: String, amount: Long, rollover: Boolean = false): BudgetEntity =
        BudgetEntity(categoryKey = key, amountMinor = amount, rollover = rollover, updatedAt = 0L)

    private fun summarize(budgets: List<BudgetEntity>, txs: List<TransactionItem>): BudgetSummary =
        BudgetCalculator.summarize(nov, budgets, categories, groups, txs, today, zone)

    @Test
    fun `spent per category sums spending in the month only`() {
        val s = summarize(
            listOf(budget("GROCERIES", 30000)),
            listOf(
                expense("GROCERIES", 5000), expense("GROCERIES", 2500),
                expense("GROCERIES", 9999, month = 10, day = 28)
            )
        )
        val line = s.sections.single().lines.single()
        assertEquals(7500L, line.spentMinor)
        assertEquals(22500L, line.availableMinor)
        assertEquals(22500L, s.leftToSpendMinor)
    }

    @Test
    fun `excluded and income transactions are ignored for spending`() {
        val s = summarize(
            listOf(budget("GROCERIES", 10000)),
            listOf(expense("GROCERIES", 4000), expense("GROCERIES", 3000, excluded = true), income(200000))
        )
        assertEquals(4000L, s.totalSpentBudgetedMinor)
        assertEquals(200000L, s.incomeMinor)
        assertTrue(s.unbudgeted.isEmpty())
    }

    @Test
    fun `positive rollover from previous month adds to available`() {
        val s = summarize(
            listOf(budget("DINING", 20000, rollover = true)),
            listOf(expense("DINING", 15000, month = 10, day = 10), expense("DINING", 3000))
        )
        val line = s.sections.single().lines.single()
        assertEquals(5000L, line.rolloverInMinor)
        assertEquals(22000L, line.availableMinor)
        assertEquals(22000L, s.leftToSpendMinor)
    }

    @Test
    fun `negative rollover reduces available`() {
        val s = summarize(
            listOf(budget("DINING", 20000, rollover = true)),
            listOf(expense("DINING", 26000, month = 10, day = 10), expense("DINING", 1000))
        )
        val line = s.sections.single().lines.single()
        assertEquals(-6000L, line.rolloverInMinor)
        assertEquals(13000L, line.availableMinor)
        assertEquals(13000L, s.leftToSpendMinor)
    }

    @Test
    fun `no rollover when flag is off`() {
        val s = summarize(
            listOf(budget("DINING", 20000, rollover = false)),
            listOf(expense("DINING", 5000, month = 10, day = 10))
        )
        assertEquals(0L, s.sections.single().lines.single().rolloverInMinor)
        assertEquals(20000L, s.leftToSpendMinor)
    }

    @Test
    fun `unbudgeted spending is listed and excluded from left to spend`() {
        val s = summarize(
            listOf(budget("GROCERIES", 10000)),
            listOf(expense("GROCERIES", 1000), expense("SHOPPING", 4000), expense("DINING", 6000))
        )
        assertEquals(listOf("DINING", "SHOPPING"), s.unbudgeted.map { it.categoryKey })
        assertEquals(10000L, s.unbudgetedSpentMinor)
        assertEquals(0L, s.unbudgeted.first().budgetedMinor)
        assertEquals(9000L, s.leftToSpendMinor)
    }

    @Test
    fun `categories without budget or spend are omitted including hidden ones`() {
        val withHidden = categories + category("EDUCATION", 1, hidden = true)
        val s = BudgetCalculator.summarize(nov, emptyList(), withHidden, groups, emptyList(), today, zone)
        assertTrue(s.sections.isEmpty())
        assertTrue(s.unbudgeted.isEmpty())
        assertEquals(0L, s.leftToSpendMinor)
    }

    @Test
    fun `sections follow group sort order and lines are sorted by spent descending`() {
        val s = summarize(
            listOf(budget("GROCERIES", 10000), budget("DINING", 10000), budget("RENT", 90000)),
            listOf(expense("GROCERIES", 1000), expense("DINING", 3000), expense("RENT", 90000))
        )
        assertEquals(listOf("Living", "Food"), s.sections.map { it.groupName })
        assertEquals(listOf("DINING", "GROCERIES"), s.sections[1].lines.map { it.categoryKey })
        assertEquals(110000L, s.totalBudgetedMinor)
    }

    @Test
    fun `progress edge cases`() {
        val base = BudgetLine("K", "n", "e", "#FFFFFF", 1, "g", 0L, 0L, 0L)
        assertEquals(0f, base.progress, 0f)
        assertEquals(1f, base.copy(spentMinor = 100).progress, 0f)
        assertEquals(0.5f, base.copy(budgetedMinor = 1000, spentMinor = 500).progress, 0.0001f)
        assertEquals(1.5f, base.copy(budgetedMinor = 1000, spentMinor = 1500).progress, 0.0001f)
        assertEquals(1f, base.copy(budgetedMinor = 1000, rolloverInMinor = -1000, spentMinor = 1).progress, 0f)
    }

    @Test
    fun `month progress is carried into the summary`() {
        val s = summarize(emptyList(), emptyList())
        assertEquals((14 + 0.5f) / 30f, s.monthProgress, 0.0001f)
    }

    @Test
    fun `suggested budgets average three previous months rounded up to whole units`() {
        val txs = listOf(
            expense("GROCERIES", 10000, month = 8, day = 3),
            expense("GROCERIES", 10050, month = 9, day = 3),
            expense("GROCERIES", 10000, month = 10, day = 3),
            expense("GROCERIES", 99999, month = 11, day = 3),
            expense("DINING", 100, month = 10, day = 3),
            expense("DINING", 5000, month = 7, day = 3),
            expense("RENT", 30000, month = 9, day = 1, excluded = true)
        )
        val result = suggestedBudgets(categories, txs, nov, zone)
        assertEquals(10100L, result["GROCERIES"])
        assertEquals(100L, result["DINING"])
        assertTrue("RENT" !in result)
        assertTrue("SALARY" !in result)
    }
}
