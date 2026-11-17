package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.domain.Period
import com.spendtracker.app.domain.PeriodCalculator
import com.spendtracker.app.domain.TimeRange
import com.spendtracker.app.domain.firstDayOfWeek
import com.spendtracker.app.domain.netSpendInRange
import com.spendtracker.app.domain.netSpendMinor
import com.spendtracker.app.domain.refundAttributionOfEntities
import com.spendtracker.app.domain.spendCategoryKey
import com.spendtracker.app.domain.todayFlow
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
import java.util.Locale
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
    val onboardingMessage: String = "Building your baseline: comparisons will appear after your first week/month",
    /** False when neither period has any spending: show [emptyMessage] instead of the chart/list. */
    val hasData: Boolean = true,
    val emptyMessage: String = "Not enough data yet"
)

/** Result of one comparison: the breakdown plus like-for-like totals (see [ComparisonViewModel.computeComparison]). */
data class ComparisonResult(
    val breakdown: List<DataPoint>,
    /** Spend so far in the current period. */
    val currentTotal: Double,
    /** Spend over the same number of days at the start of the previous period. */
    val previousTotal: Double,
    val hasData: Boolean
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

    /** Refreshed by [todayFlow] so the windows move on after midnight. */
    private var currentDate: LocalDate = LocalDate.now()

    init {
        if (repository != null) {
            viewModelScope.launch {
                combine(
                    repository.observeTransactions(),
                    repository.observeCategories(),
                    todayFlow()
                ) { txs, cats, today ->
                    Triple(txs, cats, today)
                }.collect { (txs, cats, today) ->
                    currentDate = today
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

        val result = computeComparison(
            period = period,
            mode = mode,
            transactions = cachedTransactions,
            categoriesMap = cachedCategories,
            today = if (repository != null) currentDate else LocalDate.now()
        )
        val currentTotal = result.currentTotal
        val previousTotal = result.previousTotal

        val isInitial = initialPeriodOverride ?: if (repository != null) {
            isInitialBaseline(_uiState.value.historyDays, period)
        } else {
            _uiState.value.isInitialPeriod
        }

        val pctChange = when {
            isInitial || !result.hasData -> 0.0
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
                breakdownItems = result.breakdown,
                isLoading = false,
                isInitialPeriod = isInitial,
                hasData = result.hasData
            )
        }
    }

    /**
     * Pure comparison. Totals are always like-for-like: the current period so far against the
     * same number of days at the start of the previous period (on 3 March: 1-3 March vs 1-3 Feb).
     * By-category bars use the same windows; by-time bars compare slot by slot (day, week, month).
     * Spend = spending minus refunds (refunds are never income). No data -> empty breakdown and
     * hasData = false (no sample data).
     */
    fun computeComparison(
        period: ComparisonPeriod,
        mode: ComparisonMode,
        transactions: List<TransactionEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        firstDay: DayOfWeek = firstDayOfWeek(locale)
    ): ComparisonResult {
        val curWindow = PeriodCalculator.current(period.toDomain(), today, firstDay)
        val curRange = PeriodCalculator.currentToDate(curWindow, today).toRange(zone)
        val prevRange = PeriodCalculator.previousToDate(curWindow, today).toRange(zone)

        val breakdown = if (mode == ComparisonMode.CATEGORIES) {
            calculateCategoryComparison(transactions, categoriesMap, curRange, prevRange)
        } else {
            calculateIntervalComparison(period, transactions, today, zone, locale, firstDay)
        }
        return ComparisonResult(
            breakdown = breakdown,
            currentTotal = netSpendInRange(transactions, curRange) / 100.0,
            previousTotal = netSpendInRange(transactions, prevRange) / 100.0,
            hasData = breakdown.any { it.currentValue > 0.0 || it.previousValue > 0.0 }
        )
    }

    private fun ComparisonPeriod.toDomain(): Period = when (this) {
        ComparisonPeriod.WEEK -> Period.WEEK
        ComparisonPeriod.MONTH -> Period.MONTH
        ComparisonPeriod.YEAR -> Period.YEAR
    }

    private fun calculateCategoryComparison(
        transactions: List<TransactionEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        curRange: TimeRange,
        prevRange: TimeRange
    ): List<DataPoint> {
        val attribution = refundAttributionOfEntities(transactions)

        fun byCategory(range: TimeRange): Map<String, Double> {
            val sums = HashMap<String, Long>()
            for (tx in transactions) {
                if (tx.timestamp < range.start || tx.timestamp >= range.end) continue
                val key = tx.spendCategoryKey(attribution) ?: continue
                sums[key] = (sums[key] ?: 0L) + tx.netSpendMinor()
            }
            return sums.mapValues { (_, minor) -> max(0L, minor) / 100.0 }
        }

        val curByCategory = byCategory(curRange)
        val prevByCategory = byCategory(prevRange)

        return (curByCategory.keys + prevByCategory.keys).toSet().map { catKey ->
            val entity = categoriesMap[catKey]
            val emoji = entity?.emoji ?: "🏷️"
            val name = entity?.name ?: catKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            DataPoint(
                label = "$emoji $name",
                currentValue = curByCategory[catKey] ?: 0.0,
                previousValue = prevByCategory[catKey] ?: 0.0
            )
        }
            .filter { it.currentValue > 0.0 || it.previousValue > 0.0 }
            .sortedByDescending { max(it.currentValue, it.previousValue) }
    }

    fun calculateIntervalComparison(
        period: ComparisonPeriod,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        firstDay: DayOfWeek = firstDayOfWeek(locale)
    ): List<DataPoint> {
        // Spending minus refunds; slots never go below zero.
        val counted = transactions.filter { it.netSpendMinor() != 0L }
        fun spendIn(start: Long, end: Long): Double =
            max(0L, counted.filter { it.timestamp >= start && it.timestamp < end }.sumOf { it.netSpendMinor() }) / 100.0

        return when (period) {
            ComparisonPeriod.WEEK -> {
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

                    val curVal = spendIn(curRange.first, curRange.second)
                    val prevVal = spendIn(prevRange.first, prevRange.second)
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

                    val curVal = spendIn(curRange.first, curRange.second)
                    val prevVal = spendIn(prevRange.first, prevRange.second)
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

                    val curVal = spendIn(curRange.first, curRange.second)
                    val prevVal = spendIn(prevRange.first, prevRange.second)
                    val label = curYm.month.getDisplayName(TextStyle.SHORT, locale)
                    DataPoint(label = label, currentValue = curVal, previousValue = prevVal)
                }
            }
        }
    }
}
