package com.spendtracker.app.ui.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.cashflow.BreakdownDimension
import com.spendtracker.app.domain.cashflow.BreakdownRow
import com.spendtracker.app.domain.cashflow.CashFlowCalculator
import com.spendtracker.app.domain.cashflow.CashFlowReport
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
    val canGoNext: Boolean get() = selectedMonth.isBefore(currentMonth)
}

class CashFlowViewModel(private val repository: SpendRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val currentMonth: YearMonth = YearMonth.now(zone)

    private data class Controls(
        val month: YearMonth,
        val range: Int = 6,
        val dimension: BreakdownDimension = BreakdownDimension.CATEGORY,
        val showIncome: Boolean = false,
        val drill: BreakdownRow? = null
    )

    private val controls = MutableStateFlow(Controls(month = currentMonth))

    val uiState: StateFlow<CashFlowUiState> = combine(
        controls,
        repository.observeTransactionItems(),
        repository.observeCategories(),
        repository.observeGroups()
    ) { c, transactions, categories, groups ->
        // Keep the chart window ending at the current month unless the selection falls before it.
        val windowStart = currentMonth.minusMonths((c.range - 1).toLong())
        val endMonth = if (c.month.isBefore(windowStart)) {
            minOf(currentMonth, c.month.plusMonths((c.range - 1).toLong()))
        } else {
            currentMonth
        }
        val report = CashFlowCalculator.report(
            transactions = transactions,
            selectedMonth = c.month,
            monthsBack = c.range,
            dimension = c.dimension,
            categories = categories,
            groups = groups,
            zone = zone,
            endMonth = endMonth
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
                    month = c.month,
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
            selectedMonth = c.month,
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
        CashFlowUiState(currentMonth = currentMonth, selectedMonth = currentMonth)
    )

    fun selectMonth(month: YearMonth) {
        val clamped = if (month.isAfter(currentMonth)) currentMonth else month
        controls.update { it.copy(month = clamped, drill = null) }
    }

    fun previousMonth() {
        controls.update { it.copy(month = it.month.minusMonths(1), drill = null) }
    }

    fun nextMonth() {
        controls.update {
            if (it.month.isBefore(currentMonth)) it.copy(month = it.month.plusMonths(1), drill = null) else it
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
