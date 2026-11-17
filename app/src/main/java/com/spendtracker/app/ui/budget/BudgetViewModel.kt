package com.spendtracker.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.PlanningRepository
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.budget.BudgetCalculator
import com.spendtracker.app.domain.budget.BudgetSummary
import com.spendtracker.app.domain.budget.suggestedBudgets
import com.spendtracker.app.domain.todayFlow
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A suggested budget for a category that has none yet (average of the previous 3 months). */
data class SuggestedBudget(
    val categoryKey: String,
    val name: String,
    val emoji: String,
    val amountMinor: Long
)

data class BudgetUiState(
    val month: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val summary: BudgetSummary? = null,
    val budgets: List<BudgetEntity> = emptyList(),
    /** Suggestions for expense categories that have no budget; used by the empty state. */
    val suggestions: List<SuggestedBudget> = emptyList(),
    /** Visible expense categories with no budget and no spending this month. */
    val unusedCategories: List<CategoryEntity> = emptyList()
) {
    val hasBudgets: Boolean get() = budgets.isNotEmpty()
}

class BudgetViewModel(
    private val repository: SpendRepository,
    private val planningRepository: PlanningRepository
) : ViewModel() {

    /** Null = follow the current month (it moves on by itself at month end). */
    private val selectedMonth: MutableStateFlow<YearMonth?> = MutableStateFlow(null)

    private data class Inputs(
        val budgets: List<BudgetEntity>,
        val categories: List<CategoryEntity>,
        val groups: List<CategoryGroupEntity>,
        val transactions: List<TransactionItem>
    )

    private val inputs = combine(
        planningRepository.observeBudgets(),
        repository.observeCategories(),
        repository.observeGroups(),
        repository.observeTransactionItems()
    ) { budgets, categories, groups, transactions -> Inputs(budgets, categories, groups, transactions) }

    val uiState: StateFlow<BudgetUiState> = combine(
        selectedMonth,
        todayFlow(),
        inputs
    ) { selected, today, input ->
        val budgets: List<BudgetEntity> = input.budgets
        val categories: List<CategoryEntity> = input.categories
        val transactions: List<TransactionItem> = input.transactions
        val month: YearMonth = selected ?: YearMonth.from(today)
        val zone: ZoneId = ZoneId.systemDefault()
        val summary: BudgetSummary = BudgetCalculator.summarize(
            month = month,
            budgets = budgets,
            categories = categories,
            groups = input.groups,
            transactions = transactions,
            today = today,
            zone = zone
        )
        val budgetedKeys: Set<String> = budgets.map { it.categoryKey }.toSet()
        val suggestionMap: Map<String, Long> = suggestedBudgets(categories, transactions, month, zone)
        val categoryByKey: Map<String, CategoryEntity> = categories.associateBy { it.key }
        val suggestions: List<SuggestedBudget> = suggestionMap
            .filterKeys { it !in budgetedKeys }
            .mapNotNull { (key, amount) ->
                val category: CategoryEntity = categoryByKey[key] ?: return@mapNotNull null
                if (category.isHidden) return@mapNotNull null
                SuggestedBudget(key, category.name, category.emoji, amount)
            }
            .sortedByDescending { it.amountMinor }
        val shownKeys: Set<String> =
            (summary.sections.flatMap { it.lines } + summary.unbudgeted).map { it.categoryKey }.toSet()
        val unused: List<CategoryEntity> = categories.filter {
            it.type == TransactionType.EXPENSE && !it.isHidden &&
                it.key !in budgetedKeys && it.key !in shownKeys
        }
        BudgetUiState(
            month = month,
            isLoading = false,
            summary = summary,
            budgets = budgets,
            suggestions = suggestions,
            unusedCategories = unused
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun previousMonth() {
        moveMonth(-1L)
    }

    fun nextMonth() {
        moveMonth(1L)
    }

    private fun moveMonth(by: Long) {
        val current: YearMonth = YearMonth.now()
        val target: YearMonth = (selectedMonth.value ?: current).plusMonths(by)
        // Back on the current month: follow it again so it rolls over at month end.
        selectedMonth.value = if (target == current) null else target
    }

    fun setBudget(categoryKey: String, amountMinor: Long, rollover: Boolean) {
        viewModelScope.launch {
            planningRepository.setBudget(categoryKey, amountMinor, rollover)
        }
    }

    fun removeBudget(categoryKey: String) {
        viewModelScope.launch {
            planningRepository.removeBudget(categoryKey)
        }
    }

    /** Writes the suggested budget (no rollover) for every category that has none yet. */
    fun applySuggestedBudgets() {
        val suggestions: List<SuggestedBudget> = uiState.value.suggestions
        if (suggestions.isEmpty()) return
        viewModelScope.launch {
            for (suggestion in suggestions) {
                planningRepository.setBudget(suggestion.categoryKey, suggestion.amountMinor, false)
            }
        }
    }
}
