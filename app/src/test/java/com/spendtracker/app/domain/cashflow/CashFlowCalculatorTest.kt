package com.spendtracker.app.domain.cashflow

import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CashFlowCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val nov = YearMonth.of(2026, 11)

    private val groups = listOf(
        CategoryGroupEntity(id = 1, name = "Food", emoji = "F", sortOrder = 0, isBuiltIn = true),
        CategoryGroupEntity(id = 2, name = "Home", emoji = "H", sortOrder = 1, isBuiltIn = true)
    )
    private val categories = listOf(
        CategoryEntity("GROCERIES", 1, "Groceries", "G", "#111111", TransactionType.EXPENSE, true, false),
        CategoryEntity("DINING", 1, "Dining", "D", "#222222", TransactionType.EXPENSE, true, false),
        CategoryEntity("RENT", 2, "Rent", "R", "#333333", TransactionType.EXPENSE, true, false),
        CategoryEntity("SALARY", 2, "Salary", "S", "#444444", TransactionType.INCOME, true, false)
    )

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: Long,
        date: LocalDate,
        category: String? = "GROCERIES",
        merchant: String? = "Tesco",
        exclude: Boolean = false
    ) = TransactionItem(
        id = id,
        type = type,
        amountMinor = amount,
        accountId = 1L,
        accountName = "Chase",
        categoryKey = category,
        merchant = merchant,
        timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        excludeFromSpending = exclude
    )

    private fun expense(id: Long, amount: Long, date: LocalDate, category: String? = "GROCERIES", merchant: String? = "Tesco") =
        tx(id, TransactionType.EXPENSE, amount, date, category, merchant)

    private fun income(id: Long, amount: Long, date: LocalDate) =
        tx(id, TransactionType.INCOME, amount, date, "SALARY", "Employer")

    @Test
    fun `monthly is zero filled and ordered oldest to newest`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 3)),
            income(2, 5000, LocalDate.of(2026, 9, 1))
        )
        val months = CashFlowCalculator.monthly(list, nov, 4, zone)
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10), nov),
            months.map { it.month }
        )
        assertEquals(listOf(0L, 5000L, 0L, 0L), months.map { it.incomeMinor })
        assertEquals(listOf(0L, 0L, 0L, 1000L), months.map { it.expenseMinor })
    }

    @Test
    fun `monthly ignores excluded and internal transfer rows`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 3)),
            tx(2, TransactionType.EXPENSE, 9000, LocalDate.of(2026, 11, 4), exclude = true),
            tx(3, TransactionType.EXPENSE, 7000, LocalDate.of(2026, 11, 5), category = "INTERNAL_TRANSFER"),
            tx(4, TransactionType.INCOME, 8000, LocalDate.of(2026, 11, 6), exclude = true),
            tx(5, TransactionType.TRANSFER, 6000, LocalDate.of(2026, 11, 7))
        )
        val m = CashFlowCalculator.monthly(list, nov, 1, zone).single()
        assertEquals(1000L, m.expenseMinor)
        assertEquals(0L, m.incomeMinor)
    }

    @Test
    fun `savings and savings rate are computed`() {
        val m = CashFlowMonth(nov, incomeMinor = 10_000, expenseMinor = 7_500)
        assertEquals(2_500L, m.savingsMinor)
        assertEquals(0.25f, m.savingsRate!!, 0.0001f)
    }

    @Test
    fun `savings rate is null when there is no income`() {
        val m = CashFlowMonth(nov, incomeMinor = 0, expenseMinor = 500)
        assertEquals(-500L, m.savingsMinor)
        assertNull(m.savingsRate)
    }

    @Test
    fun `category breakdown is sorted descending with shares summing to one`() {
        val list = listOf(
            expense(1, 3000, LocalDate.of(2026, 11, 2), "GROCERIES"),
            expense(2, 1000, LocalDate.of(2026, 11, 3), "DINING"),
            expense(3, 6000, LocalDate.of(2026, 11, 4), "RENT"),
            expense(4, 9999, LocalDate.of(2026, 10, 4), "RENT")
        )
        val rows = CashFlowCalculator.breakdown(list, nov, BreakdownDimension.CATEGORY, false, categories, groups, zone)
        assertEquals(listOf("RENT", "GROCERIES", "DINING"), rows.map { it.key })
        assertEquals("Rent", rows[0].label)
        assertEquals("#333333", rows[0].colorHex)
        assertEquals(0.6f, rows[0].share, 0.0001f)
        assertEquals(1.0f, rows.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `category breakdown falls back to item fields for unknown category`() {
        val item = expense(1, 500, LocalDate.of(2026, 11, 2), "C_MISSING").copy(
            categoryName = "Mystery", categoryEmoji = "M", categoryColorHex = "#ABCDEF"
        )
        val row = CashFlowCalculator.breakdown(listOf(item), nov, BreakdownDimension.CATEGORY, false, categories, groups, zone).single()
        assertEquals("Mystery", row.label)
        assertEquals("M", row.emoji)
        assertEquals("#ABCDEF", row.colorHex)
    }

    @Test
    fun `group breakdown merges categories of one group`() {
        val list = listOf(
            expense(1, 3000, LocalDate.of(2026, 11, 2), "GROCERIES"),
            expense(2, 1000, LocalDate.of(2026, 11, 3), "DINING"),
            expense(3, 2000, LocalDate.of(2026, 11, 4), "RENT")
        )
        val rows = CashFlowCalculator.breakdown(list, nov, BreakdownDimension.GROUP, false, categories, groups, zone)
        assertEquals(listOf("1", "2"), rows.map { it.key })
        assertEquals("Food", rows[0].label)
        assertEquals("F", rows[0].emoji)
        assertEquals(4000L, rows[0].amountMinor)
        assertEquals(2, rows[0].transactionCount)
        assertEquals(1.0f, rows.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `merchant breakdown groups case insensitively and labels unknown vendor`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 2), merchant = "Tesco"),
            expense(2, 500, LocalDate.of(2026, 11, 3), merchant = "TESCO "),
            expense(3, 300, LocalDate.of(2026, 11, 4), merchant = null),
            expense(4, 200, LocalDate.of(2026, 11, 5), merchant = "  ")
        )
        val rows = CashFlowCalculator.breakdown(list, nov, BreakdownDimension.MERCHANT, false, categories, groups, zone)
        assertEquals(listOf("tesco", "unknown"), rows.map { it.key })
        assertEquals(1500L, rows[0].amountMinor)
        assertEquals("Unknown vendor", rows[1].label)
        assertEquals(500L, rows[1].amountMinor)
        assertEquals(1.0f, rows.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `income breakdown only includes income`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 2)),
            income(2, 4000, LocalDate.of(2026, 11, 3))
        )
        val rows = CashFlowCalculator.breakdown(list, nov, BreakdownDimension.CATEGORY, true, categories, groups, zone)
        assertEquals(listOf("SALARY"), rows.map { it.key })
        assertEquals(1f, rows.single().share, 0.0001f)
    }

    @Test
    fun `empty breakdown is empty`() {
        assertTrue(CashFlowCalculator.breakdown(emptyList(), nov, BreakdownDimension.CATEGORY, false, categories, groups, zone).isEmpty())
    }

    @Test
    fun `report averages only months with activity`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 9, 2)),
            expense(2, 3000, LocalDate.of(2026, 11, 2)),
            income(3, 8000, LocalDate.of(2026, 11, 3))
        )
        val report = CashFlowCalculator.report(list, nov, 6, BreakdownDimension.CATEGORY, categories, groups, zone)
        assertEquals(6, report.months.size)
        assertEquals(2000L, report.averageMonthlyExpenseMinor)
        assertEquals(4000L, report.averageMonthlyIncomeMinor)
        assertEquals(nov, report.selected.month)
        assertEquals(3000L, report.selected.expenseMinor)
        assertEquals(1, report.expenseBreakdown.size)
        assertEquals(1, report.incomeBreakdown.size)
    }

    @Test
    fun `report with no data has zero averages`() {
        val report = CashFlowCalculator.report(emptyList(), nov, 6, BreakdownDimension.GROUP, categories, groups, zone)
        assertEquals(0L, report.averageMonthlyExpenseMinor)
        assertEquals(0L, report.averageMonthlyIncomeMinor)
    }

    @Test
    fun `report keeps the window at endMonth when an earlier month is selected`() {
        val list = listOf(expense(1, 1000, LocalDate.of(2026, 9, 2)))
        val report = CashFlowCalculator.report(
            list, YearMonth.of(2026, 9), 6, BreakdownDimension.CATEGORY, categories, groups, zone, endMonth = nov
        )
        assertEquals(nov, report.months.last().month)
        assertEquals(1000L, report.selected.expenseMinor)
    }

    @Test
    fun `transactionsFor returns matching rows newest first`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 2), "GROCERIES", "Tesco"),
            expense(2, 500, LocalDate.of(2026, 11, 9), "DINING", "Tesco"),
            expense(3, 300, LocalDate.of(2026, 11, 5), "GROCERIES", "Aldi"),
            expense(4, 300, LocalDate.of(2026, 10, 5), "GROCERIES", "Tesco")
        )
        val byCategory = CashFlowCalculator.transactionsFor(list, nov, BreakdownDimension.CATEGORY, "GROCERIES", false, categories, zone)
        assertEquals(listOf(3L, 1L), byCategory.map { it.id })
        val byGroup = CashFlowCalculator.transactionsFor(list, nov, BreakdownDimension.GROUP, "1", false, categories, zone)
        assertEquals(listOf(2L, 3L, 1L), byGroup.map { it.id })
        val byMerchant = CashFlowCalculator.transactionsFor(list, nov, BreakdownDimension.MERCHANT, "tesco", false, categories, zone)
        assertEquals(listOf(2L, 1L), byMerchant.map { it.id })
    }

    @Test
    fun `transactionsFor handles unknown merchant and income`() {
        val list = listOf(
            expense(1, 1000, LocalDate.of(2026, 11, 2), merchant = null),
            income(2, 5000, LocalDate.of(2026, 11, 3))
        )
        val unknown = CashFlowCalculator.transactionsFor(list, nov, BreakdownDimension.MERCHANT, "unknown", false, categories, zone)
        assertEquals(listOf(1L), unknown.map { it.id })
        val inc = CashFlowCalculator.transactionsFor(list, nov, BreakdownDimension.CATEGORY, "SALARY", true, categories, zone)
        assertEquals(listOf(2L), inc.map { it.id })
    }
}
