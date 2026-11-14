package com.spendtracker.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.recurring.ManualRecurring
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringCandidate
import com.spendtracker.app.domain.recurring.RecurringDetector
import com.spendtracker.app.domain.recurring.RecurringDraft
import com.spendtracker.app.domain.recurring.RecurringFormErrors
import com.spendtracker.app.domain.recurring.RecurringFrequency
import com.spendtracker.app.domain.recurring.RecurringOccurrence
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.EmptyState
import com.spendtracker.app.ui.components.SecondaryButton
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.components.StatusChip
import com.spendtracker.app.ui.components.StatusTone
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.screenContentPadding
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun RecurringRoute(viewModel: RecurringViewModel, modifier: Modifier = Modifier) {
    val state: RecurringUiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringScreen(
        uiState = state,
        onSelectView = viewModel::selectView,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectDay = viewModel::selectDay,
        onApprove = viewModel::approve,
        onIgnore = viewModel::ignore,
        onStopTracking = viewModel::stopTracking,
        onAddManual = viewModel::addManual,
        onUpdateItem = viewModel::updateItem,
        onRestore = viewModel::restore,
        onDelete = viewModel::delete,
        modifier = modifier
    )
}

/**
 * @param onAddManual returns an error message to show in the sheet, or null when saved.
 * @param onUpdateItem returns an error message to show in the sheet, or null when saved.
 */
@Composable
fun RecurringScreen(
    uiState: RecurringUiState,
    onSelectView: (RecurringView) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onApprove: (RecurringCandidate) -> Unit,
    onIgnore: (RecurringCandidate) -> Unit,
    onStopTracking: (Long) -> Unit,
    onAddManual: (RecurringDraft) -> String?,
    onUpdateItem: (Long, RecurringDraft) -> String?,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdd: Boolean by rememberSaveable { mutableStateOf(false) }
    var editingId: Long? by rememberSaveable { mutableStateOf<Long?>(null) }
    var ignoredExpanded: Boolean by rememberSaveable { mutableStateOf(false) }
    var stopTarget: RecurringItemRow? by remember { mutableStateOf<RecurringItemRow?>(null) }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
        ) {
            item(key = "views") {
                ViewSelector(selected = uiState.view, onSelect = onSelectView)
            }

            if (uiState.items.isNotEmpty()) {
                item(key = "summary") { SummaryCard(uiState.summary) }
            }

            if (uiState.suggestions.isNotEmpty()) {
                suggestionsSection(
                    suggestions = uiState.suggestions,
                    categories = uiState.categories,
                    onApprove = onApprove,
                    onIgnore = onIgnore
                )
            }

            when (uiState.view) {
                RecurringView.UPCOMING -> upcomingSection(
                    uiState,
                    onAdd = { showAdd = true },
                    onEditItem = { id: Long -> editingId = id }
                )
                RecurringView.CALENDAR -> calendarSection(
                    uiState,
                    onPreviousMonth,
                    onNextMonth,
                    onSelectDay,
                    onEditItem = { id: Long -> editingId = id }
                )
                RecurringView.ALL -> allSection(
                    state = uiState,
                    ignoredExpanded = ignoredExpanded,
                    onToggleIgnored = { ignoredExpanded = !ignoredExpanded },
                    onAdd = { showAdd = true },
                    onEdit = { editingId = it.item.id },
                    onStopTracking = { stopTarget = it },
                    onRestore = onRestore,
                    onRemoveIgnored = onDelete
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add recurring") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
        )
    }

    val categories: List<CategoryEntity> = uiState.categories.values.toList()

    if (showAdd) {
        RecurringFormSheet(
            editing = null,
            categories = categories,
            today = uiState.today,
            onDismiss = { showAdd = false },
            onSubmit = { draft: RecurringDraft ->
                val error: String? = onAddManual(draft)
                if (error == null) showAdd = false
                error
            },
            onDelete = null
        )
    }

    val editingRow: RecurringItemRow? = editingId?.let { id: Long -> uiState.items.firstOrNull { it.item.id == id } }
    if (editingRow != null) {
        RecurringFormSheet(
            editing = editingRow,
            categories = categories,
            today = uiState.today,
            onDismiss = { editingId = null },
            onSubmit = { draft: RecurringDraft ->
                val error: String? = onUpdateItem(editingRow.item.id, draft)
                if (error == null) editingId = null
                error
            },
            onDelete = {
                onDelete(editingRow.item.id)
                editingId = null
            }
        )
    }

    val stopping: RecurringItemRow? = stopTarget
    if (stopping != null) {
        AlertDialog(
            onDismissRequest = { stopTarget = null },
            title = { Text("Stop tracking ${stopping.item.displayName}?") },
            text = {
                Text("It will no longer appear in Upcoming or the calendar. You can restore it from Ignored in the All view.")
            },
            confirmButton = {
                DestructiveButton(
                    text = "Stop tracking",
                    onClick = {
                        onStopTracking(stopping.item.id)
                        stopTarget = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { stopTarget = null }) { Text("Cancel") }
            }
        )
    }
}

// ---- sections ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSelector(selected: RecurringView, onSelect: (RecurringView) -> Unit) {
    val views: List<RecurringView> = RecurringView.values().toList()
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        views.forEachIndexed { index: Int, view: RecurringView ->
            SegmentedButton(
                selected = selected == view,
                onClick = { onSelect(view) },
                shape = SegmentedButtonDefaults.itemShape(index, views.size),
                icon = {}
            ) { Text(view.label) }
        }
    }
}

