package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.theme.Spacing

/**
 * Placeholder card shown when a list/section has nothing to show. Wrapped in [AppCard].
 *
 * Give an [icon] (tinted `primary`, 32dp) OR an [emoji] (displaySmall); if both are set
 * the icon wins. [title] is optional so a one-line hint ("No transactions this month")
 * can be shown with [body] only. When [actionLabel] and [onAction] are both set a
 * [PrimaryButton] is shown; [actionIcon] defaults to the Add icon (pass null to hide it).
 */
@Composable
fun EmptyState(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = Icons.Filled.Add,
    onAction: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, contentPadding = Spacing.xl) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                emoji != null -> Text(text = emoji, style = MaterialTheme.typography.displaySmall)
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    leadingIcon = actionIcon
                )
            }
        }
    }
}
