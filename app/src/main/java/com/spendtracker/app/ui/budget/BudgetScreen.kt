package com.spendtracker.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.domain.budget.BudgetGroupSection
import com.spendtracker.app.domain.budget.BudgetLine
import com.spendtracker.app.domain.budget.BudgetSummary
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

@Composable
fun BudgetRoute(viewModel: BudgetViewModel, modifier: Modifier = Modifier) {
    val state: BudgetUiState by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetScreen(
        uiState = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSaveBudget = viewModel::setBudget,
        onRemoveBudget = viewModel::removeBudget,
        onApplySuggestions = viewModel::applySuggestedBudgets,
        modifier = modifier
    )
}

/** Category being edited in [EditBudgetSheet]. */
private data class BudgetEditTarget(
    val categoryKey: String,
    val name: String,
    val emoji: String,
    val colorHex: String
)

@Composable
fun BudgetScreen(
    uiState: BudgetUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSaveBudget: (categoryKey: String, amountMinor: Long, rollover: Boolean) -> Unit,
    onRemoveBudget: (categoryKey: String) -> Unit,
    onApplySuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing: BudgetEditTarget? by remember { mutableStateOf<BudgetEditTarget?>(null) }
    var showSuggestionDialog: Boolean by remember { mutableStateOf(false) }

    val summary: BudgetSummary? = uiState.summary

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "month") {
            MonthSelector(
                month = uiState.month,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        if (uiState.isLoading || summary == null) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            if (uiState.hasBudgets) {
                item(key = "hero") { LeftToSpendCard(summary) }
            } else {
                item(key = "empty") {
                    EmptyBudgetCard(
                        suggestionCount = uiState.suggestions.size,
                        onUseSuggestions = { showSuggestionDialog = true }
                    )
                }
            }

            for (section: BudgetGroupSection in summary.sections) {
                item(key = "group_header_${section.groupId}") {
                    GroupHeader(
                        name = section.groupName,
                        budgetedMinor = section.lines.sumOf { it.budgetedMinor + it.rolloverInMinor },
                        spentMinor = section.lines.sumOf { it.spentMinor }
                    )
                }
                item(key = "group_${section.groupId}") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column {
                            section.lines.forEachIndexed { index: Int, line: BudgetLine ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                BudgetLineRow(
                                    line = line,
                                    onClick = {
                                        editing = BudgetEditTarget(
                                            line.categoryKey, line.name, line.emoji, line.colorHex
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (summary.unbudgeted.isNotEmpty()) {
                item(key = "unbudgeted_header") {
                    GroupHeader(
                        name = "Unbudgeted spending",
                        budgetedMinor = null,
                        spentMinor = summary.unbudgetedSpentMinor
                    )
                }
                item(key = "unbudgeted") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column {
                            summary.unbudgeted.forEachIndexed { index: Int, line: BudgetLine ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                UnbudgetedRow(
                                    line = line,
                                    onSetBudget = {
                                        editing = BudgetEditTarget(
                                            line.categoryKey, line.name, line.emoji, line.colorHex
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.unusedCategories.isNotEmpty()) {
                item(key = "unused") {
                    UnusedCategories(
                        categories = uiState.unusedCategories,
                        onPick = { category: CategoryEntity ->
                            editing = BudgetEditTarget(
                                category.key, category.name, category.emoji, category.colorHex
                            )
                        }
                    )
                }
            }
        }
    }

    val target: BudgetEditTarget? = editing
    if (target != null) {
        val existing: BudgetEntity? = uiState.budgets.firstOrNull { it.categoryKey == target.categoryKey }
        EditBudgetSheet(
            categoryName = target.name,
            emoji = target.emoji,
            colorHex = target.colorHex,
            initialAmountMinor = existing?.amountMinor,
            initialRollover = existing?.rollover ?: false,
            onDismiss = { editing = null },
            onSave = { amountMinor: Long, rollover: Boolean ->
                onSaveBudget(target.categoryKey, amountMinor, rollover)
                editing = null
            },
            onRemove = if (existing != null) {
                {
                    onRemoveBudget(target.categoryKey)
                    editing = null
                }
            } else {
                null
            }
        )
    }

    if (showSuggestionDialog) {
        SuggestionsDialog(
            suggestions = uiState.suggestions,
            onConfirm = {
                onApplySuggestions()
                showSuggestionDialog = false
            },
            onDismiss = { showSuggestionDialog = false }
        )
    }
}

// region Month selector and hero

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun LeftToSpendCard(summary: BudgetSummary, modifier: Modifier = Modifier) {
    val left: Long = summary.leftToSpendMinor
    val isOver: Boolean = left < 0
    val available: Long = left + summary.totalSpentBudgetedMinor
    val rollover: Long = available - summary.totalBudgetedMinor
    val progress: Float = when {
        available > 0 -> summary.totalSpentBudgetedMinor.toFloat() / available
        summary.totalSpentBudgetedMinor > 0 -> 1f
        else -> 0f
    }
    val monthProgress: Float = summary.monthProgress
    val inMonth: Boolean = monthProgress > 0f && monthProgress < 1f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (isOver) "Over budget" else "Left to spend",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = CurrencyUtils.formatMinor(abs(left)),
                style = MaterialTheme.typography.displaySmall,
                color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${CurrencyUtils.formatMinor(summary.totalSpentBudgetedMinor)} spent of " +
                    "${CurrencyUtils.formatMinor(available)} budgeted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            ProgressWithTodayMarker(
                progress = progress,
                todayFraction = if (inMonth) monthProgress else null,
                isOver = progress >= 1f
            )
            Spacer(Modifier.height(12.dp))
            val hint: String? = when {
                !inMonth -> null
                progress >= 1f -> "Budget used up"
                progress > monthProgress + 0.05f -> "Ahead of pace"
                else -> "On pace"
            }
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hint == "On pace") {
                        SpendTrackerTheme.finance.income
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (rollover != 0L) {
                Text(
                    text = "Includes ${CurrencyUtils.formatSignedMinor(rollover)} rolled over",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.unbudgetedSpentMinor > 0) {
                Text(
                    text = "${CurrencyUtils.formatMinor(summary.unbudgetedSpentMinor)} unbudgeted spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Progress bar with a vertical "today" line drawn at [todayFraction] of the month. */
@Composable
private fun ProgressWithTodayMarker(
    progress: Float,
    todayFraction: Float?,
    isOver: Boolean,
    modifier: Modifier = Modifier
) {
    val markerColor: Color = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        if (todayFraction != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val x: Float = size.width * todayFraction.coerceIn(0f, 1f)
                        drawLine(
                            color = markerColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            )
        }
    }
}

// endregion

// region Sections and rows

@Composable
private fun GroupHeader(name: String, budgetedMinor: Long?, spentMinor: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (budgetedMinor != null) {
                "${CurrencyUtils.formatMinor(spentMinor)} of ${CurrencyUtils.formatMinor(budgetedMinor)}"
            } else {
                CurrencyUtils.formatMinor(spentMinor)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetLineRow(line: BudgetLine, onClick: () -> Unit) {
    val total: Long = line.budgetedMinor + line.rolloverInMinor
    val available: Long = line.availableMinor
    val isOver: Boolean = available < 0
    val barOver: Boolean = line.progress >= 1f && (isOver || total <= 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryEmojiBox(emoji = line.emoji, colorHex = line.colorHex, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isOver) {
                        "${CurrencyUtils.formatMinor(abs(available))} over"
                    } else {
                        "${CurrencyUtils.formatMinor(available)} left"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { line.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (barOver || line.progress >= 1f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${CurrencyUtils.formatMinor(line.spentMinor)} of ${CurrencyUtils.formatMinor(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (line.rolloverInMinor != 0L) {
                    RolloverChip(line.rolloverInMinor)
                }
            }
        }
    }
}

@Composable
private fun RolloverChip(rolloverMinor: Long) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = "Rollover ${CurrencyUtils.formatSignedMinor(rolloverMinor)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun UnbudgetedRow(line: BudgetLine, onSetBudget: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryEmojiBox(emoji = line.emoji, colorHex = line.colorHex, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${CurrencyUtils.formatMinor(line.spentMinor)} spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onSetBudget) {
            Text("Set budget")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnusedCategories(categories: List<CategoryEntity>, onPick: (CategoryEntity) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "Add a budget",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (category: CategoryEntity in categories) {
                AssistChip(
                    onClick = { onPick(category) },
                    label = { Text("${category.emoji} ${category.name}") }
                )
            }
        }
    }
}

// endregion

// region Empty state and suggestions

@Composable
private fun EmptyBudgetCard(
    suggestionCount: Int,
    onUseSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎯", fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Set budgets for your categories",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (suggestionCount > 0) {
                    "Start from what you usually spend: we can suggest a monthly amount for " +
                        "$suggestionCount ${if (suggestionCount == 1) "category" else "categories"} " +
                        "based on the last three months."
                } else {
                    "Pick a category below to give it a monthly limit and track what is left to spend."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (suggestionCount > 0) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onUseSuggestions) {
                    Text("Use suggested budgets")
                }
            }
        }
    }
}

@Composable
private fun SuggestionsDialog(
    suggestions: List<SuggestedBudget>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use suggested budgets?") },
        text = {
            Column {
                Text(
                    text = "Monthly budgets based on your average spending over the last three months, " +
                        "rounded up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (suggestion: SuggestedBudget in suggestions) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = suggestion.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = suggestion.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = CurrencyUtils.formatMinor(suggestion.amountMinor),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row {
                    Text(
                        text = "Total per month",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyUtils.formatMinor(suggestions.sumOf { it.amountMinor }),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// endregion

// region Edit sheet

private fun currencySymbol(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).symbol }.getOrNull() ?: "£"

/**
 * Bottom sheet to set, change or remove the budget of one category.
 * [onRemove] is null when the category has no budget yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBudgetSheet(
    categoryName: String,
    emoji: String,
    colorHex: String,
    initialAmountMinor: Long?,
    initialRollover: Boolean,
    onDismiss: () -> Unit,
    onSave: (amountMinor: Long, rollover: Boolean) -> Unit,
    onRemove: (() -> Unit)?
) {
    var amountText: String by remember {
        mutableStateOf(
            if (initialAmountMinor != null && initialAmountMinor > 0) {
                CurrencyUtils.minorToDecimalString(initialAmountMinor).removeSuffix(".00")
            } else {
                ""
            }
        )
    }
    var rollover: Boolean by remember { mutableStateOf(initialRollover) }
    val parsed: Long? = CurrencyUtils.parseDecimalToMinor(amountText)
    val showError: Boolean = amountText.isNotBlank() && parsed == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryEmojiBox(emoji = emoji, colorHex = colorHex, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = categoryName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Monthly budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                prefix = { Text(currencySymbol()) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text("Enter an amount with up to 2 decimals") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Roll over", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Carry unspent (or overspent) money from last month into this one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = rollover, onCheckedChange = { rollover = it })
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val amount: Long? = parsed
                    if (amount != null) onSave(amount, rollover)
                },
                enabled = parsed != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            if (onRemove != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove budget", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// endregion