@Composable
private fun SummaryCard(summary: RecurringMonthSummary) {
    AppCard(verticalSpacing = Spacing.xs) {
        Text(
            text = "Remaining due in ${summary.month.format(monthNameFormatter())}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyUtils.formatMinor(summary.remainingDueMinor),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            CountLabel(statusColor(OccurrenceStatus.PAID), "${summary.paidCount} paid")
            CountLabel(statusColor(OccurrenceStatus.UPCOMING), "${summary.upcomingCount} upcoming")
            if (summary.overdueCount > 0) {
                CountLabel(statusColor(OccurrenceStatus.OVERDUE), "${summary.overdueCount} overdue")
            }
        }
    }
}

@Composable
private fun CountLabel(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun LazyListScope.suggestionsSection(
    suggestions: List<RecurringCandidate>,
    categories: Map<String, CategoryEntity>,
    onApprove: (RecurringCandidate) -> Unit,
    onIgnore: (RecurringCandidate) -> Unit
) {
    item(key = "suggestions-header") {
        val noun: String = if (suggestions.size == 1) "payment" else "payments"
        SectionHeader(
            title = "We found ${suggestions.size} possible recurring $noun",
            subtitle = "Approve the ones you want to keep an eye on."
        )
    }
    items(suggestions, key = { "suggestion-${it.merchantKey}-${it.isIncome}" }) { candidate: RecurringCandidate ->
        SuggestionCard(candidate, categories[candidate.categoryKey], onApprove, onIgnore)
    }
}

@Composable
private fun SuggestionCard(
    candidate: RecurringCandidate,
    category: CategoryEntity?,
    onApprove: (RecurringCandidate) -> Unit,
    onIgnore: (RecurringCandidate) -> Unit
) {
    AppCard(verticalSpacing = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CategoryEmojiBox(emoji = category?.emoji ?: DEFAULT_EMOJI, colorHex = category?.colorHex)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val amount: String = (if (candidate.amountVaries) "About " else "") +
                    CurrencyUtils.formatMinor(candidate.typicalAmountMinor)
                Text(
                    text = "$amount · ${candidate.frequency.label} · next ${formatDay(candidate.nextDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(text = "Ignore", onClick = { onIgnore(candidate) })
            TonalButton(text = "Approve", onClick = { onApprove(candidate) })
        }
    }
}

private fun LazyListScope.upcomingSection(
    state: RecurringUiState,
    onAdd: () -> Unit,
    onEditItem: (Long) -> Unit
) {
    if (state.upcoming.isEmpty()) {
        item(key = "upcoming-empty") {
            if (state.items.isEmpty()) {
                EmptyState(
                    title = "No recurring payments yet",
                    body = if (state.suggestions.isEmpty()) {
                        "When a payment repeats, like a subscription or rent, we will spot it and ask if you want to track it. You can also add one yourself."
                    } else {
                        "Approve one of the suggestions above, or add one yourself, and its due dates will show up here."
                    },
                    icon = Icons.Filled.Repeat,
                    actionLabel = "Add recurring payment",
                    onAction = onAdd
                )
            } else {
                EmptyState(
                    title = "Nothing due soon",
                    body = "None of your recurring payments are due in the next 30 days.",
                    icon = Icons.Filled.Repeat
                )
            }
        }
        return
    }

    val today: LocalDate = state.today
    val weekEnd: LocalDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val thisMonth: YearMonth = YearMonth.from(today)
    val groups: List<Pair<String, List<RecurringOccurrence>>> = listOf(
        "Overdue" to state.upcoming.filter { it.expectedDate.isBefore(today) },
        "This week" to state.upcoming.filter { !it.expectedDate.isBefore(today) && !it.expectedDate.isAfter(weekEnd) },
        "Later this month" to state.upcoming.filter {
            it.expectedDate.isAfter(weekEnd) && YearMonth.from(it.expectedDate) == thisMonth
        },
        "Next month" to state.upcoming.filter {
            it.expectedDate.isAfter(weekEnd) && YearMonth.from(it.expectedDate) != thisMonth
        }
    ).filter { it.second.isNotEmpty() }

    groups.forEach { group: Pair<String, List<RecurringOccurrence>> ->
        item(key = "upcoming-${group.first}") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = group.first, small = true)
                OccurrenceListCard(group.second, state.categories, onEditItem)
            }
        }
    }
}

