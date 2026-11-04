package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.Period
import com.spendtracker.app.domain.PeriodCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Supported comparison time horizons for evaluating period-over-period spend.
 */
enum class ComparisonPeriod(
    val label: String,
    val referenceContext: String
) {
    WEEK(label = "Week", referenceContext = "vs last week"),
    MONTH(label = "Month", referenceContext = "vs last month"),
    YEAR(label = "Year", referenceContext = "vs last year")
}

enum class ComparisonMode(val label: String) {
    CATEGORIES("By Category"),
    INTERVAL("By Time")
}

/**
 * Represents a discrete sub-interval or category comparison point (e.g. Groceries, Dining, Day, Month).
 */
data class DataPoint(
    val label: String,
    val currentValue: Double,
    val previousValue: Double
) {
    val difference: Double
        get() = currentValue - previousValue

    val percentageChange: Double
        get() = when {
            previousValue > 0.0 -> ((currentValue - previousValue) / previousValue) * 100.0
            currentValue > 0.0 -> 100.0
            else -> 0.0
        }
}

/**
 * Complete UI state for the period comparison screen.
 */
data class ComparisonUiState(
    val selectedPeriod: ComparisonPeriod = ComparisonPeriod.WEEK,
    val comparisonMode: ComparisonMode = ComparisonMode.CATEGORIES,
    val currentPeriodTotal: Double = 0.0,
    val previousPeriodTotal: Double = 0.0,
    val percentageChange: Double = 0.0,
    val isIncrease: Boolean = false,
    val breakdownItems: List<DataPoint> = emptyList(),
    val isLoading: Boolean = false,
    /** Guard against misleading comparisons when history is insufficient (< 7 or < 30 days) */
    val isInitialPeriod: Boolean = false,
    val historyDays: Long = 0L,
    val onboardingMessage: String = "Building your baseline: comparisons will appear after your first week/month"
)

/**
 * ViewModel managing reactive calculation and period switching.
 */
