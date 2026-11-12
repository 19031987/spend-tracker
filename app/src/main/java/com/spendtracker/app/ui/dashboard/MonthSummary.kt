package com.spendtracker.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.StatusPill
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.util.CurrencyUtils

/**
 * Hero card shared by Home (Dashboard) and Transactions: this month's spend as the
 * headline figure, money in / out tiles and the carry-over from earlier months.
 *
 * @param netMinor running balance shown in the status pill (sign picks the colour).
 * @param trend optional line under the headline (e.g. month-over-month comparison).
 * @param onClick makes the whole card clickable when non-null.
 */
@Composable
internal fun MonthSummaryHeroCard(
    monthName: String,
    spentMinor: Long,
    incomeMinor: Long,
    netMinor: Long,
    carriedOverMinor: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trend: (@Composable () -> Unit)? = null
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
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
            Spacer(Modifier.width(Spacing.sm))
            StatusPill(isPositive = netMinor >= 0, amountMinor = kotlin.math.abs(netMinor))
        }
        Spacer(Modifier.height(Spacing.sm))
        // Full-width, single line, auto-shrinking so large amounts never truncate.
        AutoSizeAmount(
            text = CurrencyUtils.formatMinor(spentMinor),
            style = MaterialTheme.typography.displaySmall
        )
        if (trend != null) {
            Spacer(Modifier.height(Spacing.sm))
            trend()
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SummaryStat(
                icon = Icons.Default.ArrowDownward,
                label = "Money in",
                value = CurrencyUtils.formatMinor(incomeMinor),
                modifier = Modifier.weight(1f)
            )
            SummaryStat(
                icon = Icons.Default.ArrowUpward,
                label = "Money out",
                value = CurrencyUtils.formatMinor(spentMinor),
                modifier = Modifier.weight(1f)
            )
        }
        if (carriedOverMinor != 0L) {
            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            Spacer(Modifier.height(Spacing.md))
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
                    text = CurrencyUtils.formatSignedMinor(carriedOverMinor),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
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
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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

/** Single-line text that steps its (theme) font size down until it fits the available width. */
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
