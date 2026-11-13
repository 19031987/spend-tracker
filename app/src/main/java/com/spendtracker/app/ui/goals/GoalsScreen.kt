package com.spendtracker.app.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.domain.goals.GoalProgress
import com.spendtracker.app.domain.goals.GoalStatus
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.DestructiveButton
import com.spendtracker.app.ui.components.DestructiveTextButton
import com.spendtracker.app.ui.components.EmptyState
import com.spendtracker.app.ui.components.PrimaryButton
import com.spendtracker.app.ui.components.SecondaryButton
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetActions
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.components.StatTile
import com.spendtracker.app.ui.components.StatusChip
import com.spendtracker.app.ui.components.StatusTone
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.screenContentPadding
import com.spendtracker.app.ui.util.ColorUtils
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val GoalEmojis: List<String> = listOf(
    "🏠", "✈️", "🚗", "🎓", "💍",
    "💻", "🏖️", "🛡️", "🎁", "🎯"
)

private val GoalColors: List<String> = listOf(
    "#A5B4FC", "#F9A8D4", "#FDBA74", "#86EFAC", "#7DD3FC", "#C4B5FD", "#FCA5A5", "#5EEAD4"
)

private val MonthYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

private fun formatMonthYear(date: LocalDate): String = date.format(MonthYearFormatter)

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun LocalDate.toEpochMillisLocal(): Long =
    this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private data class GoalFormResult(
    val name: String,
    val emoji: String,
    val colorHex: String,
    val targetMinor: Long,
    val targetDate: Long?,
    val startingMinor: Long
)