class ComparisonViewModel(
    private val repository: SpendRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState(isLoading = true))
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()
    private var initialPeriodOverride: Boolean? = null

    private var cachedTransactions: List<TransactionEntity> = emptyList()
    private var cachedCategories: Map<String, CategoryEntity> = emptyMap()

    init {
        if (repository != null) {
            viewModelScope.launch {
                combine(
                    repository.observeTransactions(),
                    repository.observeCategories()
                ) { txs, cats ->
                    Pair(txs, cats)
                }.collect { (txs, cats) ->
                    cachedTransactions = txs
                    cachedCategories = cats.associateBy { it.key }
                    val historyDays = calculateHistoryDays(txs)
                    val isInitial = initialPeriodOverride ?: isInitialBaseline(historyDays, _uiState.value.selectedPeriod)

                    _uiState.update { current ->
                        current.copy(
                            historyDays = historyDays,
                            isInitialPeriod = isInitial
                        )
                    }
                    recalculateComparison()
                }
            }
        } else {
            selectPeriod(ComparisonPeriod.WEEK)
        }
    }

    fun setInitialPeriod(isInitial: Boolean) {
        initialPeriodOverride = isInitial
        _uiState.update { current ->
            val pctChange = when {
                isInitial -> 0.0
                current.previousPeriodTotal > 0.0 -> ((current.currentPeriodTotal - current.previousPeriodTotal) / current.previousPeriodTotal) * 100.0
                current.currentPeriodTotal > 0.0 -> 100.0
                else -> 0.0
            }
            current.copy(isInitialPeriod = isInitial, percentageChange = pctChange)
        }
    }

    fun isInitialBaseline(historyDays: Long, period: ComparisonPeriod): Boolean {
        val requiredDays = when (period) {
            ComparisonPeriod.WEEK -> 7L
            ComparisonPeriod.MONTH -> 30L
            ComparisonPeriod.YEAR -> 30L
        }
        return historyDays < requiredDays
    }

    fun calculateHistoryDays(transactions: List<TransactionEntity>): Long {
        if (transactions.isEmpty()) return 0L
        val oldest = transactions.minOfOrNull { it.timestamp } ?: return 0L
        val diff = System.currentTimeMillis() - oldest
        return (diff / (24L * 60 * 60 * 1000L)).coerceAtLeast(0L)
    }

    fun selectPeriod(period: ComparisonPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        recalculateComparison()
    }

    fun selectMode(mode: ComparisonMode) {
        _uiState.update { it.copy(comparisonMode = mode) }
        recalculateComparison()
    }

    private fun recalculateComparison() {
        val period = _uiState.value.selectedPeriod
        val mode = _uiState.value.comparisonMode

        val breakdown = if (mode == ComparisonMode.CATEGORIES) {
            if (repository != null && cachedTransactions.isNotEmpty()) {
                calculateCategoryComparison(period, cachedTransactions, cachedCategories)
            } else {
                generateSampleCategoryData(period)
            }
        } else {
            calculateIntervalComparison(period, cachedTransactions)
        }

        val currentTotal = breakdown.sumOf { it.currentValue }
        val previousTotal = breakdown.sumOf { it.previousValue }

        val isInitial = initialPeriodOverride ?: if (repository != null) {
            isInitialBaseline(_uiState.value.historyDays, period)
        } else {
            _uiState.value.isInitialPeriod
        }

        val pctChange = when {
            isInitial -> 0.0
            previousTotal > 0.0 -> ((currentTotal - previousTotal) / previousTotal) * 100.0
            currentTotal > 0.0 -> 100.0
            else -> 0.0
        }

        _uiState.update {
            it.copy(
                selectedPeriod = period,
                comparisonMode = mode,
                currentPeriodTotal = currentTotal,
                previousPeriodTotal = previousTotal,
                percentageChange = pctChange,
                isIncrease = currentTotal >= previousTotal,
                breakdownItems = breakdown,
                isLoading = false,
                isInitialPeriod = isInitial
            )
        }
    }

    private fun calculateCategoryComparison(
        period: ComparisonPeriod,
        transactions: List<TransactionEntity>,
        categoriesMap: Map<String, CategoryEntity>
    ): List<DataPoint> {
        val domainPeriod = when (period) {
            ComparisonPeriod.WEEK -> Period.WEEK
            ComparisonPeriod.MONTH -> Period.MONTH
            ComparisonPeriod.YEAR -> Period.YEAR
        }
        val today = LocalDate.now()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val curWindow = PeriodCalculator.current(domainPeriod, today, firstDay)
        val prevWindow = PeriodCalculator.previous(curWindow)
        val zone = ZoneId.systemDefault()
        val curRange = curWindow.toRange(zone)
        val prevRange = prevWindow.toRange(zone)

        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE &&
            !it.excludeFromSpending &&
            (it.category == null || it.category != "INTERNAL_TRANSFER")
        }

        val curTxs = expenses.filter { it.timestamp >= curRange.start && it.timestamp < curRange.end }
        val prevTxs = expenses.filter { it.timestamp >= prevRange.start && it.timestamp < prevRange.end }

        val curByCategory = curTxs.groupBy { it.category ?: "OTHER_EXPENSE" }
            .mapValues { (_, txList) -> txList.sumOf { abs(it.amount.toDouble()) } / 100.0 }
        val prevByCategory = prevTxs.groupBy { it.category ?: "OTHER_EXPENSE" }
            .mapValues { (_, txList) -> txList.sumOf { abs(it.amount.toDouble()) } / 100.0 }

        val allCatKeys = (curByCategory.keys + prevByCategory.keys).toSet()

        if (allCatKeys.isEmpty()) {
            return generateSampleCategoryData(period)
        }

        return allCatKeys.map { catKey ->
            val entity = categoriesMap[catKey]
            val emoji = entity?.emoji ?: "🏷️"
            val name = entity?.name ?: catKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            val label = "$emoji $name"
            val curVal = curByCategory[catKey] ?: 0.0
            val prevVal = prevByCategory[catKey] ?: 0.0
            DataPoint(
                label = label,
                currentValue = curVal,
                previousValue = prevVal
            )
        }.sortedByDescending { max(it.currentValue, it.previousValue) }
    }

    private fun generateSampleCategoryData(period: ComparisonPeriod): List<DataPoint> = when (period) {
        ComparisonPeriod.WEEK -> listOf(
            DataPoint(label = "🛒 Groceries", currentValue = 168.50, previousValue = 145.20),
            DataPoint(label = "🍽️ Dining Out", currentValue = 94.00, previousValue = 122.50),
            DataPoint(label = "⚡ Utilities & Bills", currentValue = 65.00, previousValue = 65.00),
            DataPoint(label = "🚗 Transport", currentValue = 48.20, previousValue = 35.80),
            DataPoint(label = "🛍️ Shopping & Lifestyle", currentValue = 52.80, previousValue = 78.00),
            DataPoint(label = "🍿 Subscriptions", currentValue = 10.99, previousValue = 10.99)
        )
        ComparisonPeriod.MONTH -> listOf(
            DataPoint(label = "🛒 Groceries", currentValue = 680.00, previousValue = 612.40),
            DataPoint(label = "🏠 Rent & Housing", currentValue = 1250.00, previousValue = 1250.00),
            DataPoint(label = "🍽️ Dining Out", currentValue = 385.20, previousValue = 440.00),
            DataPoint(label = "⚡ Utilities & Bills", currentValue = 210.00, previousValue = 195.50),
            DataPoint(label = "🚗 Transport", currentValue = 195.00, previousValue = 168.00),
            DataPoint(label = "🛍️ Shopping & Lifestyle", currentValue = 245.50, previousValue = 310.00),
            DataPoint(label = "🍿 Subscriptions", currentValue = 34.99, previousValue = 34.99)
        )
        ComparisonPeriod.YEAR -> listOf(
            DataPoint(label = "🏠 Rent & Housing", currentValue = 15000.00, previousValue = 14400.00),
            DataPoint(label = "🛒 Groceries", currentValue = 7850.00, previousValue = 7200.00),
            DataPoint(label = "🍽️ Dining Out", currentValue = 4400.00, previousValue = 4800.00),
            DataPoint(label = "⚡ Utilities & Bills", currentValue = 2600.00, previousValue = 2450.00),
            DataPoint(label = "🚗 Transport", currentValue = 2280.00, previousValue = 2100.00),
            DataPoint(label = "🛍️ Shopping & Lifestyle", currentValue = 3100.00, previousValue = 3450.00)
        )
    }

    fun calculateIntervalComparison(
        period: ComparisonPeriod,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<DataPoint> {
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE &&
            !it.excludeFromSpending &&
            (it.category == null || it.category != "INTERNAL_TRANSFER")
        }

        return when (period) {
            ComparisonPeriod.WEEK -> {
                val firstDay = DayOfWeek.MONDAY
                val curStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
                val prevStart = curStart.minusDays(7)
                (0 until 7).map { i ->
                    val curDay = curStart.plusDays(i.toLong())
                    val prevDay = prevStart.plusDays(i.toLong())
                    val curRange = Pair(
                        curDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                        curDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    )
                    val prevRange = Pair(
                        prevDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                        prevDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    )

                    val curVal = expenses.filter { it.timestamp >= curRange.first && it.timestamp < curRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    val prevVal = expenses.filter { it.timestamp >= prevRange.first && it.timestamp < prevRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    val label = curDay.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    DataPoint(label = label, currentValue = curVal, previousValue = prevVal)
                }
            }
            ComparisonPeriod.MONTH -> {
                val curStart = today.withDayOfMonth(1)
                val prevStart = curStart.minusMonths(1)
                val daysInCur = curStart.lengthOfMonth()
                val daysInPrev = prevStart.lengthOfMonth()
                val weekCount = 4 // Standard 4 weeks (Days 1–7, 8–14, 15–21, 22–end of month)
                (0 until weekCount).map { i ->
                    val startDay = i * 7 + 1
                    val endDayCur = if (i == 3) daysInCur else (i + 1) * 7
                    val endDayPrev = if (i == 3) daysInPrev else (i + 1) * 7
                    val curRange = Pair(
                        curStart.plusDays(startDay.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        curStart.plusDays(endDayCur.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                    )
                    val prevRange = Pair(
                        prevStart.plusDays(startDay.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        prevStart.plusDays(endDayPrev.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                    )

                    val curVal = expenses.filter { it.timestamp >= curRange.first && it.timestamp < curRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    val prevVal = expenses.filter { it.timestamp >= prevRange.first && it.timestamp < prevRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    DataPoint(label = "Week ${i + 1}", currentValue = curVal, previousValue = prevVal)
                }
            }
            ComparisonPeriod.YEAR -> {
                val curYear = today.year
                val prevYear = curYear - 1
                (1..12).map { month ->
                    val curYm = YearMonth.of(curYear, month)
                    val prevYm = YearMonth.of(prevYear, month)
                    val curRange = Pair(
                        curYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        curYm.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    )
                    val prevRange = Pair(
                        prevYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        prevYm.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    )

                    val curVal = expenses.filter { it.timestamp >= curRange.first && it.timestamp < curRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    val prevVal = expenses.filter { it.timestamp >= prevRange.first && it.timestamp < prevRange.second }
                        .sumOf { abs(it.amount.toDouble()) } / 100.0
                    val label = curYm.month.getDisplayName(TextStyle.SHORT, locale)
                    DataPoint(label = label, currentValue = curVal, previousValue = prevVal)
                }
            }
        }
    }
}
