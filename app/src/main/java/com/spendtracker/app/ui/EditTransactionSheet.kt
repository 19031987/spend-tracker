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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
        excludeFromSpending: Boolean
    ) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by rememberSaveable { mutableStateOf(transaction.type) }
    var merchantText by rememberSaveable { mutableStateOf(transaction.merchant ?: "") }
    var amountText by rememberSaveable {
        mutableStateOf(
            BigDecimal.valueOf(transaction.amountMinor).movePointLeft(2).toPlainString()
        )
    }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(transaction.accountId) }
    var destinationId by rememberSaveable { mutableStateOf<Long?>(transaction.destinationAccountId) }
    var selectedCategoryKey by rememberSaveable { mutableStateOf(transaction.categoryKey) }
    var note by rememberSaveable { mutableStateOf(transaction.note ?: "") }
    var excludeFromSpending by rememberSaveable { mutableStateOf(transaction.excludeFromSpending) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

            // Transaction Type Selector
            val types = TransactionType.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            type = t
                            if (t == TransactionType.TRANSFER) {
                                selectedCategoryKey = "INTERNAL_TRANSFER"
                                excludeFromSpending = true
                            } else if (selectedCategoryKey == "INTERNAL_TRANSFER") {
                                selectedCategoryKey = "OTHER_EXPENSE"
                                excludeFromSpending = false
                            }
                            localError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(
                            when (t) {
                                TransactionType.EXPENSE -> "Expense"
                                TransactionType.INCOME -> "Income"
                                TransactionType.TRANSFER -> "🔄 Transfer"
                            },
                            maxLines = 1,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Payee / Merchant
            if (type != TransactionType.TRANSFER) {
                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text("Payee / Merchant Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

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

            // Account Pickers: "From Account" and "To Account"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AccountPicker(
                        label = if (type == TransactionType.TRANSFER) "From account" else "Account (Source)",
                        accounts = accounts,
                        selected = effectiveSource,
                        onSelected = { sourceId = it.id }
                    )
                }

                if (type == TransactionType.TRANSFER) {
                    Box(modifier = Modifier.weight(1f)) {
                        AccountPicker(
                            label = "To account",
                            accounts = accounts.filter { it.id != effectiveSource?.id },
                            selected = effectiveDestination,
                            onSelected = { destinationId = it.id }
                        )
                    }
                }
            }

            // Category Display / Picker
            if (type == TransactionType.TRANSFER) {
                // Internal Transfer notification / banner
                Surface(
                    color = Color(0xFFEDE9FE),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🔄", fontSize = 20.sp)
                        Column {
                            Text(
                                "Category: Internal Transfer",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4C1D95),
                                fontSize = 14.sp
                            )
                            Text(
                                "Internal transactions nullify each other and are excluded from spending totals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6D28D9),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("❓", fontSize = 16.sp)
                                    Text("Uncategorized", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = { showCategoryPicker = true }) {
                                Text("Change", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Nullify / Exclude toggle for non-transfers
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
                    if (type == TransactionType.TRANSFER) {
                        val dest = effectiveDestination
                        if (dest == null) {
                            localError = "Select a destination account"
                            return@Button
                        }
                        onSave(
                            transaction.id,
                            TransactionType.TRANSFER,
                            source.id,
                            dest.id,
                            amount,
                            "INTERNAL_TRANSFER",
                            "${source.name} ➔ ${dest.name}",
                            note.trim().takeIf { it.isNotEmpty() },
                            true // Transfer is ALWAYS nullified from spend!
                        )
                    } else {
                        onSave(
                            transaction.id,
                            type,
                            source.id,
                            null,
                            amount,
                            selectedCategoryKey,
                            merchantText.trim().takeIf { it.isNotEmpty() },
                            note.trim().takeIf { it.isNotEmpty() },
                            excludeFromSpending || selectedCategoryKey == "INTERNAL_TRANSFER"
                        )
                    }
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
        CategoryPickerSheet(
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
