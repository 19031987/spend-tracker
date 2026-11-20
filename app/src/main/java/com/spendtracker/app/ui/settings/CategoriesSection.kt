package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.spendtracker.app.data.CatalogSeed
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.Spacing

private fun CategoryEntity.isListed(): Boolean =
    type != TransactionType.TRANSFER && key != "INTERNAL_TRANSFER"

@Composable
fun CategoriesSection(
    viewModel: SettingsViewModel,
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    onAddCategory: () -> Unit
) {
    var editingCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    SheetContainer(modifier = Modifier.padding(top = Spacing.lg)) {
        SectionHeader(
            "Categories",
            subtitle = "Tap a category or group to edit it",
            actionLabel = "Add",
            actionIcon = Icons.Filled.Add,
            actionIconLeading = true,
            onAction = onAddCategory
        )

        groups.forEach { group ->
            val groupCats = categories.filter { it.groupId == group.id && !it.isHidden && it.isListed() }
            AppCard(verticalSpacing = Spacing.xs) {
                SectionHeader(
                    "${group.emoji} ${group.name}",
                    subtitle = "${groupCats.size} ${if (groupCats.size == 1) "category" else "categories"}",
                    actionLabel = "Edit",
                    actionIcon = Icons.Outlined.Edit,
                    actionIconLeading = true,
                    onAction = { editingGroupId = group.id }
                )
                groupCats.forEach { cat ->
                    SettingsListRow(
                        title = cat.name,
                        supporting = if (cat.isBuiltIn) "Built-in" else null,
                        onClick = { editingCategoryKey = cat.key },
                        leading = { CategoryEmojiBox(emoji = cat.emoji, colorHex = cat.colorHex, size = 40.dp) },
                        trailing = {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Edit ${cat.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        val hidden = categories.filter { it.isHidden && it.isListed() }
        if (hidden.isNotEmpty()) {
            AppCard(verticalSpacing = Spacing.xs) {
                SectionHeader(
                    "Hidden (${hidden.size})",
                    subtitle = "Built-in categories you removed",
                    actionLabel = if (showHidden) "Hide" else "Show",
                    actionIcon = if (showHidden) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    onAction = { showHidden = !showHidden }
                )
                if (showHidden) {
                    hidden.forEach { cat ->
                        SettingsListRow(
                            title = cat.name,
                            supporting = groups.firstOrNull { it.id == cat.groupId }?.name,
                            leading = { CategoryEmojiBox(emoji = cat.emoji, colorHex = cat.colorHex, size = 40.dp) },
                            trailing = {
                                TonalButton(
                                    text = "Restore",
                                    onClick = { viewModel.restoreCategory(cat.key) },
                                    leadingIcon = Icons.Filled.Restore
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val editingCategory = categories.firstOrNull { it.key == editingCategoryKey }
    if (editingCategory != null) {
        EditCategorySheet(
            category = editingCategory,
            groups = groups,
            onSave = { name, emoji, color, groupId ->
                viewModel.updateCategory(editingCategory.key, name, emoji, color, groupId)
                editingCategoryKey = null
            },
            onRemove = {
                viewModel.deleteCategory(editingCategory.key)
                editingCategoryKey = null
            },
            onDismiss = { editingCategoryKey = null }
        )
    }

    val editingGroup = groups.firstOrNull { it.id == editingGroupId }
    if (editingGroup != null) {
        EditGroupSheet(
            group = editingGroup,
            groups = groups,
            categoryCount = categories.count { it.groupId == editingGroup.id },
            viewModel = viewModel,
            onDismiss = { editingGroupId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCategorySheet(
    category: CategoryEntity,
    groups: List<CategoryGroupEntity>,
    onSave: (name: String, emoji: String, colorHex: String, groupId: Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(category.key) { mutableStateOf(category.name) }
    var emoji by rememberSaveable(category.key) { mutableStateOf(category.emoji) }
    var colorHex by rememberSaveable(category.key) { mutableStateOf(category.colorHex) }
    var groupId by rememberSaveable(category.key) { mutableStateOf(category.groupId) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader("Edit category", subtitle = if (category.isBuiltIn) "Built-in category" else null)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= 8) emoji = it },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader("Colour", small = true)
                ColorSwatchPicker(selectedHex = colorHex, onSelect = { colorHex = it })
            }
            SettingsDropdown(
                label = "Group",
                selected = groups.firstOrNull { it.id == groupId },
                options = groups,
                optionLabel = { "${it.emoji} ${it.name}" },
                onSelect = { groupId = it.id },
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                confirmLabel = "Save",
                onConfirm = { onSave(name, emoji, colorHex, groupId) },
                onDismiss = onDismiss,
                confirmEnabled = name.isNotBlank()
            )
            DestructiveTextButton(
                text = if (category.isBuiltIn) "Hide category" else "Delete category",
                onClick = { confirmRemove = true },
                leadingIcon = Icons.Outlined.Delete,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (category.isBuiltIn) "Hide \"${category.name}\"?" else "Delete \"${category.name}\"?") },
            text = {
                Text(
                    if (category.isBuiltIn) {
                        "It disappears from pickers and lists. Its transactions keep the category, and you can restore it from Hidden."
                    } else {
                        "Its rules are deleted and its transactions become uncategorised. This can't be undone."
                    }
                )
            },
            confirmButton = {
                DestructiveButton(
                    text = if (category.isBuiltIn) "Hide" else "Delete",
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    }
                )
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupSheet(
    group: CategoryGroupEntity,
    groups: List<CategoryGroupEntity>,
    categoryCount: Int,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(group.id) { mutableStateOf(group.name) }
    var emoji by rememberSaveable(group.id) { mutableStateOf(group.emoji) }
    var confirmDelete by remember { mutableStateOf(false) }
    val otherGroups = groups.filter { it.id != group.id }
    var moveToId by rememberSaveable(group.id) {
        mutableStateOf(
            otherGroups.firstOrNull { it.id == CatalogSeed.OTHER_GROUP_ID }?.id ?: otherGroups.firstOrNull()?.id
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader("Edit group", subtitle = if (group.isBuiltIn) "Built-in group" else null)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= 8) emoji = it },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                confirmLabel = "Save",
                onConfirm = {
                    viewModel.updateGroup(group.id, name, emoji)
                    onDismiss()
                },
                onDismiss = onDismiss,
                confirmEnabled = name.isNotBlank()
            )
            if (group.isBuiltIn) {
                SettingsBodyText(
                    "Built-in groups can be renamed but not deleted.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (otherGroups.isNotEmpty()) {
                DestructiveTextButton(
                    text = "Delete group",
                    onClick = { confirmDelete = true },
                    leadingIcon = Icons.Outlined.Delete,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${group.name}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (categoryCount == 0) {
                        Text("This group has no categories.")
                    } else {
                        Text("Its $categoryCount ${if (categoryCount == 1) "category moves" else "categories move"} to:")
                        SettingsDropdown(
                            label = "Move to group",
                            selected = otherGroups.firstOrNull { it.id == moveToId },
                            options = otherGroups,
                            optionLabel = { "${it.emoji} ${it.name}" },
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
                    text = "Delete",
                    enabled = moveToId != null,
                    onClick = {
                        val target = moveToId ?: return@DestructiveButton
                        viewModel.deleteGroup(group.id, target) { err ->
                            if (err == null) {
                                confirmDelete = false
                                onDismiss()
                            } else {
                                error = err
                            }
                        }
                    }
                )
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
