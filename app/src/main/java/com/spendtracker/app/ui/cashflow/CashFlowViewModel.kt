package com.spendtracker.app.ui.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.cashflow.BreakdownDimension
import com.spendtracker.app.domain.cashflow.BreakdownRow
import com.spendtracker.app.domain.cashflow.CashFlowCalculator
import com.spendtracker.app.domain.cashflow.CashFlowReport
import com.spendtracker.app.domain.todayFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId

/** The breakdown row whose transactions are shown in the drill-down sheet. */
data class CashFlowDrillDown(
    val key: String,
    val label: String,
    val emoji: String,
    val colorHex: String,
    val amountMinor: Long,
    val transactions: List<TransactionItem>
)

data class CashFlowUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val rangeMonths: Int = 6,
    val dimension: BreakdownDimension = BreakdownDimension.CATEGORY,
    val showIncome: Boolean = false,
    val report: CashFlowReport? = null,
    val hasData: Boolean = false,
    val drillDown: CashFlowDrillDown? = null
) {
    /** False until there is at least one finished month with activity (averages exclude this month). */
    val hasAverages: Boolean get() = (report?.averageMonthCount ?: 0) > 0

    val canGoNext: Boolean get() = selectedMonth.isBefore(currentMonth)
}

class CashFlowViewModel(private val repository: SpendRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Read fresh each time so the current month moves on after month end. */
    private fun currentMonth(): YearMonth = YearMonth.now(zone)

    private data class Controls(
        /** Null = follow the current month. */
        val month: YearMonth? = null,
        val range: Int = 6,
        val dimension: BreakdownDimension = BreakdownDimension.CATEGORY,
        val showIncome: Boolean = false,
        val drill: BreakdownRow? = null
    )

    private val controls = MutableStateFlow(Controls())

    val uiState: StateFlow<CashFlowUiState> = combine(
        controls,
        repository.observeTransactionItems(),
        repository.observeCategories(),
        repository.observeGroups(),
        todayFlow()
    ) { controlState, transactions, categories, groups, today ->
        val currentMonth: YearMonth = YearMonth.from(today)
        val selected: YearMonth = controlState.month?.takeIf { !it.isAfter(currentMonth) } ?: currentMonth
        val c: Controls = controlState.copy(month = selected)
        // Keep the chart window ending at the current month unless the selection falls before it.
        val windowStart = currentMonth.minusMonths((c.range - 1).toLong())
        val endMonth = if (selected.isBefore(windowStart)) {
            minOf(currentMonth, selected.plusMonths((c.range - 1).toLong()))
        } else {
            currentMonth
        }
        val report = CashFlowCalculator.report(
            transactions = transactions,
            selectedMonth = selected,
            monthsBack = c.range,
            dimension = c.dimension,
            categories = categories,
            groups = groups,
            zone = zone,
            endMonth = endMonth,
            inProgressMonth = currentMonth
        )
        val drillDown = c.drill?.let { row ->
            CashFlowDrillDown(
                key = row.key,
                label = row.label,
                emoji = row.emoji,
                colorHex = row.colorHex,
                amountMinor = row.amountMinor,
                transactions = CashFlowCalculator.transactionsFor(
                    transactions = transactions,
                    month = selected,
                    dimension = c.dimension,
                    key = row.key,
                    income = c.showIncome,
                    categories = categories,
                    zone = zone
                )
            )
        }
        CashFlowUiState(
            currentMonth = currentMonth,
            selectedMonth = selected,
            rangeMonths = c.range,
            dimension = c.dimension,
            showIncome = c.showIncome,
            report = report,
            hasData = report.months.any { it.incomeMinor > 0 || it.expenseMinor > 0 },
            drillDown = drillDown
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CashFlowUiState(currentMonth = currentMonth(), selectedMonth = currentMonth())
    )

    /** Stores null for the current month so the selection keeps following it after month end. */
    private fun normalise(month: YearMonth): YearMonth? {
        val current = currentMonth()
        return if (!month.isBefore(current)) null else month
    }

    fun selectMonth(month: YearMonth) {
        controls.update { it.copy(month = normalise(month), drill = null) }
    }

    fun previousMonth() {
        controls.update { it.copy(month = normalise((it.month ?: currentMonth()).minusMonths(1)), drill = null) }
    }

    fun nextMonth() {
        controls.update {
            val month = it.month ?: return@update it
            it.copy(month = normalise(month.plusMonths(1)), drill = null)
        }
    }

    fun setRange(months: Int) {
        val range = if (months >= 12) 12 else 6
        controls.update { it.copy(range = range) }
    }

    fun setDimension(dimension: BreakdownDimension) {
        controls.update { it.copy(dimension = dimension, drill = null) }
    }

    fun setShowIncome(showIncome: Boolean) {
        controls.update { it.copy(showIncome = showIncome, drill = null) }
    }

    fun openBreakdown(row: BreakdownRow) {
        controls.update { it.copy(drill = row) }
    }

    fun closeBreakdown() {
        controls.update { it.copy(drill = null) }
    }
}
