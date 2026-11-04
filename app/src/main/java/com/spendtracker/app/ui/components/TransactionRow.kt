package com.spendtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.ui.util.ColorUtils
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

/**
 * Standard 2-row fixed transaction item row with category box, merchant, bank badge,
 * category pill, signed amount, and time.
 */
@Composable
fun TransactionRowItem(
    tx: TransactionItem,
    onClick: () -> Unit,
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnknownVendor = tx.merchant.isNullOrBlank() ||
        tx.merchant.equals("Chase Payment", ignoreCase = true) ||
        tx.merchant.equals("HSBC Payment", ignoreCase = true) ||
        tx.merchant.equals("Money In", ignoreCase = true) ||
        tx.merchant.equals("Money Out", ignoreCase = true) ||
        tx.merchant.equals("Card Payment", ignoreCase = true) ||
        tx.merchant.equals("Payment", ignoreCase = true) ||
        tx.merchant.equals("Unknown", ignoreCase = true) ||
        tx.merchant.equals("Unknown Vendor", ignoreCase = true) ||
        tx.merchant.equals("Transaction", ignoreCase = true)

    val timeStr = if (tx.timestamp > 0) {
        DateUtils.formatTime(tx.timestamp)
    } else "—"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Emoji Box (uniform 42dp)
            CategoryEmojiBox(
                emoji = tx.categoryEmoji,
                colorHex = tx.categoryColorHex
            )

            // Transaction Details (fixed 2-row grid: 22dp + 6dp + 22dp = 50dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Merchant or Unknown Vendor (fixed 22dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isUnknownVendor) {
                        Text(
                            text = "Unknown Vendor",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onClick() }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "✏️ Set",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Text(
                            text = tx.merchant?.takeIf { it.isNotEmpty() } ?: "Transaction",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Row 2: Source badge + Category pill (fixed 22dp single-line row, never wraps)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source badge (Chase / HSBC / Monzo)
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    tx.source.contains("Chase", ignoreCase = true) -> Color(0xFFDBEAFE)
                                    tx.source.contains("HSBC", ignoreCase = true) -> Color(0xFFFEE2E2)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tx.source,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = when {
                                tx.source.contains("Chase", ignoreCase = true) -> Color(0xFF1E40AF)
                                tx.source.contains("HSBC", ignoreCase = true) -> Color(0xFF991B1B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Tappable Category pill
                    CategoryPill(
                        categoryName = tx.categoryName,
                        emoji = tx.categoryEmoji,
                        modifier = Modifier.weight(1f, fill = false),
                        onClick = onCategoryClick
                    )
                }
            }

            // Amount and Timestamp column (fixed 2-row grid: 22dp + 6dp + 22dp = 50dp, aligned to end)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.height(22.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val isIncome = tx.type == TransactionType.INCOME
                    Text(
                        text = CurrencyUtils.formatSignedMinor(tx.amountMinor, isPositive = isIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isIncome) ColorUtils.InflowGreen else ColorUtils.OutflowRed
                    )
                }

                Box(
                    modifier = Modifier.height(22.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
