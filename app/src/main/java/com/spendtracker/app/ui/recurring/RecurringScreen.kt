package com.spendtracker.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringCandidate
import com.spendtracker.app.domain.recurring.RecurringFrequency
import com.spendtracker.app.domain.recurring.RecurringOccurrence
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
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
        onUpdateItem = viewModel::updateItem,
        modifier = modifier
    )
}

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
    onUpdateItem: (Long, Long, RecurringFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing: RecurringItemRow? by remember { mutableStateOf<RecurringItemRow?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            RecurringView.UPCOMING -> upcomingSection(uiState)
            RecurringView.CALENDAR -> calendarSection(uiState, onPreviousMonth, onNextMonth, onSelectDay)
            RecurringView.ALL -> allSection(
                state = uiState,
                onEdit = { editing = it },
                onStopTracking = onStopTracking
            )
        }
    }

    val current: RecurringItemRow? = editing
    if (current != null) {
        EditRecurringSheet(
            row = current,
            onDismiss = { editing = null },
            onSave = { amount: Long, frequency: RecurringFrequency ->
                onUpdateItem(current.item.id, amount, frequency)
                editing = null
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Remaining due in ${summary.month.format(monthNameFormatter())}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyUtils.formatMinor(summary.remainingDueMinor),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CountLabel(SpendTrackerTheme.finance.income, "${summary.paidCount} paid")
                CountLabel(MaterialTheme.colorScheme.secondary, "${summary.upcomingCount} upcoming")
                if (summary.overdueCount > 0) {
                    CountLabel(MaterialTheme.colorScheme.error, "${summary.overdueCount} overdue")
                }
            }
        }
    }
}

@Composable
private fun CountLabel(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
            val noun: String = if (suggestions.size == 1) "payment" else "payments"
            Text(
                text = "We found ${suggestions.size} possible recurring $noun",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Approve the ones you want to keep an eye on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryEmojiBox(emoji = category?.emoji ?: "🔁", colorHex = category?.colorHex)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onIgnore(candidate) }) { Text("Ignore") }
                FilledTonalButton(onClick = { onApprove(candidate) }) { Text("Approve") }
            }
        }
    }
}

private fun LazyListScope.upcomingSection(state: RecurringUiState) {
    if (state.upcoming.isEmpty()) {
        item(key = "upcoming-empty") {
            if (state.items.isEmpty()) {
                EmptyState(
                    title = "No recurring payments yet",
                    body = if (state.suggestions.isEmpty()) {
                        "When a payment repeats, like a subscription or rent, we will spot it and ask if you want to track it."
                    } else {
                        "Approve one of the suggestions above and its due dates will show up here."
                    }
                )
            } else {
                EmptyState(
                    title = "Nothing due soon",
                    body = "None of your recurring payments are due in the next 30 days."
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(group.first)
                OccurrenceListCard(group.second, state.categories)
            }
        }
    }
}

private fun LazyListScope.calendarSection(
    state: RecurringUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate?) -> Unit
) {
    item(key = "calendar") {
        CalendarCard(state, onPreviousMonth, onNextMonth, onSelectDay)
    }

    val selected: LocalDate? = state.selectedDay
    val shown: List<RecurringOccurrence> =
        if (selected == null) state.monthOccurrences
        else state.monthOccurrences.filter { it.expectedDate == selected }

    item(key = "calendar-list") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(
                if (selected == null) "All payments in ${state.month.format(monthNameFormatter())}"
                else selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            )
            if (shown.isEmpty()) {
                EmptyState(
                    title = if (selected == null) "Nothing expected this month" else "Nothing due on this day",
                    body = "Tap another day to see what is coming up."
                )
            } else {
                OccurrenceListCard(shown, state.categories)
            }
        }
    }
}

