package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBar
import com.spendtracker.app.domain.NewTransaction
import com.spendtracker.app.domain.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val period: Period = Period.WEEK,
    val isLoading: Boolean = true,
    val currentTotalMinor: Long = 0,
    val previousTotalMinor: Long = 0,
    /** Null when the previous period had no spend (percentage undefined) or during initial baseline period. */
    val deltaPercent: Double? = null,
    val bars: List<ChartBar> = emptyList(),
    val categories: List<CategoryShare> = emptyList(),
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

                AnalyticsUiState(
                    period = period,
                    isLoading = false,
                    currentTotalMinor = snapshot.currentTotal,
                    previousTotalMinor = snapshot.previousTotal,
                    deltaPercent = delta,
                    bars = snapshot.bars,
                    categories = snapshot.categories,
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
}

data class AddTransactionUiState(
    val isSaving: Boolean = false,
    val error: String? = null
)

class AddTransactionViewModel(private val repository: SpendRepository) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<com.spendtracker.app.data.CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.spendtracker.app.data.CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<com.spendtracker.app.data.MerchantRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<com.spendtracker.app.domain.HistoryEntry>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    fun evaluateCategory(merchant: String): com.spendtracker.app.domain.Classification {
        if (merchant.isBlank()) return com.spendtracker.app.domain.Classification.Unknown
        val domainRules = rules.value.mapNotNull { r ->
            runCatching {
                com.spendtracker.app.domain.MerchantRule(
                    id = r.id,
                    matchType = com.spendtracker.app.domain.MatchType.valueOf(r.matchType),
                    pattern = r.pattern,
                    categoryKey = r.categoryKey
                )
            }.getOrNull()
        }
        val validKeys = categories.value.filter { !it.isHidden }.map { it.key }.toSet()
        return com.spendtracker.app.domain.CategorizationEngine.classify(
            merchant = merchant,
            rules = domainRules,
            history = history.value,
            validKeys = validKeys
        )
    }

    fun createCategory(
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: com.spendtracker.app.data.TransactionType,
        ruleMerchant: String?,
        onComplete: (com.spendtracker.app.data.CategoryEntity) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createCategoryWithRule(
                    name = name,
                    groupId = groupId,
                    newGroupName = newGroupName,
                    emoji = emoji,
                    colorHex = colorHex,
                    type = type,
                    alwaysMatchMerchant = ruleMerchant
                )
                // Retrieve updated category
                val updatedCats = repository.observeCategories()
                // Wait briefly for insertion or synthesize CategoryEntity
                val fallbackCat = com.spendtracker.app.data.CategoryEntity(
                    key = "c_new",
                    groupId = groupId ?: 1L,
                    name = name,
                    emoji = emoji,
                    colorHex = colorHex,
                    type = type,
                    isBuiltIn = false,
                    isHidden = false
                )
                onComplete(fallbackCat)
            } catch (e: Exception) {
                _state.value = AddTransactionUiState(error = "Could not create category")
            }
        }
    }

    fun submit(transaction: NewTransaction, onSuccess: () -> Unit) {
        if (_state.value.isSaving) return
        _state.value = AddTransactionUiState(isSaving = true)
        viewModelScope.launch {
            try {
                repository.add(transaction)
                _state.value = AddTransactionUiState()
                onSuccess()
            } catch (e: IllegalArgumentException) {
                _state.value = AddTransactionUiState(error = e.message)
            } catch (e: Exception) {
                _state.value = AddTransactionUiState(error = "Could not save transaction")
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

class TransactionsViewModel(private val repository: SpendRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<com.spendtracker.app.data.CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.spendtracker.app.data.CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<com.spendtracker.app.domain.TransactionItem>> = repository.observeTransactionItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateTransaction(
        id: Long,
        type: com.spendtracker.app.data.TransactionType,
        accountId: Long,
        destinationAccountId: Long?,
        amountMinor: Long,
        categoryKey: String?,
        merchant: String?,
        note: String?,
        excludeFromSpending: Boolean,
        ruleMerchant: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(
                    id = id,
                    type = type,
                    accountId = accountId,
                    destinationAccountId = destinationAccountId,
                    amountMinor = amountMinor,
                    categoryKey = categoryKey,
                    merchant = merchant,
                    note = note,
                    excludeFromSpending = excludeFromSpending
                )
                if (!ruleMerchant.isNullOrBlank() && !categoryKey.isNullOrBlank()) {
                    repository.addRule("EXACT", ruleMerchant.trim(), categoryKey)
                }
                onSuccess()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun quickSetCategory(
        id: Long,
        categoryKey: String,
        ruleMerchant: String? = null
    ) {
        viewModelScope.launch {
            try {
                val current = transactions.value.firstOrNull { it.id == id } ?: return@launch
                repository.updateTransaction(
                    id = id,
                    type = current.type,
                    accountId = current.accountId,
                    destinationAccountId = current.destinationAccountId,
                    amountMinor = current.amountMinor,
                    categoryKey = categoryKey,
                    merchant = current.merchant,
                    note = current.note,
                    excludeFromSpending = current.excludeFromSpending || categoryKey == "INTERNAL_TRANSFER"
                )
                if (!ruleMerchant.isNullOrBlank()) {
                    repository.addRule("EXACT", ruleMerchant.trim(), categoryKey)
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deleteTransaction(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                onSuccess()
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}

