package com.spendtracker.app.ui.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.CategoryPill
import com.spendtracker.app.ui.components.EmptyState
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.screenContentPadding
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils

@Composable
fun ReviewRoute(
    viewModel: ReviewViewModel,
    onChangeCategory: (TransactionItem) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewScreen(
        state = state,
        onMarkReviewed = viewModel::markReviewed,
        onUndo = viewModel::undoLast,
        onMarkAllReviewed = viewModel::markAllReviewed,
        onChangeCategory = onChangeCategory,
        onOpenTransaction = onOpenTransaction,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onMarkReviewed: (TransactionItem) -> Unit,
    onUndo: () -> Unit,
    onMarkAllReviewed: () -> Unit,
    onChangeCategory: (TransactionItem) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val showUndo = state.lastReviewedId != null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
    ) {
        if (showUndo) {
            item(key = "undo") {
                UndoCard(onUndo = onUndo, modifier = Modifier.animateItemPlacement())
            }
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "All caught up",
                    body = "New transactions captured from bank notifications will show up here for a quick check.",
                    icon = Icons.Default.Check,
                    modifier = Modifier.animateItemPlacement()
                )
            }
        } else {
            item(key = "header") {
                HeaderCard(
                    count = state.count,
                    onMarkAll = onMarkAllReviewed,
                    modifier = Modifier.animateItemPlacement()
                )
            }
            items(state.items, key = { it.id }) { tx ->
                ReviewCard(
                    tx = tx,
                    onMarkReviewed = { onMarkReviewed(tx) },
                    onChangeCategory = { onChangeCategory(tx) },
                    onOpen = { onOpenTransaction(tx) },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(count: Int, onMarkAll: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    AppCard(modifier = modifier, verticalSpacing = Spacing.xs) {
        Text(
            text = "$count to review",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Transactions captured from bank notifications. Check the merchant and category, then mark them reviewed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.End
        ) {
            TonalButton(
                text = "Mark all reviewed",
                onClick = { confirm = true },
                leadingIcon = Icons.Default.Check
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mark all reviewed?") },
            text = { Text("All $count transactions will be marked as reviewed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onMarkAll()
                }) { Text("Mark all") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UndoCard(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    // Intentionally highlighted: transient confirmation with an Undo action.
    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Marked reviewed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo) { Text("Undo") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(
    tx: TransactionItem,
    onMarkReviewed: () -> Unit,
    onChangeCategory: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onMarkReviewed()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Reviewed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) {
        val isIncome = tx.type == TransactionType.INCOME
        val isUnknown = tx.merchant.isNullOrBlank()
        AppCard(onClick = onOpen, contentPadding = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CategoryEmojiBox(emoji = tx.categoryEmoji, colorHex = tx.categoryColorHex)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = if (isUnknown) "Unknown vendor" else tx.merchant.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyUtils.formatSignedMinor(tx.amountMinor, isPositive = isIncome),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val whenText = if (tx.timestamp > 0) {
                        "${DateUtils.formatDateShort(tx.timestamp)}, ${DateUtils.formatTime(tx.timestamp)}"
                    } else {
                        "—"
                    }
                    Text(
                        text = "$whenText · ${tx.accountName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CategoryPill(
                        categoryName = tx.categoryName,
                        emoji = tx.categoryEmoji,
                        onClick = onChangeCategory
                    )
                }
                IconButton(onClick = onMarkReviewed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Mark reviewed",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
