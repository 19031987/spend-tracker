package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.NewTransaction
import com.spendtracker.app.domain.TransactionItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** One-shot messages for the transactions UI (shown as a Snackbar). */
sealed interface TransactionEvent {
    val message: String

    data class Info(override val message: String) : TransactionEvent

    data class Failed(override val message: String) : TransactionEvent

    /** A transaction was deleted; [restorable] means [TransactionsViewModel.undoDelete] can bring it back. */
    data class Deleted(
        val item: TransactionItem,
        val restorable: Boolean,
        override val message: String = "Transaction deleted"
    ) : TransactionEvent

    /**
     * The category of a single transaction was changed from the quick picker. The UI offers
     * to always categorise [merchant] this way; nothing is remembered unless the user accepts.
     */
    data class CategoryChanged(
        val merchant: String,
        val categoryKey: String,
        val categoryName: String,
        override val message: String
    ) : TransactionEvent
}

class TransactionsViewModel(private val repository: SpendRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionItem>> = repository.observeTransactionItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<TransactionEvent>(Channel.BUFFERED)
    val events: Flow<TransactionEvent> = _events.receiveAsFlow()

    fun updateTransaction(
        id: Long,
        type: TransactionType,
        accountId: Long,
        destinationAccountId: Long?,
        amountMinor: Long,
        categoryKey: String?,
        merchant: String?,
        note: String?,
        excludeFromSpending: Boolean,
        ruleMerchant: String? = null,
        timestamp: Long? = null,
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
                    excludeFromSpending = excludeFromSpending,
                    timestamp = timestamp
                )
                if (!ruleMerchant.isNullOrBlank() && !categoryKey.isNullOrBlank()) {
                    addRuleIfMissing(ruleMerchant, categoryKey)
                }
                onSuccess()
                _events.send(TransactionEvent.Info("Transaction saved"))
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't save the transaction. Try again."))
            }
        }
    }

    /**
     * Sets the category of one transaction. Never creates a merchant rule by itself: when the
     * transaction has a merchant, a [TransactionEvent.CategoryChanged] lets the UI ask first.
     */
    fun quickSetCategory(id: Long, categoryKey: String, offerRule: Boolean = true) {
        viewModelScope.launch {
            try {
                val current = transactions.value.firstOrNull { it.id == id } ?: return@launch
                val target = quickCategoryTarget(current, categoryKey, categories.value)
                repository.updateTransaction(
                    id = id,
                    type = target.type,
                    accountId = current.accountId,
                    destinationAccountId = target.destinationAccountId,
                    amountMinor = current.amountMinor,
                    categoryKey = categoryKey,
                    merchant = current.merchant,
                    note = current.note,
                    excludeFromSpending = target.excludeFromSpending
                )
                val catName = categories.value.firstOrNull { it.key == categoryKey }?.name ?: categoryKey
                val merchant = current.merchant?.trim()?.takeIf { it.isNotEmpty() }
                val alreadyRuled = offerRule && merchant != null &&
                    hasEqualsRule(repository.observeRules().first(), merchant, categoryKey)
                if (offerRule && merchant != null && !alreadyRuled && categoryKey != "INTERNAL_TRANSFER") {
                    _events.send(
                        TransactionEvent.CategoryChanged(
                            merchant = merchant,
                            categoryKey = categoryKey,
                            categoryName = catName,
                            message = "Moved to $catName. Always categorise $merchant as $catName?"
                        )
                    )
                } else {
                    _events.send(TransactionEvent.Info("Moved to $catName"))
                }
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't change the category. Try again."))
            }
        }
    }

    /** Opt-in from the snackbar after [quickSetCategory]: remember the merchant → category rule. */
    fun alwaysCategorise(merchant: String, categoryKey: String, categoryName: String) {
        viewModelScope.launch {
            try {
                addRuleIfMissing(merchant, categoryKey)
                _events.send(TransactionEvent.Info("$merchant will always be categorised as $categoryName"))
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't save the rule. Try again."))
            }
        }
    }

    fun deleteTransaction(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val item = transactions.value.firstOrNull { it.id == id }
                repository.delete(id)
                onSuccess()
                if (item != null) {
                    _events.send(TransactionEvent.Deleted(item, restorable = canRestore(item)))
                } else {
                    _events.send(TransactionEvent.Info("Transaction deleted"))
                }
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't delete the transaction. Try again."))
            }
        }
    }

    /** Re-adds a transaction deleted a moment ago (only offered when [canRestore] is true). */
    fun undoDelete(item: TransactionItem) {
        if (!canRestore(item)) return
        viewModelScope.launch {
            try {
                repository.add(
                    NewTransaction.Entry(
                        type = item.type,
                        accountId = item.accountId,
                        amountMinor = item.amountMinor,
                        categoryKey = item.categoryKey,
                        merchant = item.merchant,
                        note = item.note,
                        timestamp = item.timestamp
                    )
                )
                _events.send(TransactionEvent.Info("Transaction restored"))
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't restore the transaction."))
            }
        }
    }

    /**
     * Creates a category and hands the stored entity to [onCreated] so the caller can select it.
     * The repository does not return the new key, so it is found by diffing the category list.
     */
    fun createCategory(
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        ruleMerchant: String?,
        onCreated: (CategoryEntity) -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val before = repository.observeCategories().first().map { it.key }.toSet()
                repository.createCategoryWithRule(
                    name = name,
                    groupId = groupId,
                    newGroupName = newGroupName,
                    emoji = emoji,
                    colorHex = colorHex,
                    type = type,
                    alwaysMatchMerchant = ruleMerchant
                )
                val created = findCreatedCategory(repository.observeCategories().first(), before, name, type)
                if (created != null) {
                    onCreated(created)
                } else {
                    _events.send(TransactionEvent.Info("Category \"${name.trim()}\" added"))
                }
            } catch (e: Exception) {
                _events.send(TransactionEvent.Failed("Couldn't create the category. Try again."))
            }
        }
    }

    private suspend fun addRuleIfMissing(merchant: String, categoryKey: String) {
        val pattern = merchant.trim()
        if (pattern.isEmpty()) return
        if (hasEqualsRule(repository.observeRules().first(), pattern, categoryKey)) return
        repository.addRule("EQUALS", pattern, categoryKey)
    }
}

