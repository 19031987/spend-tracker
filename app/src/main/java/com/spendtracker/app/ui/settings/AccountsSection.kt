package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.AccountNameCheck
import com.spendtracker.app.data.checkAccountName
import com.spendtracker.app.data.isProtectedAccount
import com.spendtracker.app.data.isRecognisedBankName
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.EmptyState
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme

private fun txLabel(count: Int): String = if (count == 1) "1 transaction" else "$count transactions"

@Composable
fun AccountsSection(
    viewModel: SettingsViewModel,
    accounts: List<AccountEntity>,
    transactionCounts: Map<Long, Int>
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    SheetContainer(modifier = Modifier.padding(top = Spacing.lg)) {
        SectionHeader(
            "Accounts",
            subtitle = "Detected from bank notifications or added by you",
            actionLabel = "Add",
            actionIcon = Icons.Filled.Add,
            actionIconLeading = true,
            onAction = { showAdd = true }
        )
        if (accounts.isEmpty()) {
            EmptyState(body = "No accounts yet.", actionLabel = "Add account", onAction = { showAdd = true })
        } else {
            AppCard(verticalSpacing = Spacing.xs) {
                accounts.forEach { account ->
                    val builtIn = isProtectedAccount(account.name)
                    val count = transactionCounts[account.id] ?: 0
                    SettingsListRow(
                        title = account.name,
                        supporting = if (builtIn) "${txLabel(count)} · built-in" else txLabel(count),
                        onClick = if (builtIn) null else ({ editingId = account.id }),
                        leading = {
                            Icon(
                                Icons.Filled.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailing = {
                            Icon(
                                if (builtIn) Icons.Outlined.Lock else Icons.Filled.ChevronRight,
                                contentDescription = if (builtIn) "Built-in account" else "Edit ${account.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
            SettingsBodyText(
                "Chase, HSBC and Credit Card are always available and can't be renamed or deleted. " +
                    "Bank names are tidied automatically (for example \"Monzo joint\" becomes \"Monzo\")."
            )
        }
    }

    if (showAdd) {
        AccountNameSheet(
            title = "Add account",
            confirmLabel = "Add",
            initialName = "",
            editingId = null,
            accounts = accounts,
            onSubmit = { name, onError ->
                viewModel.addAccount(name) { err -> if (err == null) showAdd = false else onError(err) }
            },
            onDismiss = { showAdd = false }
        )
    }

    val editing = accounts.firstOrNull { it.id == editingId }
    if (editing != null) {
        var confirmDelete by remember(editing.id) { mutableStateOf(false) }
        AccountNameSheet(
            title = "Edit account",
            confirmLabel = "Save",
            initialName = editing.name,
            editingId = editing.id,
            accounts = accounts,
            onSubmit = { name, onError ->
                viewModel.renameAccount(editing.id, name) { err -> if (err == null) editingId = null else onError(err) }
            },
            onDismiss = { editingId = null },
            onDelete = { confirmDelete = true },
            renameWarning = when {
                isRecognisedBankName(editing.name) ->
                    "New notifications from ${editing.name} will go to a new \"${editing.name}\" account. " +
                        "Renaming updates the transactions already here."
                (transactionCounts[editing.id] ?: 0) > 0 ->
                    "If this account was created from bank notifications, new ones from that bank will create " +
                        "a new \"${editing.name}\" account. Renaming updates the transactions already here."
                else -> null
            }
        )
        if (confirmDelete) {
            DeleteAccountDialog(
                account = editing,
                transactionCount = transactionCounts[editing.id] ?: 0,
                others = accounts.filter { it.id != editing.id },
                onConfirm = { moveTo, onError ->
                    viewModel.deleteAccount(editing.id, moveTo) { err ->
                        if (err == null) {
                            confirmDelete = false
                            editingId = null
                        } else {
                            onError(err)
                        }
                    }
                },
                onDismiss = { confirmDelete = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountNameSheet(
    title: String,
    confirmLabel: String,
    initialName: String,
    editingId: Long?,
    accounts: List<AccountEntity>,
    onSubmit: (name: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    renameWarning: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(editingId) { mutableStateOf(initialName) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val check = checkAccountName(name, accounts, editingId)
    val unchanged = editingId != null && name.trim() == initialName

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader(title)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    serverError = null
                },
                label = { Text("Account name") },
                singleLine = true,
                isError = serverError != null || (name.isNotBlank() && check is AccountNameCheck.Invalid),
                supportingText = {
                    val message = serverError ?: when (check) {
                        is AccountNameCheck.Invalid -> if (name.isNotBlank()) check.reason else null
                        is AccountNameCheck.Valid -> if (check.adjusted) "Will be saved as \"${check.name}\"" else null
                    }
                    if (message != null) Text(message)
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (renameWarning != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = SpendTrackerTheme.finance.warningContainer,
                    contentColor = SpendTrackerTheme.finance.onWarningContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        renameWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            }
            SheetActions(
                confirmLabel = confirmLabel,
                onConfirm = { onSubmit(name) { serverError = it } },
                onDismiss = onDismiss,
                confirmEnabled = check is AccountNameCheck.Valid && !unchanged
            )
            if (onDelete != null) {
                DestructiveTextButton(
                    text = "Delete account",
                    onClick = onDelete,
                    leadingIcon = Icons.Outlined.Delete,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    account: AccountEntity,
    transactionCount: Int,
    others: List<AccountEntity>,
    onConfirm: (moveToId: Long?, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var moveToId by rememberSaveable(account.id) {
        mutableStateOf(others.firstOrNull { isProtectedAccount(it.name) }?.id ?: others.firstOrNull()?.id)
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${account.name}\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (transactionCount == 0) {
                    Text("It has no transactions.")
                } else {
                    Text("Its ${txLabel(transactionCount)} (including transfers) will be moved to:")
                    SettingsDropdown(
                        label = "Move transactions to",
                        selected = others.firstOrNull { it.id == moveToId },
                        options = others,
                        optionLabel = { it.name },
                        onSelect = { moveToId = it.id },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            DestructiveButton(
                text = if (transactionCount == 0) "Delete" else "Move and delete",
                enabled = transactionCount == 0 || moveToId != null,
                // Only pass a target when something will actually move.
                onClick = { onConfirm(if (transactionCount == 0) null else moveToId) { error = it } }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
