package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.theme.Spacing

/**
 * Header above a group of cards on a screen (or inside a card).
 *
 * - Default: `titleMedium`, `onSurface` — use for screen sections ("Budgets", "Recent").
 * - `small = true`: `titleSmall`, `onSurfaceVariant` — use for group labels inside a
 *   section ("Ignored", "Essentials", "This week").
 *
 * The text is indented 4dp so it lines up with card content. The optional trailing
 * action is a TextButton: "See all" + `Icons.Default.ChevronRight` (trailing), or for add-new
 * "Add" + `Icons.Filled.Add` with `actionIconLeading = true`.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    small: Boolean = false,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionIconLeading: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (actionLabel != null && onAction != null) 48.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.xs)
        ) {
            Text(
                text = title,
                style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                if (actionIcon != null && actionIconLeading) {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(actionLabel)
                if (actionIcon != null && !actionIconLeading) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
