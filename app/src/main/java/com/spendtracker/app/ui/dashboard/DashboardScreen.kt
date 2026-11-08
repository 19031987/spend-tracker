package com.spendtracker.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.budget.BudgetSummary
import com.spendtracker.app.domain.dashboard.CategoryChange
import com.spendtracker.app.domain.dashboard.MonthComparison
import com.spendtracker.app.domain.dashboard.MonthSummary
import com.spendtracker.app.domain.dashboard.TopCategory
import com.spendtracker.app.domain.goals.GoalProgress
import com.spendtracker.app.domain.goals.GoalStatus
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringOccurrence
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.StatusPill
import com.spendtracker.app.ui.components.TransactionRowItem
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCashFlow: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onCategoryClick: (TransactionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState: DashboardUiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        uiState = uiState,
        onOpenBudget = onOpenBudget,
        onOpenRecurring = onOpenRecurring,
        onOpenReview = onOpenReview,
        onOpenGoals = onOpenGoals,
        onOpenCashFlow = onOpenCashFlow,
        onOpenTransactions = onOpenTransactions,
        onOpenTransaction = onOpenTransaction,
        onCategoryClick = onCategoryClick,
        modifier = modifier
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCashFlow: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onCategoryClick: (TransactionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "greeting") {
            GreetingHeader(today = uiState.today)
        }
        item(key = "month_summary") {
            MonthSummaryHero(summary = uiState.monthSummary, comparison = uiState.comparison, onClick = onOpenCashFlow)
        }
        uiState.comparison?.let { comparison ->
            item(key = "comparison") {
                ComparisonCard(comparison = comparison, onOpen = onOpenCashFlow)
            }
        }
        uiState.budget?.let { budget ->
            item(key = "budget") {
                BudgetCard(budget = budget, onOpen = onOpenBudget)
            }
        }
        if (uiState.reviewCount > 0) {
            item(key = "review") {
                ReviewCard(count = uiState.reviewCount, onOpen = onOpenReview)
            }
        }
        if (uiState.upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                UpcomingCard(items = uiState.upcoming, today = uiState.today, onOpen = onOpenRecurring)
            }
        }
        if (uiState.topCategories.isNotEmpty()) {
            item(key = "top_categories") {
                TopCategoriesCard(categories = uiState.topCategories, onOpen = onOpenCashFlow)
            }
        }
        if (uiState.goals.isNotEmpty()) {
            item(key = "goals") {
                GoalsCard(goals = uiState.goals, onOpen = onOpenGoals)
            }
        }
        item(key = "recent_header") {
            SectionHeaderRow(title = "Recent transactions", actionLabel = "See all", onAction = onOpenTransactions)
        }
        if (uiState.recent.isEmpty()) {
            item(key = "recent_empty") {
                EmptyCard(text = "No transactions yet. Add your first one to see it here.")
            }
        } else {
            item(key = "recent_list") {
                Column(modifier = Modifier.fullBleed(16)) {
                    uiState.recent.forEachIndexed { index, tx ->
                        TransactionRowItem(
                            tx = tx,
                            onClick = { onOpenTransaction(tx) },
                            onCategoryClick = { onCategoryClick(tx) },
                            isFirst = index == 0,
                            isLast = index == uiState.recent.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/** Lets a child that adds its own horizontal padding line up with the list's content padding. */
private fun Modifier.fullBleed(extraHorizontalDp: Int): Modifier = this.layout { measurable, constraints ->
    val extra: Int = (extraHorizontalDp * 2).dp.roundToPx()
    val half: Int = extra / 2
    val width: Int = constraints.maxWidth
    val placeable = measurable.measure(
        constraints.copy(minWidth = width + extra, maxWidth = width + extra)
    )
    layout(width, placeable.height) {
        placeable.place(-half, 0)
    }
}

@Composable
private fun GreetingHeader(today: LocalDate) {
    val hour: Int = remember { java.time.LocalTime.now().hour }
    val greeting: String = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateText: String = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSummaryHero(summary: MonthSummary, comparison: MonthComparison?, onClick: () -> Unit) {
    val monthName: String = summary.month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val net: Long = summary.netRemainingMinor
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spent in $monthName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(isPositive = net >= 0, amountMinor = kotlin.math.abs(net))
            }
            Spacer(Modifier.height(8.dp))
            AutoSizeAmount(
                text = CurrencyUtils.formatMinor(summary.spentMinor),
                style = MaterialTheme.typography.displaySmall
            )
            if (comparison != null && comparison.hasPrevious && comparison.spentDeltaMinor != 0L) {
                Spacer(Modifier.height(8.dp))
                SpendTrendLine(comparison)
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStat(
                    icon = Icons.Default.ArrowDownward,
                    label = "Money in",
                    value = CurrencyUtils.formatMinor(summary.incomeMinor),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    icon = Icons.Default.ArrowUpward,
                    label = "Money out",
                    value = CurrencyUtils.formatMinor(summary.spentMinor),
                    modifier = Modifier.weight(1f)
                )
            }
            if (summary.carriedOverMinor != 0L) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Carried over from earlier months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = CurrencyUtils.formatSignedMinor(summary.carriedOverMinor),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Single-line text that steps its font size down until it fits the available width. */
@Composable
private fun AutoSizeAmount(text: String, style: TextStyle) {
    var scaledStyle: TextStyle by remember(text, style) { mutableStateOf(style) }
    Text(
        text = text,
        style = scaledStyle,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.fillMaxWidth(),
        onTextLayout = { result ->
            if (result.didOverflowWidth && scaledStyle.fontSize.value > 18f) {
                scaledStyle = scaledStyle.copy(fontSize = scaledStyle.fontSize * 0.9f)
            }
        }
    )
}

@Composable
private fun SectionHeaderRow(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Standard dashboard card: header row (title + "See all" chevron) above [content]. */
@Composable
private fun DashboardCard(
    title: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "See all",
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.padding(end = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
    }
}

@Composable
private fun BudgetCard(budget: BudgetSummary, onOpen: () -> Unit) {
    val finance = SpendTrackerTheme.finance
    val left: Long = budget.leftToSpendMinor
    val available: Long = left + budget.totalSpentBudgetedMinor
    val fraction: Float = if (available <= 0L) {
        if (budget.totalSpentBudgetedMinor > 0L) 1f else 0f
    } else {
        (budget.totalSpentBudgetedMinor.toFloat() / available.toFloat()).coerceIn(0f, 1f)
    }
    val isOver: Boolean = left < 0L
    val barColor = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    DashboardCard(title = "Budget", onAction = onOpen) {
        Text(
            text = if (isOver) "Over budget" else "Left to spend",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = CurrencyUtils.formatMinor(kotlin.math.abs(left)),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isOver) finance.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(12.dp))
        ProgressWithMarker(
            progress = fraction,
            marker = budget.monthProgress,
            color = barColor
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${CurrencyUtils.formatMinor(budget.totalSpentBudgetedMinor)} of ${CurrencyUtils.formatMinor(available.coerceAtLeast(0L))} spent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Progress bar with a thin vertical marker (e.g. how far through the month we are). */
@Composable
private fun ProgressWithMarker(progress: Float, marker: Float, color: androidx.compose.ui.graphics.Color) {
    val markerFraction: Float = marker.coerceIn(0.02f, 0.98f)
    Box(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(markerFraction))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            )
            Spacer(Modifier.weight(1f - markerFraction))
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 transaction to review" else "$count transactions to review",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Check categories and amounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Button(onClick = onOpen) {
                Text("Review")
            }
        }
    }
}

@Composable
private fun UpcomingCard(items: List<RecurringOccurrence>, today: LocalDate, onOpen: () -> Unit) {
    DashboardCard(title = "Upcoming", onAction = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { occurrence ->
                UpcomingRow(occurrence = occurrence, today = today)
            }
        }
    }
}

@Composable
private fun UpcomingRow(occurrence: RecurringOccurrence, today: LocalDate) {
    val overdue: Boolean = occurrence.status == OccurrenceStatus.OVERDUE
    val date: LocalDate = occurrence.expectedDate
    val monthAbbrev: String = date.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
    val finance = SpendTrackerTheme.finance
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                Text(text = monthAbbrev, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occurrence.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    overdue -> "Overdue"
                    date == today -> "Due today"
                    date == today.plusDays(1) -> "Due tomorrow"
                    else -> "Due ${date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = CurrencyUtils.formatMinor(occurrence.expectedAmountMinor),
            style = MaterialTheme.typography.titleMedium,
            color = if (occurrence.isIncome) finance.income else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TopCategoriesCard(categories: List<TopCategory>, onOpen: () -> Unit) {
    DashboardCard(title = "Top spending", onAction = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryEmojiBox(emoji = category.emoji, colorHex = category.colorHex, size = 40.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = CurrencyUtils.formatMinor(category.amountMinor),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        LinearProgressIndicator(
                            progress = { category.share.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsCard(goals: List<GoalProgress>, onOpen: () -> Unit) {
    DashboardCard(title = "Goals", onAction = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            goals.forEach { goal ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryEmojiBox(emoji = goal.emoji, colorHex = goal.colorHex, size = 40.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = goal.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (goal.status == GoalStatus.COMPLETE) {
                                    "Complete"
                                } else {
                                    "${(goal.fraction.coerceIn(0f, 1f) * 100).toInt()}%"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        LinearProgressIndicator(
                            progress = { goal.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "${CurrencyUtils.formatMinor(goal.savedMinor)} of ${CurrencyUtils.formatMinor(goal.targetMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Month-over-month comparison
// ---------------------------------------------------------------------------------------------

private fun monthName(month: java.time.YearMonth): String =
    month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())

private fun shortMonthName(month: java.time.YearMonth): String =
    month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())

/** "£120.50 less than this time in October" under the hero amount. */
@Composable
private fun SpendTrendLine(comparison: MonthComparison) {
    val finance = SpendTrackerTheme.finance
    val spentLess: Boolean = comparison.spentDeltaMinor < 0L
    val container = if (spentLess) finance.surplusContainer else finance.deficitContainer
    val content = if (spentLess) finance.onSurplusContainer else finance.onDeficitContainer
    val amount: String = CurrencyUtils.formatMinor(kotlin.math.abs(comparison.spentDeltaMinor))
    val word: String = if (spentLess) "less" else "more"
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (spentLess) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$amount $word than this time in ${monthName(comparison.previousMonth)}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ComparisonCard(comparison: MonthComparison, onOpen: () -> Unit) {
    DashboardCard(title = "Compared with last month", onAction = onOpen, actionLabel = "Details") {
        val current: String = shortMonthName(comparison.month)
        val previous: String = shortMonthName(comparison.previousMonth)
        val days: Int = comparison.dayOfMonth
        Text(
            text = if (days > 0) "1–$days $current vs 1–$days $previous" else "$current vs $previous",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (!comparison.hasPrevious) {
            Text(
                text = "Nothing recorded in ${monthName(comparison.previousMonth)} yet. " +
                    "Comparisons appear once there is a month of history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DashboardCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ComparisonMetricRow(
                label = "Spending",
                currentMinor = comparison.spentMinor,
                previousMinor = comparison.previousSpentMinor,
                deltaMinor = comparison.spentDeltaMinor,
                higherIsGood = false
            )
            ComparisonMetricRow(
                label = "Income",
                currentMinor = comparison.incomeMinor,
                previousMinor = comparison.previousIncomeMinor,
                deltaMinor = comparison.incomeDeltaMinor,
                higherIsGood = true
            )
            ComparisonMetricRow(
                label = "Saved",
                currentMinor = comparison.savedMinor,
                previousMinor = comparison.previousSavedMinor,
                deltaMinor = comparison.savedDeltaMinor,
                higherIsGood = true
            )
        }
        if (comparison.increased.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            ComparisonSectionTitle("Spending more on")
            comparison.increased.forEach { CategoryChangeRow(it) }
        }
        if (comparison.decreased.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            ComparisonSectionTitle("Spending less on")
            comparison.decreased.forEach { CategoryChangeRow(it) }
        }
    }
}

@Composable
private fun ComparisonSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ComparisonMetricRow(
    label: String,
    currentMinor: Long,
    previousMinor: Long,
    deltaMinor: Long,
    higherIsGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${CurrencyUtils.formatMinor(currentMinor)} vs ${CurrencyUtils.formatMinor(previousMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DeltaChip(deltaMinor = deltaMinor, changeFraction = null, higherIsGood = higherIsGood)
    }
}

@Composable
private fun CategoryChangeRow(change: CategoryChange) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryEmojiBox(emoji = change.emoji, colorHex = change.colorHex, size = 36.dp, emojiSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${CurrencyUtils.formatMinor(change.currentMinor)} vs ${CurrencyUtils.formatMinor(change.previousMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DeltaChip(deltaMinor = change.deltaMinor, changeFraction = change.changeFraction, higherIsGood = false)
    }
}

/**
 * Signed change with an arrow. Colour shows whether the move is good for the user:
 * for spending a decrease is good, for income and savings an increase is good.
 */
@Composable
private fun DeltaChip(deltaMinor: Long, changeFraction: Float?, higherIsGood: Boolean) {
    val finance = SpendTrackerTheme.finance
    val isUp: Boolean = deltaMinor > 0L
    val neutral: Boolean = deltaMinor == 0L
    val good: Boolean = if (higherIsGood) isUp else !isUp
    val container = when {
        neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
        good -> finance.surplusContainer
        else -> finance.deficitContainer
    }
    val content = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> finance.onSurplusContainer
        else -> finance.onDeficitContainer
    }
    val amount: String = CurrencyUtils.formatMinor(kotlin.math.abs(deltaMinor))
    val percent: String = changeFraction?.let { " · ${kotlin.math.abs(kotlin.math.round(it * 100f)).toInt()}%" } ?: ""
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!neutral) {
                Icon(
                    imageVector = if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isUp) "Up" else "Down",
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (neutral) "No change" else amount + percent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
