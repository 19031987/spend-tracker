@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.ColorUtils

val DEFAULT_EMOJIS = listOf("🛒", "☕", "🍽️", "🛍️", "🎮", "🍿", "🏠", "⚡", "🚇", "⛽", "🚗", "💰", "🏋️", "📚", "💊", "✈️", "🏷️", "🔧")
val DEFAULT_PASTEL_COLORS = listOf("#FED7AA", "#FBCFE8", "#DBEAFE", "#D1FAE5", "#DDD6FE", "#FDE68A", "#E2E8F0", "#CFFAFE", "#FCE7F3")

fun parseColorHex(hex: String): Color = ColorUtils.parseColorHex(hex)

/**
 * "Category" field shared by the Add and Edit transaction sheets: category avatar + name
 * (or an amber "Uncategorized" placeholder) with a "Change" action. Tapping anywhere opens
 * the picker.
 */
@Composable
internal fun CategoryField(
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finance = SpendTrackerTheme.finance
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (category != null) {
                    CategoryEmojiBox(emoji = category.emoji, colorHex = category.colorHex, size = 36.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(finance.warningContainer)
                            .border(1.dp, finance.warning, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❓", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    text = category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (category != null) MaterialTheme.colorScheme.onSurface else finance.warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClick) { Text("Change") }
            }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    selectedCategoryKey: String?,
    currentMerchant: String?,
    transactionType: TransactionType,
    onDismiss: () -> Unit,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCreateCategory: (
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        ruleMerchant: String?
    ) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            CategoryPickerContent(
                groups = groups,
                categories = categories,
                selectedCategoryKey = selectedCategoryKey,
                currentMerchant = currentMerchant,
                transactionType = transactionType,
                onDismiss = onDismiss,
                onCategorySelected = onCategorySelected,
                onCreateCategory = onCreateCategory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = Spacing.md)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    selectedCategoryKey: String?,
    currentMerchant: String?,
    transactionType: TransactionType,
    onDismiss: () -> Unit,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCreateCategory: (
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        ruleMerchant: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CategoryPickerContent(
            groups = groups,
            categories = categories,
            selectedCategoryKey = selectedCategoryKey,
            currentMerchant = currentMerchant,
            transactionType = transactionType,
            onDismiss = onDismiss,
            onCategorySelected = onCategorySelected,
            onCreateCategory = onCreateCategory,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerContent(
    groups: List<CategoryGroupEntity>,
    categories: List<CategoryEntity>,
    selectedCategoryKey: String?,
    currentMerchant: String?,
    transactionType: TransactionType,
    onDismiss: () -> Unit,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCreateCategory: (
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        ruleMerchant: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCreateMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Creation Form state
    var newCatName by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf(groups.firstOrNull()?.id ?: 1L) }
    var isInlineNewGroup by rememberSaveable { mutableStateOf(false) }
    var newGroupNameText by rememberSaveable { mutableStateOf("") }
    var selectedEmoji by rememberSaveable { mutableStateOf("🏷️") }
    var selectedColorHex by rememberSaveable { mutableStateOf("#FED7AA") }
    var autoRuleChecked by rememberSaveable { mutableStateOf(true) }

    val sidePadding = ScreenPadding.sheetHorizontalPadding

    Column(modifier = modifier) {
            SheetHeader(
                title = if (isCreateMode) "New category" else "Choose category",
                onClose = { if (isCreateMode) isCreateMode = false else onDismiss() },
                modifier = Modifier.padding(start = sidePadding, end = Spacing.xs, bottom = Spacing.sm)
            )

            if (!isCreateMode) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search category (e.g. Groceries, Coffee)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding)
                )

                // Hierarchical Category List
                val filteredCategories = categories.filter { cat ->
                    !cat.isHidden &&
                        cat.type != TransactionType.TRANSFER &&
                        cat.key != "INTERNAL_TRANSFER" &&
                        cat.type == transactionType &&
                        (searchQuery.isBlank() || cat.name.contains(searchQuery, ignoreCase = true))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = sidePadding, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    val activeGroups = groups.filter { g ->
                        filteredCategories.any { it.groupId == g.id }
                    }

                    activeGroups.forEach { group ->
                        val groupCats = filteredCategories.filter { it.groupId == group.id }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SectionHeader(title = "${group.emoji}  ${group.name}", small = true)

                            groupCats.forEach { cat ->
                                val isSelected = cat.key == selectedCategoryKey
                                Surface(
                                    onClick = {
                                        onCategorySelected(cat)
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        CategoryEmojiBox(emoji = cat.emoji, colorHex = cat.colorHex, size = 36.dp)
                                        Text(
                                            cat.name,
                                            style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sticky "Add category" footer
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TonalButton(
                    text = "Add category",
                    onClick = { isCreateMode = true },
                    leadingIcon = Icons.Filled.Add,
                    fullWidth = true,
                    modifier = Modifier.padding(horizontal = sidePadding, vertical = Spacing.md)
                )
            } else {
                // CREATE CUSTOM CATEGORY FORM
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = sidePadding, end = sidePadding, top = Spacing.sm, bottom = ScreenPadding.sheetBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(ScreenPadding.sheetItemSpacing)
                ) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Category name") },
                        placeholder = { Text("e.g. Garden & Hardware") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Parent Group Selector
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Parent group", style = MaterialTheme.typography.labelLarge)
                        var groupDropdownExpanded by rememberSaveable { mutableStateOf(false) }
                        val activeGroupName = if (isInlineNewGroup) "New group"
                        else groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Select group"

                        Box {
                            OutlinedButton(
                                onClick = { groupDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(activeGroupName, modifier = Modifier.weight(1f))
                            }
                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false }
                            ) {
                                groups.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text("${g.emoji} ${g.name}") },
                                        onClick = {
                                            selectedGroupId = g.id
                                            isInlineNewGroup = false
                                            groupDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("New group…") },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = {
                                        isInlineNewGroup = true
                                        groupDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (isInlineNewGroup) {
                            OutlinedTextField(
                                value = newGroupNameText,
                                onValueChange = { newGroupNameText = it },
                                label = { Text("New group name") },
                                placeholder = { Text("e.g. Home Improvement") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Emoji Selector
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Icon", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            DEFAULT_EMOJIS.forEach { emoji ->
                                val isSelected = emoji == selectedEmoji
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .clickable { selectedEmoji = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }

                    // Pastel Color Swatch Picker
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Colour", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            DEFAULT_PASTEL_COLORS.forEach { hex ->
                                val color = parseColorHex(hex)
                                val isSelected = hex == selectedColorHex
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }

                    // Rule Checkbox
                    if (!currentMerchant.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Checkbox(
                                checked = autoRuleChecked,
                                onCheckedChange = { autoRuleChecked = it }
                            )
                            Text(
                                "Always categorize future transactions from \"$currentMerchant\" as this category",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    SheetActions(
                        confirmLabel = "Save",
                        confirmEnabled = newCatName.isNotBlank() && (!isInlineNewGroup || newGroupNameText.isNotBlank()),
                        onConfirm = {
                            onCreateCategory(
                                newCatName.trim(),
                                if (isInlineNewGroup) null else selectedGroupId,
                                if (isInlineNewGroup) newGroupNameText.trim() else null,
                                selectedEmoji,
                                selectedColorHex,
                                transactionType,
                                if (autoRuleChecked) currentMerchant?.trim() else null
                            )
                            onDismiss()
                        },
                        onDismiss = { isCreateMode = false }
                    )
                }
            }
        }
    }
