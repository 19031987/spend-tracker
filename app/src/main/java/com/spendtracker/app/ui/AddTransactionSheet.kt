package com.spendtracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.TransactionCategory
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.NewTransaction
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    accounts: List<AccountEntity>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (NewTransaction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var destinationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf<TransactionCategory?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val effectiveSource = accounts.firstOrNull { it.id == sourceId } ?: accounts.firstOrNull()
    val effectiveDestination = accounts.firstOrNull { it.id == destinationId && it.id != effectiveSource?.id }
        ?: accounts.firstOrNull { it.id != effectiveSource?.id }

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
            Text("New transaction", style = MaterialTheme.typography.titleLarge)

            val types = TransactionType.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            type = t
                            category = null
                            localError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(t.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1)
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
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

            // Spending categories exist only for EXPENSE / INCOME; TRANSFER swaps them for a note.
            AnimatedVisibility(visible = type != TransactionType.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransactionCategory.forType(type).forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.label, maxLines = 1) }
                            )
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
                modifier = Modifier.fillMaxWidth()
            )

            val shownError = localError ?: errorMessage
            if (shownError != null) {
                Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amount = parseMinorUnits(amountText)
                    val source = effectiveSource
                    val now = System.currentTimeMillis()
                    when {
                        amount == null -> localError = "Enter a valid amount (max 2 decimals)"
                        source == null -> localError = "Select an account"
                        type == TransactionType.TRANSFER -> {
                            val dest = effectiveDestination
                            if (dest == null) {
                                localError = "Create a second account to transfer between"
                            } else {
                                localError = null
                                onSubmit(NewTransaction.Transfer(source.id, dest.id, amount, note, now))
                            }
                        }
                        else -> {
                            localError = null
                            val chosen = category ?: TransactionCategory.forType(type).last()
                            onSubmit(NewTransaction.Entry(type, source.id, amount, chosen, note, now))
                        }
                    }
                }
            ) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        }
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
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name ?: "Select account", maxLines = 1)
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
