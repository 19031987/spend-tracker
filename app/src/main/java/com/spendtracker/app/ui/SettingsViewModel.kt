package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.BackupRepository
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.ImportMode
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.domain.BackupCodec
import com.spendtracker.app.domain.BackupData
import com.spendtracker.app.domain.BackupFormatException
import com.spendtracker.app.domain.ImportPlan
import com.spendtracker.app.domain.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A decoded backup waiting for the user to choose Merge or Replace. */
data class PendingImport(
    val data: BackupData,
    val mergePlan: ImportPlan
)

data class SettingsUiState(
    val selectedTab: SettingsTab = SettingsTab.CATEGORIES,
    val isPerformingAction: Boolean = false,
    val feedbackMessage: String? = null,
    val pendingImport: PendingImport? = null,
    val importError: String? = null
)

enum class SettingsTab(val label: String) {
    CATEGORIES("Categories"),
    RULES("Rules"),
    ACCOUNTS("Accounts"),
    PREFERENCES("Preferences"),
    DATA("Data")
}

class SettingsViewModel(
    private val repository: SpendRepository,
    private val preferences: UserPreferences,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val groups: StateFlow<List<CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<MerchantRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transaction count per account id. */
    val accountTransactionCounts: StateFlow<Map<Long, Int>> = repository.observeAccountTransactionCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val currencySymbol: Flow<String> = preferences.observeCurrency()
    val themeMode: Flow<String> = preferences.observeTheme()
    val isBiometricLockEnabled: Flow<Boolean> = preferences.observeBiometricLock()

    fun getCurrentCurrency(): String = preferences.currencySymbol
    fun setCurrency(symbol: String) { preferences.currencySymbol = symbol }

    fun getDefaultPeriod(): Period = preferences.defaultPeriod
    fun setDefaultPeriod(period: Period) { preferences.defaultPeriod = period }

    fun getThemeMode(): String = preferences.themeMode
    fun setThemeMode(mode: String) { preferences.themeMode = mode }

    fun isBiometricEnabled(): Boolean = preferences.isBiometricLockEnabled
    fun setBiometricEnabled(enabled: Boolean) { preferences.isBiometricLockEnabled = enabled }

    fun selectTab(tab: SettingsTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /** Runs [block]; an [IllegalArgumentException]'s message is passed to [onDone] (null on success). */
    private fun runAction(onDone: (String?) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: IllegalArgumentException) {
                e.message ?: "Something went wrong"
            }
            onDone(error)
        }
    }

    // ---- Rules ----

    fun addRule(matchType: String, pattern: String, categoryKey: String) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            repository.addRule(matchType, pattern, categoryKey)
        }
    }

    fun updateRule(id: Long, matchType: String, pattern: String, categoryKey: String) {
        if (pattern.isBlank()) return
        runAction { repository.updateRule(id, matchType, pattern, categoryKey) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    // ---- Categories and groups ----

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

    fun updateCategory(key: String, name: String, emoji: String, colorHex: String, groupId: Long) {
        if (name.isBlank()) return
        runAction { repository.updateCategory(key, name, emoji, colorHex, groupId) }
    }

    /** Built-in categories are hidden (restorable); custom ones are deleted. */
    fun deleteCategory(key: String) {
        viewModelScope.launch {
            repository.deleteCategory(key, hide = true)
        }
    }

    fun restoreCategory(key: String) {
        viewModelScope.launch { repository.restoreCategory(key) }
    }

    fun updateGroup(id: Long, name: String, emoji: String) {
        if (name.isBlank()) return
        runAction { repository.updateGroup(id, name, emoji) }
    }

    fun deleteGroup(id: Long, moveToGroupId: Long, onDone: (String?) -> Unit) =
        runAction(onDone) { repository.deleteGroup(id, moveToGroupId) }

    // ---- Accounts ----

    fun addAccount(name: String, onDone: (String?) -> Unit) =
        runAction(onDone) { repository.addAccount(name) }

    fun renameAccount(id: Long, name: String, onDone: (String?) -> Unit) =
        runAction(onDone) { repository.renameAccount(id, name) }

    fun deleteAccount(id: Long, moveToAccountId: Long?, onDone: (String?) -> Unit) =
        runAction(onDone) { repository.deleteAccount(id, moveToAccountId) }

    // ---- Data ----

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

    /** Full backup (every table) as versioned JSON. */
    suspend fun getBackupJson(): String = backupRepository.exportBackupJson()
    suspend fun getExportCsv(): String = repository.exportCsv()

    /** Parses and validates a backup file's text, then shows the Merge / Replace confirmation. */
    fun loadImport(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPerformingAction = true, importError = null, pendingImport = null)
            try {
                val data = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
                val plan = backupRepository.preview(data, ImportMode.MERGE)
                _uiState.value = _uiState.value.copy(isPerformingAction = false, pendingImport = PendingImport(data, plan))
            } catch (e: BackupFormatException) {
                _uiState.value = _uiState.value.copy(isPerformingAction = false, importError = e.message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPerformingAction = false,
                    importError = "Couldn't read this backup: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun reportImportError(message: String) {
        _uiState.value = _uiState.value.copy(importError = message, pendingImport = null)
    }

    fun confirmImport(mode: ImportMode) {
        val pending = _uiState.value.pendingImport ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPerformingAction = true, pendingImport = null)
            val message = try {
                val plan = backupRepository.import(pending.data, mode)
                val added = plan.added
                if (mode == ImportMode.REPLACE) {
                    "Restored ${added.transactions} transactions and ${added.accounts} accounts."
                } else {
                    "Imported ${added.transactions} new transactions" +
                        (if (plan.skipped.transactions > 0) ", skipped ${plan.skipped.transactions} already here." else ".")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "Import failed, nothing was changed: ${e.message ?: e.javaClass.simpleName}"
            }
            _uiState.value = _uiState.value.copy(isPerformingAction = false, feedbackMessage = message)
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(pendingImport = null, importError = null)
    }

    fun showFeedback(message: String) {
        _uiState.value = _uiState.value.copy(feedbackMessage = message)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }
}
