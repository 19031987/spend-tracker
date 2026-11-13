@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.ui.components.AccountDropdownSelector
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.PrimaryButton
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    transaction: TransactionItem,
    accounts: List<AccountEntity>,
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        type: TransactionType,
        accountId: Long,
        destinationAccountId: Long?,
        amountMinor: Long,
        categoryKey: String?,
        merchant: String?,
        note: String?,
        excludeFromSpending: Boolean,
        ruleMerchant: String?
    ) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by rememberSaveable {
        mutableStateOf(
            if (transaction.type == TransactionType.TRANSFER) TransactionType.EXPENSE else transaction.type
        )
    }
    var merchantText by rememberSaveable { mutableStateOf(transaction.merchant ?: "") }
    var amountText by rememberSaveable {
        mutableStateOf(
            CurrencyUtils.minorToDecimalString(transaction.amountMinor)
        )
    }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(transaction.accountId) }
    var selectedCategoryKey by rememberSaveable {
        mutableStateOf(
            if (transaction.categoryKey == "INTERNAL_TRANSFER") null else transaction.categoryKey
        )
    }
    var note by rememberSaveable { mutableStateOf(transaction.note ?: "") }
    var excludeFromSpending by rememberSaveable { mutableStateOf(transaction.excludeFromSpending) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var autoRuleChecked by rememberSaveable { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val deduplicatedAccounts = remember(accounts) { deduplicateAccounts(accounts) }

    val effectiveSource = deduplicatedAccounts.firstOrNull { it.id == sourceId }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals(normalizeBankName(transaction.source), ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals(normalizeBankName(transaction.accountName), ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull()

    val activeCategory = categories.firstOrNull { it.key == selectedCategoryKey && it.type == type }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader(
                title = "Edit transaction",
                subtitle = DateUtils.formatDateTime(transaction.timestamp)
            )

            // 2-way Transaction Type Selector: Money Out vs Money In
            val types = listOf(
                TransactionType.EXPENSE to "↓ Money Out",
                TransactionType.INCOME to "↑ Money In"
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            type = t
                            val curCat = categories.firstOrNull { it.key == selectedCategoryKey }
                            if (curCat != null && curCat.type != t) {
                                selectedCategoryKey = if (t == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
                            }
                            localError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(text = label, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Payee / Merchant
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Payee / Merchant Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount (£)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Account Picker
            AccountDropdownSelector(
                label = "Account",
                accounts = deduplicatedAccounts,
                selected = effectiveSource,
                onAccountSelected = { sourceId = it.id }
            )

            // Category Display / Picker
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CategoryField(
                    category = activeCategory,
                    onClick = { showCategoryPicker = true }
                )

                if (merchantText.isNotBlank() && activeCategory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Checkbox(
                            checked = autoRuleChecked,
                            onCheckedChange = { autoRuleChecked = it }
                        )
                        Text(
                            "Always categorize \"${merchantText.trim()}\" as ${activeCategory.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Nullify / Exclude toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nullify from spend calculations",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Exclude this transaction from total spending and analytics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = excludeFromSpending,
                        onCheckedChange = { excludeFromSpending = it }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (localError != null) {
                Text(
                    localError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            PrimaryButton(
                text = "Save changes",
                fullWidth = true,
                onClick = {
                    val amount = parseMinorUnits(amountText)
                    val source = effectiveSource
                    when {
                        amount == null -> localError = "Enter a valid amount"
                        source == null -> localError = "Select an account"
                        else -> onSave(
                            transaction.id,
                            type,
                            source.id,
                            null,
                            amount,
                            selectedCategoryKey ?: if (type == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE",
                            merchantText.trim().takeIf { it.isNotEmpty() },
                            note.trim().takeIf { it.isNotEmpty() },
                            excludeFromSpending,
                            if (autoRuleChecked && merchantText.isNotBlank()) merchantText.trim() else null
                        )
                    }
                }
            )

            DestructiveTextButton(
                text = "Delete transaction",
                onClick = { showDeleteConfirm = true },
                leadingIcon = Icons.Outlined.Delete,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transaction?") },
            text = { Text("Are you sure you want to delete this transaction? If it is a transfer, both legs will be deleted.") },
            confirmButton = {
                DestructiveButton(
                    text = "Delete",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(transaction.id)
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            groups = groups,
            categories = categories,
            selectedCategoryKey = selectedCategoryKey,
            currentMerchant = merchantText.trim().takeIf { it.isNotEmpty() },
            transactionType = type,
            onDismiss = { showCategoryPicker = false },
            onCategorySelected = { cat ->
                selectedCategoryKey = cat.key
                if (cat.key == "INTERNAL_TRANSFER") {
                    excludeFromSpending = true
                }
                showCategoryPicker = false
            },
            onCreateCategory = { _, _, _, _, _, _, _ ->
                showCategoryPicker = false
            }
        )
    }
}
