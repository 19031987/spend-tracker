package com.spendtracker.app.ui

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    val effectiveSource = accounts.firstOrNull { it.id == sourceId }
        ?: accounts.firstOrNull { it.name.equals("Chase", ignoreCase = true) }
        ?: accounts.firstOrNull()
    val effectiveDestination = accounts.firstOrNull { it.id == destinationId && it.id != effectiveSource?.id }
        ?: accounts.firstOrNull { it.id != effectiveSource?.id && it.name.equals("HSBC", ignoreCase = true) }
        ?: accounts.firstOrNull { it.id != effectiveSource?.id }

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

            // 3-way Direction Selector: Money Out vs Money In vs Between Accounts
            val types = listOf(
                TransactionType.EXPENSE to "↓ Money Out",
                TransactionType.INCOME to "↑ Money In",
                TransactionType.TRANSFER to "🔄 Between Accounts"
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            type = t
                            if (t == TransactionType.TRANSFER) {
                                selectedCategoryKey = "INTERNAL_TRANSFER"
                            } else {
                                selectedCategoryKey = null
                            }
                            autoAssignedCategory = null
                            localError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            fontSize = 11.sp,
                            fontWeight = if (type == t) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Day / Date Selector (Today, Yesterday, 2 Days Ago)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val days = listOf(0 to "Today", 1 to "Yesterday", 2 to "2 Days Ago")
                    days.forEach { (offset, label) ->
                        val isSelected = selectedDayOffset == offset
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedDayOffset = offset },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Payee / Merchant Name (Live classification trigger)
            if (type != TransactionType.TRANSFER) {
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
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount") },
                prefix = {
                    Text(
                        text = when (type) {
                            TransactionType.EXPENSE -> "−£ "
                            TransactionType.INCOME -> "+£ "
                            TransactionType.TRANSFER -> "£ "
                        },
                        fontWeight = FontWeight.Bold,
                        color = when (type) {
                            TransactionType.EXPENSE -> Color(0xFFDC2626)
                            TransactionType.INCOME -> Color(0xFF059669)
                            TransactionType.TRANSFER -> Color(0xFF7C3AED)
                        }
                    )
                },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            AccountPicker(
                label = if (type == TransactionType.TRANSFER) "From account" else "Account",
                accounts = accounts,
                selected = effectiveSource,
                onSelected = { sourceId = it.id }
            )

            AnimatedVisibility(visible = type == TransactionType.TRANSFER) {
                AccountPicker(
                    label = "To account",
                    accounts = accounts.filter { it.id != effectiveSource?.id },
                    selected = effectiveDestination,
                    onSelected = { destinationId = it.id }
                )
            }

            AnimatedVisibility(visible = type == TransactionType.TRANSFER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEDE9FE))
                        .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDDD6FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔄", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            "Category: Internal Transfer",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B21B6),
                            fontSize = 13.sp
                        )
                        Text(
                            "Nullified from spending calculations (excluded from net spend)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6D28D9),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Category Picker Row with Uncategorized fallback pill
            AnimatedVisibility(visible = type != TransactionType.TRANSFER) {
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Uncategorized distinct pill
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFEF3C7))
                                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("❓", fontSize = 14.sp)
                                    }
                                    Text(
                                        "Uncategorized",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            TextButton(onClick = { showCategoryPicker = true }) {
                                Text("Choose", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = {
                    Text(if (type == TransactionType.TRANSFER) "Transfer Note (optional)" else "Note (optional)")
                },
                placeholder = {
                    if (type == TransactionType.TRANSFER) Text("e.g. Savings Buffer, Card Payoff")
                },
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
                    val cal = java.util.Calendar.getInstance()
                    if (selectedDayOffset > 0) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -selectedDayOffset)
                    }
                    val txTimestamp = cal.timeInMillis
                    when {
                        amount == null -> localError = "Enter a valid amount (max 2 decimals)"
                        source == null -> localError = "Select an account"
                        type == TransactionType.TRANSFER -> {
                            val dest = effectiveDestination
                            if (dest == null) {
                                localError = "Create a second account to transfer between"
                            } else {
                                localError = null
                                onSubmit(
                                    NewTransaction.Transfer(
                                        sourceAccountId = source.id,
                                        destinationAccountId = dest.id,
                                        amountMinor = amount,
                                        note = note,
                                        timestamp = txTimestamp,
                                        sourceName = source.name,
                                        destinationName = dest.name
                                    )
                                )
                            }
                        }
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

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<AccountEntity>,
    selected: AccountEntity?,
    onSelected: (AccountEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(selected?.name ?: "Select account", maxLines = 1, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            onSelected(account)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** "12.34" or "12,34" to 1234. Returns null if invalid, <= 0, or has more than 2 decimals. */
internal fun parseMinorUnits(input: String): Long? {
    val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) return null
    return runCatching { value.multiply(BigDecimal(100)).longValueExact() }.getOrNull()
}
