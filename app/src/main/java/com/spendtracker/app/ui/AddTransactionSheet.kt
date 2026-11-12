@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import com.spendtracker.app.domain.Classification
import com.spendtracker.app.domain.NewTransaction
import com.spendtracker.app.ui.components.AccountDropdownSelector
import com.spendtracker.app.ui.components.PrimaryButton
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils
import java.util.Locale

/**
 * @param defaultAccountId account to preselect: the one the user last added a transaction to
 * (see [AddTransactionViewModel.defaultAccountId]). Falls back to the first account (A-Z).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    accounts: List<AccountEntity>,
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onEvaluateCategory: (String) -> Classification,
    onCreateCategory: (
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        ruleMerchant: String?
    ) -> Unit,
    onSubmit: (NewTransaction) -> Unit,
    defaultAccountId: Long? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val finance = SpendTrackerTheme.finance

    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var selectedDayOffset by rememberSaveable { mutableStateOf(0) }
    var merchantText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    var autoAssignedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }

    val deduplicatedAccounts = remember(accounts) { deduplicateAccounts(accounts) }

    val effectiveSource = pickDefaultAccount(
        allAccounts = accounts,
        deduplicated = deduplicatedAccounts,
        selectedId = sourceId,
        preferredId = defaultAccountId
    )

    val activeCategory = categories.firstOrNull { it.key == selectedCategoryKey }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader("New transaction")

            // 2-way Direction Selector: Money Out vs Money In
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
                            selectedCategoryKey = null
                            autoAssignedCategory = null
                            localError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(text = label, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Day / Date Selector (Today, Yesterday, 2 Days Ago, Custom Date)
            val context = androidx.compose.ui.platform.LocalContext.current
            var customTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Date", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val days = listOf(0 to "Today", 1 to "Yesterday", 2 to "2 Days Ago")
                    items(days) { (offset, label) ->
                        FilterChip(
                            selected = selectedDayOffset == offset && customTimestamp == null,
                            onClick = {
                                selectedDayOffset = offset
                                customTimestamp = null
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }

                    // 4th option: Pick Date from Calendar
                    item {
                        FilterChip(
                            selected = customTimestamp != null,
                            onClick = {
                                val cal = java.util.Calendar.getInstance()
                                if (customTimestamp != null) {
                                    cal.timeInMillis = customTimestamp!!
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = java.util.Calendar.getInstance().apply {
                                            set(java.util.Calendar.YEAR, year)
                                            set(java.util.Calendar.MONTH, month)
                                            set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        customTimestamp = selectedCal.timeInMillis
                                        selectedDayOffset = -1
                                    },
                                    cal.get(java.util.Calendar.YEAR),
                                    cal.get(java.util.Calendar.MONTH),
                                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            label = {
                                Text(
                                    text = if (customTimestamp != null) "📅 ${DateUtils.formatDateYear(customTimestamp!!)}" else "📅 Pick Date",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }
            }

            // Payee / Merchant Name (Live classification trigger)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { input ->
                        merchantText = input
                        val result = onEvaluateCategory(input)
                        when (result) {
                            is Classification.Matched -> {
                                selectedCategoryKey = result.categoryKey
                                val catName = categories.firstOrNull { it.key == result.categoryKey }?.name ?: result.categoryKey
                                autoAssignedCategory = catName
                            }
                            is Classification.Unknown -> {
                                autoAssignedCategory = null
                            }
                        }
                    },
                    label = { Text("Payee / Merchant Name") },
                    placeholder = { Text("e.g. Tesco, Shell, Starbucks, Netflix...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hint when a category was detected, with a one-tap "Change" action
                AnimatedVisibility(visible = autoAssignedCategory != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = finance.surplusContainer,
                        contentColor = finance.onSurplusContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(start = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "⚡ Auto-assigned: ${autoAssignedCategory.orEmpty()}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showCategoryPicker = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = finance.onSurplusContainer)
                            ) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount") },
                prefix = {
                    Text(
                        text = if (type == TransactionType.INCOME) "+£ " else "−£ ",
                        color = if (type == TransactionType.INCOME) finance.income else finance.expense
                    )
                },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            AccountDropdownSelector(
                label = "Account",
                accounts = deduplicatedAccounts,
                selected = effectiveSource,
                onAccountSelected = { sourceId = it.id }
            )

            CategoryField(
                category = activeCategory,
                onClick = { showCategoryPicker = true }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            val shownError = localError ?: errorMessage
            if (shownError != null) {
                Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            PrimaryButton(
                text = if (isSaving) "Saving…" else "Add transaction",
                enabled = !isSaving,
                fullWidth = true,
                onClick = {
                    val amount = parseMinorUnits(amountText)
                    val source = effectiveSource
                    val txTimestamp = if (customTimestamp != null) {
                        customTimestamp!!
                    } else {
                        val cal = java.util.Calendar.getInstance()
                        if (selectedDayOffset > 0) {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, -selectedDayOffset)
                        }
                        cal.timeInMillis
                    }
                    when {
                        amount == null -> localError = "Enter a valid amount (max 2 decimals)"
                        source == null -> localError = "Select an account"
                        else -> {
                            localError = null
                            onSubmit(
                                NewTransaction.Entry(
                                    type = type,
                                    accountId = source.id,
                                    amountMinor = amount,
                                    category = null,
                                    categoryKey = selectedCategoryKey,
                                    merchant = merchantText.trim().takeIf { it.isNotEmpty() },
                                    note = note,
                                    timestamp = txTimestamp
                                )
                            )
                        }
                    }
                }
            )
        }
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
                autoAssignedCategory = null
                showCategoryPicker = false
            },
            onCreateCategory = { name, groupId, newGroupName, emoji, colorHex, cType, ruleMerchant ->
                onCreateCategory(name, groupId, newGroupName, emoji, colorHex, cType, ruleMerchant)
                showCategoryPicker = false
            }
        )
    }
}

/** "12.34" or "12,34" to 1234. Returns null if invalid, <= 0, or has more than 2 decimals. */
internal fun parseMinorUnits(input: String): Long? = CurrencyUtils.parseDecimalToMinor(input)

/** One entry per bank (names normalised, case-insensitive), sorted A-Z: what the account picker shows. */
internal fun deduplicateAccounts(accounts: List<AccountEntity>): List<AccountEntity> =
    accounts
        .map { it.copy(name = normalizeBankName(it.name)) }
        .distinctBy { it.name.lowercase(Locale.ROOT) }
        .sortedBy { it.name }

/**
 * Account preselected in the Add sheet, in order of preference:
 * 1. the one the user picked in this sheet ([selectedId]),
 * 2. the account last used for a transaction ([preferredId]); if that id was folded into
 *    another entry by [deduplicateAccounts], the entry with the same normalised bank name,
 * 3. the first account in [deduplicated] (A-Z). Never a hard-coded bank name.
 */
internal fun pickDefaultAccount(
    allAccounts: List<AccountEntity>,
    deduplicated: List<AccountEntity>,
    selectedId: Long?,
    preferredId: Long?
): AccountEntity? {
    if (selectedId != null) {
        deduplicated.firstOrNull { it.id == selectedId }?.let { return it }
    }
    if (preferredId != null) {
        deduplicated.firstOrNull { it.id == preferredId }?.let { return it }
        val preferredName = allAccounts.firstOrNull { it.id == preferredId }?.let { normalizeBankName(it.name) }
        if (preferredName != null) {
            deduplicated.firstOrNull { it.name.equals(preferredName, ignoreCase = true) }?.let { return it }
        }
    }
    return deduplicated.firstOrNull()
}