@Composable
fun GoalsRoute(viewModel: GoalsViewModel, modifier: Modifier = Modifier) {
    val state: GoalsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selected: GoalProgress? = state.selectedGoal

    BackHandler(enabled = selected != null) { viewModel.closeGoal() }

    var showCreate by rememberSaveable { mutableStateOf(false) }

    if (selected == null) {
        GoalListContent(
            state = state,
            onOpen = viewModel::openGoal,
            onNewGoal = { showCreate = true },
            onUnarchive = viewModel::unarchiveGoal,
            onDeleteArchived = viewModel::deleteGoal,
            modifier = modifier
        )
    } else {
        GoalDetailContent(
            goal = selected,
            contributions = state.selectedContributions,
            startingMinor = state.goalEntities.firstOrNull { it.id == selected.goalId }?.startingMinor ?: 0L,
            onBack = viewModel::closeGoal,
            onEdit = { r ->
                viewModel.updateGoal(
                    selected.goalId, r.name, r.emoji, r.colorHex, r.targetMinor, r.targetDate
                )
            },
            onArchive = { viewModel.archiveGoal(selected.goalId) },
            onDelete = { viewModel.deleteGoal(selected.goalId) },
            onAddMoney = { amount, note -> viewModel.addMoney(selected.goalId, amount, note) },
            onWithdraw = { amount, note -> viewModel.withdraw(selected.goalId, amount, note) },
            onDeleteContribution = viewModel::deleteContribution,
            modifier = modifier
        )
    }

    if (showCreate) {
        GoalFormSheet(
            editing = null,
            onDismiss = { showCreate = false },
            onSave = { r ->
                viewModel.createGoal(
                    r.name, r.emoji, r.colorHex, r.targetMinor, r.targetDate, r.startingMinor
                )
                showCreate = false
            }
        )
    }
}

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
private fun GoalListContent(
    state: GoalsUiState,
    onOpen: (Long) -> Unit,
    onNewGoal: () -> Unit,
    onUnarchive: (Long) -> Unit,
    onDeleteArchived: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<GoalProgress?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
    ) {
        if (state.goals.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Save towards something",
                    body = "Set a target, add money as you go and see whether you are on track.",
                    emoji = "🎯",
                    actionLabel = "New goal",
                    onAction = onNewGoal
                )
            }
        } else {
            item(key = "header") {
                SectionHeader(
                    title = "Active",
                    subtitle = "${CurrencyUtils.formatMinor(state.totalSavedMinor)} saved across " +
                        "${state.goals.size} ${if (state.goals.size == 1) "goal" else "goals"}",
                    actionLabel = "New goal",
                    actionIcon = Icons.Filled.Add,
                    actionIconLeading = true,
                    onAction = onNewGoal
                )
            }
            items(state.goals, key = { it.goalId }) { goal ->
                GoalCard(goal = goal, onClick = { onOpen(goal.goalId) })
            }
        }

        if (state.archivedGoals.isNotEmpty()) {
            item(key = "archived_header") {
                SectionHeader(
                    title = "Archived (${state.archivedGoals.size})",
                    actionLabel = if (showArchived) "Hide" else "Show",
                    actionIcon = if (showArchived) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    onAction = { showArchived = !showArchived }
                )
            }
            if (showArchived) {
                items(state.archivedGoals, key = { "a${it.goalId}" }) { goal ->
                    ArchivedGoalCard(
                        goal = goal,
                        onUnarchive = { onUnarchive(goal.goalId) },
                        onDelete = { confirmDelete = goal }
                    )
                }
            }
        }
    }

    val pendingDelete: GoalProgress? = confirmDelete
    if (pendingDelete != null) {
        DeleteGoalDialog(
            goalName = pendingDelete.name,
            onConfirm = {
                confirmDelete = null
                onDeleteArchived(pendingDelete.goalId)
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun ArchivedGoalCard(goal: GoalProgress, onUnarchive: () -> Unit, onDelete: () -> Unit) {
    val accent: Color = ColorUtils.parseColorHex(goal.colorHex)
    AppCard(verticalSpacing = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(emoji = goal.emoji, color = accent, size = 40)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${CurrencyUtils.formatMinor(goal.savedMinor)} of " +
                        CurrencyUtils.formatMinor(goal.targetMinor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DestructiveTextButton(
                text = "Delete",
                onClick = onDelete,
                leadingIcon = Icons.Outlined.Delete
            )
            TonalButton(text = "Unarchive", onClick = onUnarchive)
        }
    }
}

@Composable
private fun DeleteGoalDialog(goalName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete goal?") },
        text = { Text("\"$goalName\" and its contribution history will be permanently deleted.") },
        confirmButton = { DestructiveButton(text = "Delete", onClick = onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoalCard(goal: GoalProgress, onClick: () -> Unit) {
    val accent: Color = ColorUtils.parseColorHex(goal.colorHex)
    AppCard(onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(emoji = goal.emoji, color = accent, size = 44)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${CurrencyUtils.formatMinor(goal.savedMinor)} of " +
                            CurrencyUtils.formatMinor(goal.targetMinor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                GoalStatusChip(status = goal.status)
            }
            LinearProgressIndicator(
                progress = { goal.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = accent.copy(alpha = 0.22f)
            )
            val subtitle: String? = needText(goal)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun needText(goal: GoalProgress): String? {
    val needed: Long? = goal.monthlyNeededMinor
    val date: LocalDate? = goal.targetDate
    return when {
        goal.status == GoalStatus.COMPLETE -> "Goal reached"
        needed != null && date != null ->
            "${CurrencyUtils.formatMinor(needed)}/month needed by ${formatMonthYear(date)}"
        else -> "${CurrencyUtils.formatMinor(goal.remainingMinor)} to go"
    }
}

@Composable
private fun EmojiBadge(emoji: String, color: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GoalStatusChip(status: GoalStatus) {
    val (label: String, tone: StatusTone) = when (status) {
        GoalStatus.COMPLETE -> "Complete" to StatusTone.Positive
        GoalStatus.ON_TRACK -> "On track" to StatusTone.Positive
        GoalStatus.BEHIND -> "Behind" to StatusTone.Negative
        GoalStatus.NO_TARGET_DATE -> "No date" to StatusTone.Neutral
    }
    StatusChip(text = label, tone = tone)
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@Composable
private fun GoalDetailContent(
    goal: GoalProgress,
    contributions: List<GoalContributionEntity>,
    startingMinor: Long,
    onBack: () -> Unit,
    onEdit: (GoalFormResult) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddMoney: (Long, String?) -> Unit,
    onWithdraw: (Long, String?) -> Unit,
    onDeleteContribution: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent: Color = ColorUtils.parseColorHex(goal.colorHex)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var moneySheet by rememberSaveable { mutableStateOf<String?>(null) } // "add" | "withdraw"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuOpen = false
                                showEdit = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                menuOpen = false
                                onArchive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                showDelete = true
                            }
                        )
                    }
                }
            }
        }

        item(key = "ring") {
            AppCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    ProgressRing(goal = goal, accent = accent)
                    GoalStatusChip(status = goal.status)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SecondaryButton(
                            text = "Withdraw",
                            onClick = { moneySheet = "withdraw" },
                            modifier = Modifier.weight(1f),
                            enabled = goal.savedMinor > 0L
                        )
                        PrimaryButton(
                            text = "Add money",
                            onClick = { moneySheet = "add" },
                            leadingIcon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = "Saved",
                        value = CurrencyUtils.formatMinor(goal.savedMinor),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Remaining",
                        value = CurrencyUtils.formatMinor(goal.remainingMinor),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    val needed: Long? = goal.monthlyNeededMinor
                    val date: LocalDate? = goal.targetDate
                    StatTile(
                        label = "Monthly needed",
                        value = if (needed != null) CurrencyUtils.formatMinor(needed) else "—",
                        supportingText = if (date != null) "by ${formatMonthYear(date)}" else "No target date",
                        reserveSupportingLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    val projected: LocalDate? = goal.projectedCompletion
                    StatTile(
                        label = "Projected",
                        value = if (projected != null) formatMonthYear(projected) else "—",
                        supportingText = "Avg ${CurrencyUtils.formatMinor(goal.averageMonthlyContributionMinor)}/month",
                        reserveSupportingLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "history_title") {
            SectionHeader(title = "History")
        }

        if (contributions.isEmpty()) {
            item(key = "history_empty") {
                EmptyState(
                    body = if (startingMinor > 0L) {
                        "No contributions yet. You started with ${CurrencyUtils.formatMinor(startingMinor)}."
                    } else {
                        "No contributions yet."
                    }
                )
            }
        } else {
            items(contributions, key = { "c${it.id}" }) { c ->
                ContributionRow(contribution = c, onDelete = { onDeleteContribution(c.id) })
            }
        }
    }

    if (showEdit) {
        GoalFormSheet(
            editing = goal,
            editingStartingMinor = startingMinor,
            onDismiss = { showEdit = false },
            onSave = { r ->
                onEdit(r)
                showEdit = false
            }
        )
    }

    if (showDelete) {
        DeleteGoalDialog(
            goalName = goal.name,
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false }
        )
    }

    val sheetMode: String? = moneySheet
    if (sheetMode != null) {
        val isWithdraw: Boolean = sheetMode == "withdraw"
        MoneySheet(
            title = if (isWithdraw) "Withdraw from ${goal.name}" else "Add to ${goal.name}",
            confirmLabel = if (isWithdraw) "Withdraw" else "Add money",
            maxMinor = if (isWithdraw) goal.savedMinor else null,
            onDismiss = { moneySheet = null },
            onConfirm = { amount, note ->
                if (isWithdraw) onWithdraw(amount, note) else onAddMoney(amount, note)
                moneySheet = null
            }
        )
    }
}

@Composable
private fun ProgressRing(goal: GoalProgress, accent: Color) {
    val track: Color = accent.copy(alpha = 0.22f)
    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth: Float = 18.dp.toPx()
            val inset: Float = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (goal.fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * goal.fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = goal.emoji, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${(goal.fraction * 100f).toInt()}%",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "of ${CurrencyUtils.formatMinor(goal.targetMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContributionRow(contribution: GoalContributionEntity, onDelete: () -> Unit) {
    val finance = SpendTrackerTheme.finance
    val isWithdrawal: Boolean = contribution.amountMinor < 0L
    val date: LocalDate = Instant.ofEpochMilli(contribution.timestamp)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    AppCard(contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWithdrawal) "Withdrawal" else "Deposit",
                    style = MaterialTheme.typography.bodyLarge
                )
                val note: String = contribution.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Text(
                    text = formatShortDate(date) + note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = CurrencyUtils.formatSignedMinor(contribution.amountMinor),
                style = MaterialTheme.typography.titleSmall,
                color = if (isWithdrawal) finance.expense else finance.income
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySheet(
    title: String,
    confirmLabel: String,
    maxMinor: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader(title)
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    error = null
                },
                label = { Text("Amount") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    val msg: String? = error
                    if (msg != null) Text(msg)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                confirmLabel = confirmLabel,
                onConfirm = {
                    val minor: Long? = CurrencyUtils.parseDecimalToMinor(amountText)
                    when {
                        minor == null -> error = "Enter a valid amount"
                        maxMinor != null && minor > maxMinor ->
                            error = "Only ${CurrencyUtils.formatMinor(maxMinor)} saved"
                        else -> onConfirm(minor, note.ifBlank { null })
                    }
                },
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFormSheet(
    editing: GoalProgress?,
    onDismiss: () -> Unit,
    onSave: (GoalFormResult) -> Unit,
    editingStartingMinor: Long = 0L
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit: Boolean = editing != null

    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(editing?.emoji ?: GoalEmojis.first()) }
    var colorHex by rememberSaveable { mutableStateOf(editing?.colorHex ?: GoalColors.first()) }
    var targetText by rememberSaveable {
        mutableStateOf(editing?.let { CurrencyUtils.minorToDecimalString(it.targetMinor) } ?: "")
    }
    var startingText by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf<Long?>(editing?.targetDate?.toEpochDay()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedDate: LocalDate? = dateEpochDay?.let { LocalDate.ofEpochDay(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetContainer {
            SheetHeader(if (isEdit) "Edit goal" else "New goal")

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "Icon", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(GoalEmojis) { e ->
                    val chosen: Boolean = e == emoji
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (chosen) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = e, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Text(text = "Colour", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(GoalColors) { hex ->
                    val chosen: Boolean = hex.equals(colorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ColorUtils.parseColorHex(hex))
                            .then(
                                if (chosen) Modifier.border(
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }

            OutlinedTextField(
                value = targetText,
                onValueChange = {
                    targetText = it
                    error = null
                },
                label = { Text("Target amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = if (selectedDate != null) "Target: ${formatShortDate(selectedDate)}"
                        else "Target date (optional)"
                    )
                }
                if (selectedDate != null) {
                    IconButton(onClick = { dateEpochDay = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear target date")
                    }
                }
            }

            if (!isEdit) {
                OutlinedTextField(
                    value = startingText,
                    onValueChange = {
                        startingText = it
                        error = null
                    },
                    label = { Text("Already saved (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val message: String? = error
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            SheetActions(
                confirmLabel = if (isEdit) "Save" else "Add goal",
                onDismiss = onDismiss,
                onConfirm = {
                    val target: Long? = CurrencyUtils.parseDecimalToMinor(targetText)
                    val starting: Long? = if (isEdit) {
                        editingStartingMinor
                    } else if (startingText.isBlank()) {
                        0L
                    } else {
                        CurrencyUtils.parseDecimalToMinor(startingText)
                    }
                    when {
                        name.isBlank() -> error = "Give your goal a name"
                        target == null -> error = "Enter a valid target amount"
                        starting == null -> error = "Enter a valid amount already saved"
                        else -> onSave(
                            GoalFormResult(
                                name = name.trim(),
                                emoji = emoji,
                                colorHex = colorHex,
                                targetMinor = target,
                                targetDate = selectedDate?.toEpochMillisLocal(),
                                startingMinor = starting
                            )
                        )
                    }
                }
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (selectedDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis: Long? = pickerState.selectedDateMillis
                    if (millis != null) {
                        dateEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
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
}