internal data class QuickCategoryTarget(
    val type: TransactionType,
    val destinationAccountId: Long?,
    val excludeFromSpending: Boolean
)

/**
 * Type/exclusion to store when the quick picker sets [categoryKey] on [current]. A transfer given
 * a normal category becomes a payment (money out, or in for an income category) that counts again.
 */
internal fun quickCategoryTarget(
    current: TransactionItem,
    categoryKey: String,
    categories: List<CategoryEntity>
): QuickCategoryTarget {
    val wasTransfer = current.type == TransactionType.TRANSFER || current.categoryKey == "INTERNAL_TRANSFER"
    if (categoryKey == "INTERNAL_TRANSFER") {
        return QuickCategoryTarget(current.type, current.destinationAccountId, excludeFromSpending = true)
    }
    if (!wasTransfer) {
        return QuickCategoryTarget(current.type, current.destinationAccountId, current.excludeFromSpending)
    }
    val catType = categories.firstOrNull { it.key == categoryKey }?.type
    val newType = if (catType == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
    return QuickCategoryTarget(newType, destinationAccountId = null, excludeFromSpending = false)
}

/** Plain income/expense rows can be re-added through [SpendRepository.add] without losing data. */
internal fun canRestore(item: TransactionItem): Boolean =
    item.type != TransactionType.TRANSFER &&
        item.categoryKey != "INTERNAL_TRANSFER" &&
        !item.excludeFromSpending &&
        item.amountMinor > 0

/** True when an EQUALS rule for [merchant] (case-insensitive) → [categoryKey] already exists. */
internal fun hasEqualsRule(rules: List<MerchantRuleEntity>, merchant: String, categoryKey: String): Boolean {
    val pattern = merchant.trim().lowercase(Locale.ROOT)
    return rules.any {
        it.matchType == "EQUALS" &&
            it.categoryKey == categoryKey &&
            it.pattern.trim().lowercase(Locale.ROOT) == pattern
    }
}

/**
 * The category added by the last create call: a key that was not in [keysBefore] with the same
 * name and type; if several, the newest custom key ("c_<millis>").
 */
internal fun findCreatedCategory(
    categories: List<CategoryEntity>,
    keysBefore: Set<String>,
    name: String,
    type: TransactionType
): CategoryEntity? {
    val trimmed = name.trim()
    val fresh = categories.filter { it.key !in keysBefore }
    val byName = fresh.filter { it.name.trim().equals(trimmed, ignoreCase = true) }
    val candidates = byName.filter { it.type == type }.ifEmpty { byName }.ifEmpty { fresh }
    return candidates.maxByOrNull { it.key.removePrefix("c_").toLongOrNull() ?: Long.MIN_VALUE }
}
