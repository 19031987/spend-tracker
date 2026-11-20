package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing

/**
 * Small "label over value" metric (KPI) tile. Use in a Row with `Modifier.weight(1f)` on
 * each tile and `Arrangement.spacedBy(Spacing.md)` between them.
 *
 * - [label]: `labelMedium`, onSurfaceVariant.
 * - [value]: `titleLarge` (or `titleMedium` when [compact]), [valueColor] (default
 *   onSurface; use `SpendTrackerTheme.finance.income/expense` for money in/out).
 * - [supportingText]: optional `bodySmall` caption. Set [reserveSupportingLine] so tiles
 *   in one row keep equal height when only some have a caption.
 * - [compact] = true: 12dp padding + `titleMedium` value, for 3+ tiles in a row.
 * - [containerColor]: defaults to surfaceContainerLow (a standalone card). When the
 *   tile sits *inside* an [AppCard] pass `MaterialTheme.colorScheme.surface` so it
 *   stands out from the card.
 * - [centered]: centre-align the text (for pill-style KPI rows).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    valueColor: Color = Color.Unspecified,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    compact: Boolean = false,
    centered: Boolean = false,
    reserveSupportingLine: Boolean = false
) {
    val resolvedValueColor: Color =
        if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
    val align: TextAlign = if (centered) TextAlign.Center else TextAlign.Start
    Surface(
        modifier = modifier,
        shape = if (compact) MaterialTheme.shapes.medium else MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) Spacing.md else ScreenPadding.cardPadding),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = resolvedValueColor,
                textAlign = align,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (supportingText != null || reserveSupportingLine) {
                Text(
                    text = supportingText ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
