package com.spendtracker.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayGroup(
    val dayKey: String,
    val dayTitle: String,
    val daySpendTotal: Long,
    val items: List<TransactionItem>
)

fun groupTransactionsByDay(transactions: List<TransactionItem>): List<DayGroup> {
    if (transactions.isEmpty()) return emptyList()

    val calNow = Calendar.getInstance()
    val todayYear = calNow.get(Calendar.YEAR)
    val todayDayOfYear = calNow.get(Calendar.DAY_OF_YEAR)

    calNow.add(Calendar.DAY_OF_YEAR, -1)
    val yestYear = calNow.get(Calendar.YEAR)
    val yestDayOfYear = calNow.get(Calendar.DAY_OF_YEAR)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val headerDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

    val grouped = linkedMapOf<String, MutableList<TransactionItem>>()
    for (tx in transactions) {
        val key = if (tx.timestamp > 0) dateFormat.format(Date(tx.timestamp)) else "unknown"
        grouped.getOrPut(key) { mutableListOf() }.add(tx)
    }

    return grouped.map { (key, items) ->
        val firstTimestamp = items.firstOrNull()?.timestamp ?: 0L
        val dayTitle = if (firstTimestamp > 0) {
            val calTx = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
            val txYear = calTx.get(Calendar.YEAR)
            val txDayOfYear = calTx.get(Calendar.DAY_OF_YEAR)
            when {
                txYear == todayYear && txDayOfYear == todayDayOfYear -> "Today · ${headerDateFormat.format(Date(firstTimestamp))}"
                txYear == yestYear && txDayOfYear == yestDayOfYear -> "Yesterday · ${headerDateFormat.format(Date(firstTimestamp))}"
                else -> fullDateFormat.format(Date(firstTimestamp))
            }
        } else {
            "Other Transactions"
        }

        val daySpend = items
            .filter { it.type == TransactionType.EXPENSE && !it.excludeFromSpending && it.categoryKey != "INTERNAL_TRANSFER" }
            .sumOf { it.amountMinor }

        DayGroup(
            dayKey = key,
            dayTitle = dayTitle,
            daySpendTotal = daySpend,
            items = items
        )
    }
}

private val GENERIC_MERCHANTS = setOf(
    "chase payment", "hsbc payment", "money in", "money out", "card payment",
    "payment", "unknown", "unknown vendor", "transaction"
)

/**
 * Material list-style transaction row. Rows of the same day are stacked into one
 * rounded group: pass [isFirst]/[isLast] so only the outer corners are rounded
 * and a hairline divider separates the rows inside the group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionRowItem(
    tx: TransactionItem,
    onClick: () -> Unit,
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirst: Boolean = true,
    isLast: Boolean = true
) {
    val isUnknownVendor = tx.merchant.isNullOrBlank() ||
        tx.merchant.trim().lowercase(Locale.ROOT) in GENERIC_MERCHANTS

    val timeStr = if (tx.timestamp > 0) DateUtils.formatTime(tx.timestamp) else "—"
    val corner = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 4.dp,
        topEnd = if (isFirst) corner else 4.dp,
        bottomStart = if (isLast) corner else 4.dp,
        bottomEnd = if (isLast) corner else 4.dp
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = if (isLast) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CategoryEmojiBox(emoji = tx.categoryEmoji, colorHex = tx.categoryColorHex)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isUnknownVendor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Text(
                            text = "Unknown vendor",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Set vendor",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = tx.merchant?.takeIf { it.isNotEmpty() } ?: "Transaction",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPill(
                        categoryName = tx.categoryName,
                        emoji = tx.categoryEmoji,
                        modifier = Modifier.weight(1f, fill = false),
                        onClick = onCategoryClick
                    )
                    Text(
                        text = tx.source,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isIncome = tx.type == TransactionType.INCOME
                val finance = SpendTrackerTheme.finance
                Text(
                    text = CurrencyUtils.formatSignedMinor(tx.amountMinor, isPositive = isIncome),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isIncome) finance.income else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
