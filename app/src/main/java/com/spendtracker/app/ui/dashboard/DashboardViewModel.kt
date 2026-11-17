package com.spendtracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import com.spendtracker.app.data.PlanningRepository
import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.budget.BudgetCalculator
import com.spendtracker.app.domain.budget.BudgetSummary
import com.spendtracker.app.domain.dashboard.MonthComparison
import com.spendtracker.app.domain.dashboard.MonthComparisonCalculator
import com.spendtracker.app.domain.dashboard.MonthSummary
import com.spendtracker.app.domain.dashboard.MonthSummaryCalculator
import com.spendtracker.app.domain.dashboard.TopCategory
import com.spendtracker.app.domain.goals.GoalCalculator
import com.spendtracker.app.domain.goals.GoalProgress
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringDetector
import com.spendtracker.app.domain.recurring.RecurringOccurrence
import com.spendtracker.app.domain.todayFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val monthSummary: MonthSummary = MonthSummary(YearMonth.now(), 0L, 0L, 0L),
    /** This month-to-date against the same days of last month. */
    val comparison: MonthComparison? = null,
    /** Null when the user has no budgets. */
    val budget: BudgetSummary? = null,
    val reviewCount: Int = 0,
    val upcoming: List<RecurringOccurrence> = emptyList(),
    val topCategories: List<TopCategory> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val recent: List<TransactionItem> = emptyList()
)

private data class SpendInputs(
    val transactions: List<TransactionItem>,
    val categories: List<CategoryEntity>,
    val groups: List<CategoryGroupEntity>,
    val budgets: List<BudgetEntity>
)

private data class PlanningInputs(
    val recurring: List<RecurringItemEntity>,
    val goals: List<GoalEntity>,
    val contributions: List<GoalContributionEntity>
)

class DashboardViewModel(
    private val repository: SpendRepository,
    private val planningRepository: PlanningRepository
) : ViewModel() {

    private val spendInputs = combine(
        repository.observeTransactionItems(),
        repository.observeCategories(),
        repository.observeGroups(),
        planningRepository.observeBudgets()
    ) { transactions, categories, groups, budgets ->
        SpendInputs(transactions, categories, groups, budgets)
    }

    private val planningInputs = combine(
        planningRepository.observeRecurring(),
        planningRepository.observeGoals(),
        planningRepository.observeContributions()
    ) { recurring, goals, contributions ->
        PlanningInputs(recurring, goals, contributions)
    }

    // todayFlow re-emits after midnight, so "today", the current month and statuses never go stale.
    val uiState: StateFlow<DashboardUiState> = combine(spendInputs, planningInputs, todayFlow()) { spend, planning, today ->
        buildState(spend, planning, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun buildState(spend: SpendInputs, planning: PlanningInputs, today: LocalDate): DashboardUiState {
        val zone: ZoneId = ZoneId.systemDefault()
        val month: YearMonth = YearMonth.from(today)
        val transactions: List<TransactionItem> = spend.transactions

        val budget: BudgetSummary? = if (spend.budgets.isEmpty()) {
            null
        } else {
            BudgetCalculator.summarize(
                month = month,
                budgets = spend.budgets,
                categories = spend.categories,
                groups = spend.groups,
                transactions = transactions,
                today = today,
                zone = zone
            )
        }

        val upcoming: List<RecurringOccurrence> =
            upcomingOccurrences(planning.recurring, transactions, today, zone, MAX_UPCOMING)

        val goals: List<GoalProgress> = GoalCalculator
            .summary(planning.goals, planning.contributions, today, zone)
            .take(MAX_GOALS)

        val recent: List<TransactionItem> = transactions
            .filter { it.type != TransactionType.TRANSFER && it.categoryKey != "INTERNAL_TRANSFER" }
            .sortedByDescending { it.timestamp }
            .take(MAX_RECENT)

        return DashboardUiState(
            isLoading = false,
            today = today,
            monthSummary = MonthSummaryCalculator.summarize(transactions, month, zone),
            comparison = MonthComparisonCalculator.compare(transactions, month, today, zone),
            budget = budget,
            reviewCount = transactions.count { it.needsReview },
            upcoming = upcoming,
            topCategories = MonthSummaryCalculator.topCategories(transactions, month, zone, limit = MAX_TOP_CATEGORIES),
            goals = goals,
            recent = recent
        )
    }

    companion object {
        private const val UPCOMING_WINDOW_DAYS: Long = 7L

        /**
         * Due/overdue recurring payments for Home: everything still unpaid up to a week ahead,
         * including last month's overdue ones (so they do not vanish on the 1st). Overdue first
         * by date, then upcoming.
         */
        fun upcomingOccurrences(
            recurring: List<RecurringItemEntity>,
            transactions: List<TransactionItem>,
            today: LocalDate,
            zone: ZoneId,
            limit: Int
        ): List<RecurringOccurrence> {
            val month: YearMonth = YearMonth.from(today)
            val windowEnd: LocalDate = today.plusDays(UPCOMING_WINDOW_DAYS)
            val months: List<YearMonth> = listOf(month.minusMonths(1), month, YearMonth.from(windowEnd)).distinct()
            return months
                .flatMap { m -> RecurringDetector.occurrencesInMonth(recurring, m, transactions, today, zone) }
                .filter { occurrence ->
                    (occurrence.status == OccurrenceStatus.UPCOMING || occurrence.status == OccurrenceStatus.OVERDUE) &&
                        !occurrence.expectedDate.isAfter(windowEnd)
                }
                .sortedWith(compareBy<RecurringOccurrence> { it.expectedDate }.thenBy { it.displayName })
                .take(limit)
        }

        private const val MAX_UPCOMING: Int = 4
        private const val MAX_GOALS: Int = 3
        private const val MAX_RECENT: Int = 5
        private const val MAX_TOP_CATEGORIES: Int = 5
    }
}
