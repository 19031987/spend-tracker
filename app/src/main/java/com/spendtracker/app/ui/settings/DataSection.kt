package com.spendtracker.app.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.spendtracker.app.data.ImportMode
import com.spendtracker.app.domain.BackupCounts
import com.spendtracker.app.ui.PendingImport
import com.spendtracker.app.ui.SettingsUiState
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.PrimaryButton
import com.spendtracker.app.ui.components.SecondaryButton
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openOutputStream(uri) ?: error("Couldn't open the file")
    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
    stream.use { input ->
        val bytes = input.readBytes()
        if (bytes.size > MAX_IMPORT_BYTES) error("The file is too large to be a backup")
        String(bytes, Charsets.UTF_8)
    }
}

private fun summary(c: BackupCounts): String = listOf(
    c.transactions to "transactions",
    c.accounts to "accounts",
    c.categories to "categories",
    c.groups to "groups",
    c.rules to "rules",
    c.budgets to "budgets",
    c.goals to "goals",
    c.contributions to "goal contributions",
    c.recurring to "recurring payments"
).joinToString(", ") { (n, label) -> "$n $label" }

@Composable
fun DataSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onResetComplete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { writeText(context, uri, viewModel.getBackupJson()) }
                viewModel.showFeedback(if (result.isSuccess) "Backup saved" else "Couldn't save the backup: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { writeText(context, uri, viewModel.getExportCsv()) }
                viewModel.showFeedback(if (result.isSuccess) "CSV saved" else "Couldn't save the CSV: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { readText(context, uri) }
                    .onSuccess { viewModel.loadImport(it) }
                    .onFailure { viewModel.reportImportError(it.message ?: "Couldn't read the file") }
            }
        }
    }

    SheetContainer(modifier = Modifier.padding(top = Spacing.lg)) {
        if (uiState.isPerformingAction) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Backup", subtitle = "Everything, in one file")
            SettingsBodyText(
                "Saves transactions, accounts, categories, rules, budgets, goals and recurring payments " +
                    "to a file you choose. The app never uploads your data."
            )
            PrimaryButton(
                text = "Export backup",
                onClick = { backupLauncher.launch("spendtracker-backup-${LocalDate.now()}.json") },
                enabled = !uiState.isPerformingAction,
                leadingIcon = Icons.Filled.FileDownload,
                fullWidth = true
            )
            TonalButton(
                text = "Import backup",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                enabled = !uiState.isPerformingAction,
                leadingIcon = Icons.Filled.FileUpload,
                fullWidth = true
            )
        }

        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Transactions (CSV)", subtitle = "For spreadsheets")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SecondaryButton(
                    text = "Save CSV",
                    onClick = { csvLauncher.launch("spendtracker-transactions-${LocalDate.now()}.csv") },
                    leadingIcon = Icons.Filled.FileDownload,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Copy",
                    onClick = {
                        scope.launch {
                            clipboard.setText(AnnotatedString(viewModel.getExportCsv()))
                            Toast.makeText(context, "CSV copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = Icons.Filled.ContentCopy,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Reset demo data")
            SettingsBodyText("Deletes all transactions and restores the demo transactions, built-in categories and default rules.")
            DestructiveButton(
                text = "Reset demo data",
                onClick = { showResetConfirm = true },
                enabled = !uiState.isPerformingAction,
                leadingIcon = Icons.Filled.Refresh,
                fullWidth = true
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset demo data?") },
            text = { Text("This deletes your transactions and resets categories and rules to the defaults. It can't be undone.") },
            confirmButton = {
                DestructiveButton(
                    text = "Reset",
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetDemoData {
                            // The sheet closes, so show the message here rather than via feedbackMessage.
                            viewModel.clearFeedback()
                            Toast.makeText(context, "Demo data reset", Toast.LENGTH_SHORT).show()
                            onResetComplete()
                        }
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    uiState.pendingImport?.let { pending -> ImportDialogs(pending, viewModel) }

    uiState.importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Can't import this file") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.cancelImport() }) { Text("OK") } }
        )
    }
}

@Composable
private fun ImportDialogs(pending: PendingImport, viewModel: SettingsViewModel) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val plan = pending.mergePlan

    if (!confirmReplace) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Import backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("The file contains ${summary(pending.data.counts)}.")
                    Text(
                        "Merge adds ${plan.added.transactions} new transactions and anything else missing; " +
                            "${plan.skipped.transactions} transactions already on this device are skipped.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Replace deletes everything on this device first, then restores the file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    DestructiveTextButton(text = "Replace all data…", onClick = { confirmReplace = true })
                }
            },
            confirmButton = { PrimaryButton(text = "Merge", onClick = { viewModel.confirmImport(ImportMode.MERGE) }) },
            dismissButton = { TextButton(onClick = { viewModel.cancelImport() }) { Text("Cancel") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "All transactions, accounts, categories, rules, budgets, goals and recurring payments on this " +
                        "device will be permanently deleted and replaced with the backup " +
                        "(${pending.data.counts.transactions} transactions). This can't be undone. " +
                        "Export a backup first if you're not sure."
                )
            },
            confirmButton = {
                DestructiveButton(text = "Replace everything", onClick = { viewModel.confirmImport(ImportMode.REPLACE) })
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Back") } }
        )
    }
}
