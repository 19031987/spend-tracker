package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.MatchType
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.EmptyState
import com.spendtracker.app.ui.components.PrimaryButton
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.theme.Spacing

private fun matchLabel(matchType: String): String =
    MatchType.values().firstOrNull { it.name == matchType }?.label ?: matchType

private fun ruleCategories(categories: List<CategoryEntity>): List<CategoryEntity> =
    categories.filter { !it.isHidden && it.type != TransactionType.TRANSFER && it.key != "INTERNAL_TRANSFER" }

@Composable
fun RulesSection(
    viewModel: SettingsViewModel,
    rules: List<MerchantRuleEntity>,
    categories: List<CategoryEntity>
) {
    val pickable = ruleCategories(categories)
    var pattern by rememberSaveable { mutableStateOf("") }
    var matchType by rememberSaveable { mutableStateOf(MatchType.CONTAINS.name) }
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedKey = categoryKey ?: pickable.firstOrNull()?.key
    var editingRuleId by rememberSaveable { mutableStateOf<Long?>(null) }

    SheetContainer(modifier = Modifier.padding(top = Spacing.lg)) {
        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("New rule", subtitle = "Automatically categorise matching merchants")
            SettingsDropdown(
                label = "Merchant name",
                selected = MatchType.values().firstOrNull { it.name == matchType },
                options = MatchType.values().toList(),
                optionLabel = { it.label },
                onSelect = { matchType = it.name },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Text to match") },
                placeholder = { Text("e.g. Tesco, Shell, Costa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SettingsDropdown(
                label = "Category",
                selected = pickable.firstOrNull { it.key == selectedKey },
                options = pickable,
                optionLabel = { "${it.emoji} ${it.name}" },
                onSelect = { categoryKey = it.key },
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryButton(
                text = "Add rule",
                onClick = {
                    val key = selectedKey ?: return@PrimaryButton
                    viewModel.addRule(matchType, pattern.trim(), key)
                    pattern = ""
                },
                enabled = pattern.isNotBlank() && selectedKey != null,
                leadingIcon = Icons.Filled.Add,
                fullWidth = true
            )
        }

        SectionHeader("Active rules (${rules.size})")
        if (rules.isEmpty()) {
            EmptyState(body = "No rules yet. Rules you add here, or when creating a category, appear here.")
        } else {
            AppCard(verticalSpacing = Spacing.xs) {
                rules.forEach { r ->
                    val cat = categories.firstOrNull { it.key == r.categoryKey }
                    SettingsListRow(
                        title = "${matchLabel(r.matchType)} \"${r.pattern}\"",
                        supporting = "→ ${cat?.name ?: r.categoryKey}",
                        onClick = { editingRuleId = r.id },
                        leading = { CategoryEmojiBox(emoji = cat?.emoji ?: "🏷️", colorHex = cat?.colorHex, size = 40.dp) },
                        trailing = {
                            IconButton(onClick = { viewModel.deleteRule(r.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }

    val editing = rules.firstOrNull { it.id == editingRuleId }
    if (editing != null) {
        EditRuleSheet(
            rule = editing,
            categories = categories,
            onSave = { m, p, c ->
                viewModel.updateRule(editing.id, m, p, c)
                editingRuleId = null
            },
            onDelete = {
                viewModel.deleteRule(editing.id)
                editingRuleId = null
            },
            onDismiss = { editingRuleId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRuleSheet(
    rule: MerchantRuleEntity,
    categories: List<CategoryEntity>,
    onSave: (matchType: String, pattern: String, categoryKey: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var matchType by rememberSaveable(rule.id) { mutableStateOf(rule.matchType) }
    var pattern by rememberSaveable(rule.id) { mutableStateOf(rule.pattern) }
    var categoryKey by rememberSaveable(rule.id) { mutableStateOf(rule.categoryKey) }
    // Keep the rule's current category selectable even if it is hidden.
    val pickable = ruleCategories(categories).let { list ->
        if (list.any { it.key == rule.categoryKey }) list
        else list + categories.filter { it.key == rule.categoryKey }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader("Edit rule")
            SettingsDropdown(
                label = "Merchant name",
                selected = MatchType.values().firstOrNull { it.name == matchType },
                options = MatchType.values().toList(),
                optionLabel = { it.label },
                onSelect = { matchType = it.name },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Text to match") },
                singleLine = true,
                isError = pattern.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            SettingsDropdown(
                label = "Category",
                selected = pickable.firstOrNull { it.key == categoryKey },
                options = pickable,
                optionLabel = { "${it.emoji} ${it.name}" },
                onSelect = { categoryKey = it.key },
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                confirmLabel = "Save",
                onConfirm = { onSave(matchType, pattern.trim(), categoryKey) },
                onDismiss = onDismiss,
                confirmEnabled = pattern.isNotBlank()
            )
            DestructiveTextButton(
                text = "Delete rule",
                onClick = onDelete,
                leadingIcon = Icons.Outlined.Delete,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
