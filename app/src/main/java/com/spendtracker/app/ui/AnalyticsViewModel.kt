package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.AnalyticsSnapshot
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBar
import com.spendtracker.app.domain.Period
import com.spendtracker.app.domain.PeriodCalculator
import com.spendtracker.app.ui.util.CurrencyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class SpendingTrendInsight(
    val title: String,
    val description: String,
    val emoji: String
)

data class AnalyticsUiState(
    val period: Period = Period.WEEK,
    val isLoading: Boolean = true,
    val currentTotalMinor: Long = 0,
    val previousTotalMinor: Long = 0,
    /** Null when the previous period had no spend (percentage undefined) or during initial baseline period. */
    val deltaPercent: Double? = null,
    val dailyAverageMinor: Long = 0,
    val projectedPeriodTotalMinor: Long? = null,
    val totalIncomeMinor: Long = 0,
    val netSavingsMinor: Long = 0,
    val savingsRatePercent: Double? = null,
    val bars: List<ChartBar> = emptyList(),
    val categories: List<CategoryShare> = emptyList(),
    val insights: List<SpendingTrendInsight> = emptyList(),
    val error: String? = null,
    val isInitialPeriod: Boolean = false,
    val onboardingMessage: String = "Building your baseline: comparisons will appear after your first week/month"
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val repository: SpendRepository) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(Period.WEEK)

    val uiState: StateFlow<AnalyticsUiState> = selectedPeriod
        .flatMapLatest { period ->
            combine(
                repository.observeAnalytics(period),
                repository.observeTransactions()
            ) { snapshot, transactions ->
                val oldestTs = transactions.minOfOrNull { it.timestamp }
                val historyDays = if (oldestTs != null) {
                    val diff = System.currentTimeMillis() - oldestTs
                    maxOf(0L, diff / (24L * 60 * 60 * 1000L))
                } else 0L

                val requiredDays = when (period) {
                    Period.WEEK -> 7L
                    Period.MONTH -> 30L
                    Period.YEAR -> 30L
                }
                val isInitial = (historyDays < requiredDays) || (snapshot.previousTotal <= 0L && historyDays < 30L)

                val delta = if (!isInitial && snapshot.previousTotal > 0) {
                    (snapshot.currentTotal - snapshot.previousTotal) * 100.0 / snapshot.previousTotal
                } else null

                val today = LocalDate.now()
                val daysElapsed = calculateDaysElapsed(period, today)
                val totalDays = calculateTotalDaysInPeriod(period, today)
                val dailyAverage = if (daysElapsed > 0) snapshot.currentTotal / daysElapsed else 0L
                val projectedTotal = dailyAverage * totalDays

                val firstDayOfWeek = DayOfWeek.MONDAY
                val currentWindow = PeriodCalculator.current(period, today, firstDayOfWeek)
                val periodRange = currentWindow.toRange(ZoneId.systemDefault())

                val currentTxs = transactions.filter {
                    it.timestamp >= periodRange.start &&
                    it.timestamp < periodRange.end &&
                    !it.excludeFromSpending
                }

                val totalIncome = currentTxs
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { abs(it.amount) }

                val netSavings = totalIncome - snapshot.currentTotal
                val savingsRate = if (totalIncome > 0) (netSavings * 100.0 / totalIncome) else null

                val insights = generateInsights(period, delta, snapshot, currentTxs)

                AnalyticsUiState(
                    period = period,
                    isLoading = false,
                    currentTotalMinor = snapshot.currentTotal,
                    previousTotalMinor = snapshot.previousTotal,
                    deltaPercent = delta,
                    dailyAverageMinor = dailyAverage,
                    projectedPeriodTotalMinor = projectedTotal,
                    totalIncomeMinor = totalIncome,
                    netSavingsMinor = netSavings,
                    savingsRatePercent = savingsRate,
                    bars = snapshot.bars,
                    categories = snapshot.categories,
                    insights = insights,
                    isInitialPeriod = isInitial
                )
            }
                .onStart { emit(AnalyticsUiState(period = period, isLoading = true)) }
                .catch { e ->
                    emit(
                        AnalyticsUiState(
                            period = period,
                            isLoading = false,
                            error = e.message ?: "Could not load analytics"
                        )
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun onPeriodSelected(period: Period) {
        selectedPeriod.update { period }
    }

    companion object {
        fun calculateDaysElapsed(period: Period, today: LocalDate = LocalDate.now()): Long = when (period) {
            Period.WEEK -> {
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (ChronoUnit.DAYS.between(startOfWeek, today) + 1).coerceIn(1, 7)
            }
            Period.MONTH -> today.dayOfMonth.toLong()
            Period.YEAR -> today.dayOfYear.toLong()
        }

        fun calculateTotalDaysInPeriod(period: Period, today: LocalDate = LocalDate.now()): Long = when (period) {
            Period.WEEK -> 7L
            Period.MONTH -> today.lengthOfMonth().toLong()
            Period.YEAR -> today.lengthOfYear().toLong()
        }

        fun generateInsights(
            period: Period,
            deltaPercent: Double?,
            snapshot: AnalyticsSnapshot,
            currentTxs: List<TransactionEntity>
        ): List<SpendingTrendInsight> {
            val insights = mutableListOf<SpendingTrendInsight>()
            if (snapshot.currentTotal <= 0) return insights

            // 1. Pacing Insight
            if (deltaPercent != null) {
                val pacingText = if (deltaPercent > 5.0) {
                    "Spending is running %.1f%% above previous %s.".format(deltaPercent, period.label.lowercase())
                } else if (deltaPercent < -5.0) {
                    "Spending is %.1f%% lower than previous %s. Great pacing!".format(abs(deltaPercent), period.label.lowercase())
                } else {
                    "Spending is on par with previous %s.".format(period.label.lowercase())
                }
                val emoji = if (deltaPercent > 5.0) "⚠️" else "✨"
                insights.add(SpendingTrendInsight("Pacing & Run-Rate", pacingText, emoji))
            }

            // 2. Top Merchant Insight
            val expenseTxs = currentTxs.filter {
                it.type == TransactionType.EXPENSE &&
                !it.merchant.isNullOrBlank() &&
                (it.category == null || it.category != "INTERNAL_TRANSFER")
            }
            val topMerchantGroup = expenseTxs.groupBy { it.merchant!!.trim() }
                .maxByOrNull { (_, list) -> list.sumOf { abs(it.amount) } }

            if (topMerchantGroup != null) {
                val merchantAmount = topMerchantGroup.value.sumOf { abs(it.amount) }
                val merchantPct = (merchantAmount * 100.0 / snapshot.currentTotal).toInt()
                val formattedAmount = CurrencyUtils.formatMinor(merchantAmount)
                insights.add(
                    SpendingTrendInsight(
                        title = "Top Merchant",
                        description = "${topMerchantGroup.key} represents $merchantPct% of total spend ($formattedAmount).",
                        emoji = "🏆"
                    )
                )
            }

            // 3. Peak Spend Point Insight
            val peakBar = snapshot.bars.maxByOrNull { it.current }
            if (peakBar != null && peakBar.current > 0) {
                val formattedPeak = CurrencyUtils.formatMinor(peakBar.current)
                insights.add(
                    SpendingTrendInsight(
                        title = "Peak Spend Day",
                        description = "${peakBar.label} was your highest spend point with $formattedPeak.",
                        emoji = "📅"
                    )
                )
            }

            return insights
        }
    }
}
