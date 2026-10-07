package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.domain.Period
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedTab: SettingsTab = SettingsTab.CATEGORIES,
    val isPerformingAction: Boolean = false,
    val feedbackMessage: String? = null
)

enum class SettingsTab(val label: String) {
    CATEGORIES("Categories & Groups"),
    RULES("Merchant Rules"),
    PREFERENCES("Preferences"),
    DATA("Data Actions")
}

class SettingsViewModel(
    private val repository: SpendRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val groups: StateFlow<List<CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<MerchantRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currencySymbol: Flow<String> = preferences.observeCurrency()
    val themeMode: Flow<String> = preferences.observeTheme()

    fun getCurrentCurrency(): String = preferences.currencySymbol
    fun setCurrency(symbol: String) { preferences.currencySymbol = symbol }

    fun getDefaultPeriod(): Period = preferences.defaultPeriod
    fun setDefaultPeriod(period: Period) { preferences.defaultPeriod = period }

    fun getThemeMode(): String = preferences.themeMode
    fun setThemeMode(mode: String) { preferences.themeMode = mode }

    fun selectTab(tab: SettingsTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun addRule(matchType: String, pattern: String, categoryKey: String) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            repository.addRule(matchType, pattern, categoryKey)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun addCategory(
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType = TransactionType.EXPENSE,
        ruleMerchant: String? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createCategoryWithRule(
                name = name,
                groupId = groupId,
                newGroupName = newGroupName,
                emoji = emoji,
                colorHex = colorHex,
                type = type,
                alwaysMatchMerchant = ruleMerchant
            )
        }
    }

    fun deleteCategory(key: String) {
        viewModelScope.launch {
            repository.deleteCategory(key, hide = true)
        }
    }

    fun resetDemoData(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPerformingAction = true)
            repository.resetDemoData()
            _uiState.value = _uiState.value.copy(
                isPerformingAction = false,
                feedbackMessage = "Demo data has been reset to defaults."
            )
            onComplete()
        }
    }

    suspend fun getExportJson(): String = repository.exportJson()
    suspend fun getExportCsv(): String = repository.exportCsv()

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }
}
