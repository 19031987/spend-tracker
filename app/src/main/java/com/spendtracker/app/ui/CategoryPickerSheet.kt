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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.TransactionType

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize

val DEFAULT_EMOJIS = listOf("🛒", "☕", "🍽️", "🛍️", "🎮", "🍿", "🏠", "⚡", "🚇", "⛽", "🚗", "💰", "🏋️", "📚", "💊", "✈️", "🏷️", "🔧")
val DEFAULT_PASTEL_COLORS = listOf("#FED7AA", "#FBCFE8", "#DBEAFE", "#D1FAE5", "#DDD6FE", "#FDE68A", "#E2E8F0", "#CFFAFE", "#FCE7F3")

fun parseColorHex(hex: String): Color {
    return runCatching {
        val clean = hex.removePrefix("#")
        val parsed = clean.toLong(16)
        if (clean.length == 6) Color(parsed or 0xFF000000L)
        else Color(parsed)
    }.getOrDefault(Color(0xFFFED7AA))
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
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
                modifier = Modifier.fillMaxSize()
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

    Column(modifier = modifier) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCreateMode) "Create Custom Category" else "Choose Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    if (isCreateMode) isCreateMode = false else onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

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
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                // Hierarchical Category List
                val filteredCategories = categories.filter { cat ->
                    !cat.isHidden &&
                        (transactionType == TransactionType.TRANSFER || cat.type == transactionType) &&
                        (searchQuery.isBlank() || cat.name.contains(searchQuery, ignoreCase = true))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val activeGroups = groups.filter { g ->
                        filteredCategories.any { it.groupId == g.id }
                    }

                    activeGroups.forEach { group ->
                        val groupCats = filteredCategories.filter { it.groupId == group.id }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(group.emoji, fontSize = 14.sp)
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            groupCats.forEach { cat ->
                                val isSelected = cat.key == selectedCategoryKey
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onCategorySelected(cat)
                                            onDismiss()
                                        },
                                    color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(parseColorHex(cat.colorHex)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(cat.emoji, fontSize = 18.sp)
                                            }
                                            Text(
                                                cat.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
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

                // Sticky Add New Category Footer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { isCreateMode = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("+ Add New Category", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // CREATE CUSTOM CATEGORY FORM
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Category Name") },
                        placeholder = { Text("e.g. Garden & Hardware") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Parent Group Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Parent Group", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        var groupDropdownExpanded by rememberSaveable { mutableStateOf(false) }
                        val activeGroupName = if (isInlineNewGroup) "+ New Group"
                        else groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Select Group"

                        Box {
                            OutlinedButton(
                                onClick = { groupDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
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
                                    text = { Text("➕ + Create New Group...", fontWeight = FontWeight.Bold) },
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
                                label = { Text("New Group Name") },
                                placeholder = { Text("e.g. Home Improvement") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Emoji Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Category Icon Emoji", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DEFAULT_EMOJIS.forEach { emoji ->
                                val isSelected = emoji == selectedEmoji
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedEmoji = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }

                    // Pastel Color Swatch Picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Badge Color Swatch", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            color = if (isSelected) Color.Black else Color.LightGray,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoRuleChecked,
                                onCheckedChange = { autoRuleChecked = it }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Always categorize future transactions from \"$currentMerchant\" as this category",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isCreateMode = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = newCatName.isNotBlank() && (!isInlineNewGroup || newGroupNameText.isNotBlank()),
                            onClick = {
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save & Assign", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

