@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.spendtracker.app.domain.TransactionItem
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            BigDecimal.valueOf(transaction.amountMinor).movePointLeft(2).toPlainString()
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

    val deduplicatedAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    val effectiveSource = deduplicatedAccounts.firstOrNull { it.id == sourceId }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals(normalizeBankName(transaction.source), ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals(normalizeBankName(transaction.accountName), ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull { it.name.trim().equals("Chase", ignoreCase = true) }
        ?: deduplicatedAccounts.firstOrNull()

    val activeCategory = categories.firstOrNull { it.key == selectedCategoryKey && it.type == type }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Edit Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(transaction.timestamp))
                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

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

            // Payee / Merchant
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Payee / Merchant Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount (£)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Account Picker
            AccountPicker(
                label = "Account (Source)",
                accounts = deduplicatedAccounts,
                selected = effectiveSource,
                onSelected = { sourceId = it.id }
            )

            // Category Display / Picker (uniform 32dp icon box)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEF3C7)),
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
                            Text("Change", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (merchantText.isNotBlank() && activeCategory != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoRuleChecked,
                            onCheckedChange = { autoRuleChecked = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Always categorize \"${merchantText.trim()}\" as ${activeCategory.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Nullify / Exclude toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nullify from spend calculations",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Exclude this transaction from total spending and analytics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = excludeFromSpending,
                    onCheckedChange = { excludeFromSpending = it }
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (localError != null) {
                Text(localError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val amount = parseMinorUnits(amountText)
                    val source = effectiveSource
                    if (amount == null) {
                        localError = "Enter a valid amount"
                        return@Button
                    }
                    if (source == null) {
                        localError = "Select an account"
                        return@Button
                    }
                    onSave(
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
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? If it is a transfer, both legs will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(transaction.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
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

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<AccountEntity>,
    selected: AccountEntity?,
    onSelected: (AccountEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uniqueAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(selected?.name?.trim() ?: "Select account", maxLines = 1, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                uniqueAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name.trim()) },
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
