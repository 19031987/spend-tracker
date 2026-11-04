package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(private val repository: SpendRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CategoryGroupEntity>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionItem>> = repository.observeTransactionItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
