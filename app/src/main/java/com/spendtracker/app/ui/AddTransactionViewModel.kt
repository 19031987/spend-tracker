package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.CategorizationEngine
import com.spendtracker.app.domain.Classification
import com.spendtracker.app.domain.HistoryEntry
import com.spendtracker.app.domain.MatchType
import com.spendtracker.app.domain.MerchantRule
import com.spendtracker.app.domain.NewTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val isSaving: Boolean = false,
    val error: String? = null
)

class AddTransactionViewModel(private val repository: SpendRepository) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<MerchantRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    fun evaluateCategory(merchant: String): Classification {
        if (merchant.isBlank()) return Classification.Unknown
        val domainRules = rules.value.mapNotNull { r ->
            runCatching {
                MerchantRule(
                    id = r.id,
                    matchType = MatchType.valueOf(r.matchType),
                    pattern = r.pattern,
                    categoryKey = r.categoryKey
                )
            }.getOrNull()
        }
        val validKeys = categories.value.filter { !it.isHidden }.map { it.key }.toSet()
        return CategorizationEngine.classify(
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
        type: TransactionType,
        ruleMerchant: String?,
        onComplete: (CategoryEntity) -> Unit
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
                val fallbackCat = CategoryEntity(
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