private fun LazyListScope.allSection(
    state: RecurringUiState,
    onEdit: (RecurringItemRow) -> Unit,
    onStopTracking: (Long) -> Unit
) {
    if (state.items.isEmpty()) {
        item(key = "all-empty") {
            EmptyState(
                title = "Nothing tracked yet",
                body = "Approved recurring payments are listed here, along with their frequency and next date."
            )
        }
        return
    }
    items(state.items, key = { "item-${it.item.id}" }) { row: RecurringItemRow ->
        TrackedItemCard(
            row = row,
            category = state.categories[row.item.categoryKey],
            onEdit = { onEdit(row) },
            onStopTracking = { onStopTracking(row.item.id) }
        )
    }
}

// ---- building blocks --------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun statusColor(status: OccurrenceStatus): Color = when (status) {
    OccurrenceStatus.PAID -> SpendTrackerTheme.finance.income
    OccurrenceStatus.PAID_DIFFERENT_AMOUNT -> MaterialTheme.colorScheme.tertiary
    OccurrenceStatus.UPCOMING -> MaterialTheme.colorScheme.secondary
    OccurrenceStatus.OVERDUE -> MaterialTheme.colorScheme.error
}

private fun statusLabel(status: OccurrenceStatus): String = when (status) {
    OccurrenceStatus.PAID -> "Paid"
    OccurrenceStatus.PAID_DIFFERENT_AMOUNT -> "Different amount"
    OccurrenceStatus.UPCOMING -> "Upcoming"
    OccurrenceStatus.OVERDUE -> "Overdue"
}

@Composable
private fun StatusChip(status: OccurrenceStatus) {
    val color: Color = statusColor(status)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
private fun OccurrenceListCard(occurrences: List<RecurringOccurrence>, categories: Map<String, CategoryEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            occurrences.forEachIndexed { index: Int, occurrence: RecurringOccurrence ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                OccurrenceRow(occurrence, categories[occurrence.categoryKey])
            }
        }
    }
}

@Composable
private fun OccurrenceRow(occurrence: RecurringOccurrence, category: CategoryEntity?) {
    val amount: Long = occurrence.actualAmountMinor ?: occurrence.expectedAmountMinor
    val subtitle: String = if (occurrence.status == OccurrenceStatus.PAID_DIFFERENT_AMOUNT) {
        "Expected ${CurrencyUtils.formatMinor(occurrence.expectedAmountMinor)}"
    } else {
        category?.name ?: if (occurrence.isIncome) "Income" else "Payment"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (occurrence.isIncome) CurrencyUtils.formatSignedMinor(amount, isPositive = true)
                else CurrencyUtils.formatMinor(amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (occurrence.isIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.onSurface
            )
            StatusChip(occurrence.status)
        }
    }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OccurrenceStatus.values().forEach { status: OccurrenceStatus ->
                    CountLabel(statusColor(status), statusLabel(status))
                }
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryEmojiBox(emoji = category?.emoji ?: "🔁", colorHex = category?.colorHex)
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
            Text(
                text = if (row.item.isIncome) CurrencyUtils.formatSignedMinor(row.item.amountMinor, isPositive = true)
                else CurrencyUtils.formatMinor(row.item.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = if (row.item.isIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.onSurface
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecurringSheet(
    row: RecurringItemRow,
    onDismiss: () -> Unit,
    onSave: (Long, RecurringFrequency) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText: String by remember(row.item.id) {
        mutableStateOf(CurrencyUtils.minorToDecimalString(row.item.amountMinor))
    }
    var frequency: RecurringFrequency by remember(row.item.id) { mutableStateOf(row.frequency) }
    var expanded: Boolean by remember { mutableStateOf(false) }
    val parsed: Long? = CurrencyUtils.parseDecimalToMinor(amountText)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Edit ${row.item.displayName}", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Typical amount") },
                prefix = { Text("£") },
                singleLine = true,
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = frequency.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeats") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RecurringFrequency.values().forEach { option: RecurringFrequency ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                frequency = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { if (parsed != null) onSave(parsed, frequency) },
                    enabled = parsed != null
                ) { Text("Save") }
            }
        }
    }
}

// ---- formatting -------------------------------------------------------------------------

private fun formatDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun monthNameFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
