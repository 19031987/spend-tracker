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
import com.spendtracker.app.domain.firstDayOfWeek
import com.spendtracker.app.domain.isIncome
import com.spendtracker.app.domain.netSpendInRange
import com.spendtracker.app.domain.todayFlow
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
    /** Previous period's spend over the same number of elapsed days (like-for-like; used for [deltaPercent]). */
    val previousTotalMinor: Long = 0,
    /** Previous period's whole spend, for reference. */
    val previousFullPeriodTotalMinor: Long = 0,
    /** True while the current period is still in progress, i.e. [previousTotalMinor] is "same point last period". */
    val isPartialPeriod: Boolean = false,
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

    // todayFlow re-emits after midnight so the windows (and the repository snapshot) are rebuilt.
    val uiState: StateFlow<AnalyticsUiState> = combine(selectedPeriod, todayFlow()) { period, today -> period to today }
        .flatMapLatest { (period, today) ->
            combine(
                repository.observeAnalytics(period),
                repository.observeTransactions()
            ) { snapshot, transactions ->
                buildState(period, today, snapshot, transactions, ZoneId.systemDefault(), firstDayOfWeek())
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
        /**
         * Pure state builder. Spend totals use the shared rule (spending minus refunds; refunds are
         * not income). The delta compares like-for-like: the current period so far against the
         * same number of days at the start of the previous period.
         */
        fun buildState(
            period: Period,
            today: LocalDate,
            snapshot: AnalyticsSnapshot,
            transactions: List<TransactionEntity>,
            zone: ZoneId,
            firstDay: DayOfWeek,
            nowMillis: Long = System.currentTimeMillis()
        ): AnalyticsUiState {
            val oldestTs = transactions.minOfOrNull { it.timestamp }
            val historyDays = if (oldestTs != null) {
                maxOf(0L, (nowMillis - oldestTs) / (24L * 60 * 60 * 1000L))
            } else 0L

            val requiredDays = when (period) {
                Period.WEEK -> 7L
                Period.MONTH -> 30L
                Period.YEAR -> 30L
            }

            val currentWindow = PeriodCalculator.current(period, today, firstDay)
            val previousWindow = PeriodCalculator.previous(currentWindow)
            val currentRange = currentWindow.toRange(zone)
            val previousToDateRange = PeriodCalculator.previousToDate(currentWindow, today).toRange(zone)

            val currentTotal = netSpendInRange(transactions, currentRange)
            val previousToDate = netSpendInRange(transactions, previousToDateRange)
            val previousFull = netSpendInRange(transactions, previousWindow.toRange(zone))

            val isInitial = (historyDays < requiredDays) || (previousFull <= 0L && historyDays < 30L)

            val delta = if (!isInitial && previousToDate > 0) {
                (currentTotal - previousToDate) * 100.0 / previousToDate
            } else null

            val daysElapsed = calculateDaysElapsed(period, today, firstDay)
            val totalDays = calculateTotalDaysInPeriod(period, today)
            val dailyAverage = if (daysElapsed > 0) currentTotal / daysElapsed else 0L
            val projectedTotal = dailyAverage * totalDays

            val currentTxs = transactions.filter {
                it.timestamp >= currentRange.start &&
                    it.timestamp < currentRange.end &&
                    !it.excludeFromSpending
            }

            val totalIncome = currentTxs
                .filter { it.isIncome() }
                .sumOf { abs(it.amount) }

            val netSavings = totalIncome - currentTotal
            val savingsRate = if (totalIncome > 0) (netSavings * 100.0 / totalIncome) else null

            val adjustedSnapshot = snapshot.copy(currentTotal = currentTotal, previousTotal = previousToDate)
            val insights = generateInsights(period, delta, adjustedSnapshot, currentTxs)

            return AnalyticsUiState(
                period = period,
                isLoading = false,
                currentTotalMinor = currentTotal,
                previousTotalMinor = previousToDate,
                previousFullPeriodTotalMinor = previousFull,
                isPartialPeriod = PeriodCalculator.currentToDate(currentWindow, today).endExclusive != currentWindow.endExclusive,
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

        fun calculateDaysElapsed(
            period: Period,
            today: LocalDate = LocalDate.now(),
            firstDay: DayOfWeek = firstDayOfWeek()
        ): Long = when (period) {
            Period.WEEK -> {
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
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
