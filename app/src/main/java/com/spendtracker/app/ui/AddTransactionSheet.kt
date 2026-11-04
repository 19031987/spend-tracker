@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.spendtracker.app.ui.components.AccountDropdownSelector
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import java.math.BigDecimal
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.domain.Classification
import com.spendtracker.app.domain.NewTransaction

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
    onSubmit: (NewTransaction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var selectedDayOffset by rememberSaveable { mutableStateOf(0) }
    var merchantText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var destinationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    var autoAssignedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }

    val deduplicatedAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    val effectiveSource = deduplicatedAccounts.firstOrNull { it.id == sourceId }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals("Chase", ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull()
    val effectiveDestination = deduplicatedAccounts.firstOrNull { it.id == destinationId && it.id != effectiveSource?.id }
        ?: deduplicatedAccounts.firstOrNull { it.id != effectiveSource?.id && it.name.trim().equals("HSBC", ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull { it.id != effectiveSource?.id }

    val activeCategory = categories.firstOrNull { it.key == selectedCategoryKey }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Record Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 12.sp,
                            fontWeight = if (type == t) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Day / Date Selector (Today, Yesterday, 2 Days Ago, Custom Date)
            val context = androidx.compose.ui.platform.LocalContext.current
            var customTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val days = listOf(0 to "Today", 1 to "Yesterday", 2 to "2 Days Ago")
                    items(days) { (offset, label) ->
                        val isSelected = selectedDayOffset == offset && customTimestamp == null
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedDayOffset = offset
                                    customTimestamp = null
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 4th option: Pick Date from Calendar
                    item {
                        val isCustomSelected = customTimestamp != null
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
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
                            color = if (isCustomSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (customTimestamp != null) "📅 ${DateUtils.formatDateYear(customTimestamp!!)}" else "📅 Pick Date",
                                    fontSize = 12.sp,
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCustomSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Payee / Merchant Name (Live classification trigger)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Monarch-style hint when detected: [⚡ Auto-assigned: Groceries] with quick one-click "Change" action
                AnimatedVisibility(visible = autoAssignedCategory != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFECFDF5))
                            .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "⚡ Auto-assigned: ${autoAssignedCategory.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { showCategoryPicker = true }
                        ) {
                            Text("Change", color = Color(0xFF047857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        fontWeight = FontWeight.Bold,
                        color = if (type == TransactionType.INCOME) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            AccountDropdownSelector(
                label = "Account",
                accounts = deduplicatedAccounts,
                selected = effectiveSource,
                onAccountSelected = { sourceId = it.id }
            )

            // Category Picker Row with Uncategorized fallback pill (uniform 32dp icon box)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showCategoryPicker = true },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (activeCategory != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(parseColorHex(activeCategory.colorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(activeCategory.emoji, fontSize = 16.sp)
                                }
                                Text(
                                    activeCategory.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            // Uncategorized distinct pill (uniform 32dp icon box)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEF3C7))
                                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("❓", fontSize = 16.sp)
                                }
                                Text(
                                    "Uncategorized",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        TextButton(onClick = { showCategoryPicker = true }) {
                            Text("Choose", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            val shownError = localError ?: errorMessage
            if (shownError != null) {
                Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
            ) {
                Text(if (isSaving) "Saving…" else "Save Transaction", fontWeight = FontWeight.Bold)
            }
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
