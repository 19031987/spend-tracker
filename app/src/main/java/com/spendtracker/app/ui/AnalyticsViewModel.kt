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
    /** Null when the previous period had no spend (percentage undefined). */
    val deltaPercent: Double? = null,
    val bars: List<ChartBar> = emptyList(),
    val categories: List<CategoryShare> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val repository: SpendRepository) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(Period.WEEK)

    val uiState: StateFlow<AnalyticsUiState> = selectedPeriod
        .flatMapLatest { period ->
            repository.observeAnalytics(period)
                .map { snapshot ->
                    val delta = if (snapshot.previousTotal > 0) {
                        (snapshot.currentTotal - snapshot.previousTotal) * 100.0 / snapshot.previousTotal
                    } else null
                    AnalyticsUiState(
                        period = period,
                        isLoading = false,
                        currentTotalMinor = snapshot.currentTotal,
                        previousTotalMinor = snapshot.previousTotal,
                        deltaPercent = delta,
                        bars = snapshot.bars,
                        categories = snapshot.categories
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

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

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