private fun LazyListScope.calendarSection(
    state: RecurringUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onEditItem: (Long) -> Unit
) {
    item(key = "calendar") {
        CalendarCard(state, onPreviousMonth, onNextMonth, onSelectDay)
    }

    val selected: LocalDate? = state.selectedDay
    val shown: List<RecurringOccurrence> =
        if (selected == null) state.monthOccurrences
        else state.monthOccurrences.filter { it.expectedDate == selected }

    item(key = "calendar-list") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(
                title = if (selected == null) "All payments in ${state.month.format(monthNameFormatter())}"
                else selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                small = true
            )
            if (shown.isEmpty()) {
                EmptyState(
                    title = if (selected == null) "Nothing expected this month" else "Nothing due on this day",
                    body = "Tap another day to see what is coming up.",
                    icon = Icons.Filled.Repeat
                )
            } else {
                OccurrenceListCard(shown, state.categories, onEditItem)
            }
        }
    }
}

private fun LazyListScope.allSection(
    state: RecurringUiState,
    ignoredExpanded: Boolean,
    onToggleIgnored: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RecurringItemRow) -> Unit,
    onStopTracking: (RecurringItemRow) -> Unit,
    onRestore: (Long) -> Unit,
    onRemoveIgnored: (Long) -> Unit
) {
    if (state.items.isEmpty()) {
        item(key = "all-empty") {
            EmptyState(
                title = "Nothing tracked yet",
                body = "Approved and added recurring payments are listed here, along with their frequency and next date.",
                icon = Icons.Filled.Repeat,
                actionLabel = "Add recurring payment",
                onAction = onAdd
            )
        }
    } else {
        items(state.items, key = { "item-${it.item.id}" }) { row: RecurringItemRow ->
            TrackedItemCard(
                row = row,
                category = state.categories[row.item.categoryKey],
                onEdit = { onEdit(row) },
                onStopTracking = { onStopTracking(row) }
            )
        }
    }

    if (state.ignored.isNotEmpty()) {
        item(key = "ignored-header") {
            SectionHeader(
                title = "Ignored (${state.ignored.size})",
                subtitle = if (ignoredExpanded) "Restore to track again, or remove so it can be suggested again." else null,
                small = true,
                actionLabel = if (ignoredExpanded) "Hide" else "Show",
                actionIcon = if (ignoredExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                onAction = onToggleIgnored
            )
        }
        if (ignoredExpanded) {
            items(state.ignored, key = { "ignored-${it.id}" }) { item: RecurringItemEntity ->
                IgnoredItemCard(
                    item = item,
                    category = state.categories[item.categoryKey],
                    onRestore = { onRestore(item.id) },
                    onRemove = { onRemoveIgnored(item.id) }
                )
            }
        }
    }
}

// ---- building blocks --------------------------------------------------------------------

@Composable
private fun statusColor(status: OccurrenceStatus): Color = when (status) {
    OccurrenceStatus.PAID -> SpendTrackerTheme.finance.income
    OccurrenceStatus.PAID_DIFFERENT_AMOUNT -> SpendTrackerTheme.finance.warning
    OccurrenceStatus.UPCOMING -> MaterialTheme.colorScheme.secondary
    OccurrenceStatus.OVERDUE -> MaterialTheme.colorScheme.error
}

private fun statusTone(status: OccurrenceStatus): StatusTone = when (status) {
    OccurrenceStatus.PAID -> StatusTone.Positive
    OccurrenceStatus.PAID_DIFFERENT_AMOUNT -> StatusTone.Warning
    OccurrenceStatus.UPCOMING -> StatusTone.Info
    OccurrenceStatus.OVERDUE -> StatusTone.Negative
}

private fun statusLabel(status: OccurrenceStatus): String = when (status) {
    OccurrenceStatus.PAID -> "Paid"
    OccurrenceStatus.PAID_DIFFERENT_AMOUNT -> "Different amount"
    OccurrenceStatus.UPCOMING -> "Upcoming"
    OccurrenceStatus.OVERDUE -> "Overdue"
}

@Composable
private fun DatePill(date: LocalDate) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(width = 48.dp, height = 52.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OccurrenceListCard(
    occurrences: List<RecurringOccurrence>,
    categories: Map<String, CategoryEntity>,
    onEditItem: (Long) -> Unit
) {
    AppCard(contentPadding = 0.dp) {
        occurrences.forEachIndexed { index: Int, occurrence: RecurringOccurrence ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            OccurrenceRow(
                occurrence = occurrence,
                category = categories[occurrence.categoryKey],
                onClick = { onEditItem(occurrence.itemId) }
            )
        }
    }
}

@Composable
private fun OccurrenceRow(occurrence: RecurringOccurrence, category: CategoryEntity?, onClick: () -> Unit) {
    val amount: Long = occurrence.actualAmountMinor ?: occurrence.expectedAmountMinor
    val subtitle: String = if (occurrence.status == OccurrenceStatus.PAID_DIFFERENT_AMOUNT) {
        "Expected ${CurrencyUtils.formatMinor(occurrence.expectedAmountMinor)}"
    } else {
        category?.name ?: if (occurrence.isIncome) "Income" else "Payment"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit ${occurrence.displayName}", onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DatePill(occurrence.expectedDate)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occurrence.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AmountText(amount, occurrence.isIncome)
            StatusChip(text = statusLabel(occurrence.status), tone = statusTone(occurrence.status))
        }
    }
}

@Composable
private fun AmountText(amountMinor: Long, isIncome: Boolean) {
    Text(
        text = if (isIncome) CurrencyUtils.formatSignedMinor(amountMinor, isPositive = true)
        else CurrencyUtils.formatMinor(amountMinor),
        style = MaterialTheme.typography.titleMedium,
        color = if (isIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CalendarCard(
    state: RecurringUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate?) -> Unit
) {
    val month: YearMonth = state.month
    val byDate: Map<LocalDate, List<RecurringOccurrence>> = state.monthOccurrences.groupBy { it.expectedDate }
    val leading: Int = month.atDay(1).dayOfWeek.value - 1
    val length: Int = month.lengthOfMonth()
    val weeks: Int = (leading + length + 6) / 7

    AppCard(contentPadding = Spacing.md, verticalSpacing = Spacing.xs) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label: String ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val dayNumber: Int = week * 7 + column - leading + 1
                    if (dayNumber in 1..length) {
                        val date: LocalDate = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            statuses = byDate[date].orEmpty().map { it.status }.distinct().take(3),
                            isToday = date == state.today,
                            isSelected = date == state.selectedDay,
                            onClick = { onSelectDay(if (date == state.selectedDay) null else date) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OccurrenceStatus.values().forEach { status: OccurrenceStatus ->
                CountLabel(statusColor(status), statusLabel(status))
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    statuses: List<OccurrenceStatus>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val background: Color =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val outline: Modifier =
        if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(outline)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = date.dayOfMonth.toString(),
                style = if (isToday) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                statuses.forEach { status: OccurrenceStatus ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor(status))
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackedItemCard(
    row: RecurringItemRow,
    category: CategoryEntity?,
    onEdit: () -> Unit,
    onStopTracking: () -> Unit
) {
    var menuOpen: Boolean by remember { mutableStateOf(false) }
    AppCard(onClick = onEdit, contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CategoryEmojiBox(emoji = category?.emoji ?: DEFAULT_EMOJI, colorHex = category?.colorHex)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.frequency.label} · next ${formatDay(row.nextDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AmountText(row.item.amountMinor, row.item.isIncome)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Stop tracking") },
                        leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onStopTracking()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IgnoredItemCard(
    item: RecurringItemEntity,
    category: CategoryEntity?,
    onRestore: () -> Unit,
    onRemove: () -> Unit
) {
    val frequency: RecurringFrequency = RecurringDetector.parseFrequency(item.frequency)
    AppCard(verticalSpacing = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CategoryEmojiBox(emoji = category?.emoji ?: DEFAULT_EMOJI, colorHex = category?.colorHex)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${frequency.label} · ${CurrencyUtils.formatMinor(item.amountMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(text = "Ignored", tone = StatusTone.Neutral)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DestructiveTextButton(text = "Remove", onClick = onRemove)
            TonalButton(text = "Restore", onClick = onRestore)
        }
    }
}

// ---- add / edit sheet -------------------------------------------------------------------

/**
 * Add (editing == null) or edit a recurring payment. [onSubmit] returns an error message to show
 * under the name (e.g. "You already track Netflix"), or null when saved; the caller closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringFormSheet(
    editing: RecurringItemRow?,
    categories: List<CategoryEntity>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (RecurringDraft) -> String?,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formKey: Long = editing?.item?.id ?: 0L
    val isEdit: Boolean = editing != null

    var name: String by rememberSaveable(formKey) { mutableStateOf(editing?.item?.displayName.orEmpty()) }
    var amountText: String by rememberSaveable(formKey) {
        mutableStateOf(editing?.let { CurrencyUtils.minorToDecimalString(it.item.amountMinor) }.orEmpty())
    }
    var isIncome: Boolean by rememberSaveable(formKey) { mutableStateOf(editing?.item?.isIncome ?: false) }
    var frequency: RecurringFrequency by rememberSaveable(formKey) {
        mutableStateOf(editing?.frequency ?: RecurringFrequency.MONTHLY)
    }
    var dateEpochDay: Long by rememberSaveable(formKey) { mutableStateOf((editing?.nextDate ?: today).toEpochDay()) }
    var dateChanged: Boolean by rememberSaveable(formKey) { mutableStateOf(false) }
    var categoryKey: String? by rememberSaveable(formKey) { mutableStateOf(editing?.item?.categoryKey) }
    var nameTouched: Boolean by rememberSaveable(formKey) { mutableStateOf(false) }
    var amountTouched: Boolean by rememberSaveable(formKey) { mutableStateOf(false) }
    var submitError: String? by remember(formKey) { mutableStateOf<String?>(null) }
    var frequencyExpanded: Boolean by remember { mutableStateOf(false) }
    var categoryExpanded: Boolean by remember { mutableStateOf(false) }
    var showDatePicker: Boolean by remember { mutableStateOf(false) }
    var confirmDelete: Boolean by remember { mutableStateOf(false) }

    val parsedAmount: Long? = CurrencyUtils.parseDecimalToMinor(amountText)
    val errors: RecurringFormErrors = ManualRecurring.validate(name, amountText, parsedAmount)
    val wantedType: TransactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
    val categoryOptions: List<CategoryEntity> = categories
        .filter { !it.isHidden && it.type == wantedType }
        .sortedBy { it.name.lowercase() }
    val selectedCategory: CategoryEntity? = categories.firstOrNull { it.key == categoryKey }
    val date: LocalDate = LocalDate.ofEpochDay(dateEpochDay)

    val dateInteraction = remember { MutableInteractionSource() }
    val datePressed: Boolean by dateInteraction.collectIsPressedAsState()
    LaunchedEffect(datePressed) {
        if (datePressed) showDatePicker = true
    }

    val save: () -> Unit = {
        nameTouched = true
        amountTouched = true
        val amount: Long? = parsedAmount
        if (errors.isValid && amount != null) {
            submitError = onSubmit(
                RecurringDraft(
                    name = name,
                    amountMinor = amount,
                    isIncome = isIncome,
                    frequency = frequency,
                    date = if (isEdit && !dateChanged) null else date,
                    categoryKey = categoryKey
                )
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader(
                title = if (isEdit) "Edit recurring payment" else "Add recurring payment",
                subtitle = if (isEdit) null else "Track a bill, subscription or regular income."
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val directions: List<Pair<Boolean, String>> = listOf(false to "Money out", true to "Money in")
                directions.forEachIndexed { index: Int, option: Pair<Boolean, String> ->
                    SegmentedButton(
                        selected = isIncome == option.first,
                        onClick = {
                            if (isIncome != option.first) {
                                isIncome = option.first
                                val wanted: TransactionType =
                                    if (option.first) TransactionType.INCOME else TransactionType.EXPENSE
                                if (selectedCategory != null && selectedCategory.type != wanted) categoryKey = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, directions.size),
                        icon = {}
                    ) { Text(option.second) }
                }
            }

            val nameError: String? = submitError ?: errors.name?.takeIf { nameTouched }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameTouched = true
                    submitError = null
                },
                label = { Text("Name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = when {
                    nameError != null -> {
                        { Text(nameError) }
                    }
                    isEdit -> null
                    else -> {
                        { Text("Use the name on your bank notifications so payments are matched.") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            val amountError: String? = errors.amount?.takeIf { amountTouched }
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountTouched = true
                },
                label = { Text(if (isIncome) "Amount you receive" else "Amount you pay") },
                prefix = { Text("£") },
                singleLine = true,
                isError = amountError != null,
                supportingText = if (amountError != null) {
                    { Text(amountError) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = it }) {
                OutlinedTextField(
                    value = frequency.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeats") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                    RecurringFrequency.values().forEach { option: RecurringFrequency ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                frequency = option
                                frequencyExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())),
                onValueChange = {},
                readOnly = true,
                label = { Text("Next payment date") },
                supportingText = { Text("Any past or upcoming payment date works.") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                    }
                },
                interactionSource = dateInteraction,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "No category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("No category") },
                        onClick = {
                            categoryKey = null
                            categoryExpanded = false
                        }
                    )
                    categoryOptions.forEach { option: CategoryEntity ->
                        DropdownMenuItem(
                            text = { Text("${option.emoji} ${option.name}") },
                            onClick = {
                                categoryKey = option.key
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            SheetActions(
                confirmLabel = if (isEdit) "Save" else "Add",
                onConfirm = save,
                onDismiss = onDismiss,
                confirmEnabled = errors.isValid
            )

            if (onDelete != null) {
                DestructiveTextButton(
                    text = "Delete payment",
                    onClick = { confirmDelete = true },
                    leadingIcon = Icons.Outlined.Delete,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis: Long? = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked: Long = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (picked != dateEpochDay) {
                            dateEpochDay = picked
                            dateChanged = true
                        }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${editing?.item?.displayName ?: "payment"}?") },
            text = { Text("It is removed from your recurring payments. If it keeps repeating it may be suggested again.") },
            confirmButton = {
                DestructiveButton(
                    text = "Delete",
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

// ---- formatting -------------------------------------------------------------------------

private const val DEFAULT_EMOJI: String = "🔁"

private fun formatDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun monthNameFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
