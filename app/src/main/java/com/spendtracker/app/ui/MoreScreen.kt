package com.spendtracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.theme.Spacing
import com.spendtracker.app.ui.theme.screenContentPadding

/** Secondary destinations reachable from the "More" tab. */
enum class MoreDestination(val title: String, val subtitle: String, val icon: ImageVector) {
    RECURRING("Recurring", "Bills and subscriptions, upcoming payments", Icons.Outlined.EventRepeat),
    GOALS("Goals", "Save towards the things that matter", Icons.Outlined.Flag),
    REVIEW("Review", "Check transactions captured from alerts", Icons.Outlined.TaskAlt),
    INSIGHTS("Insights", "Spending trends, KPIs and top categories", Icons.Outlined.Insights),
    COMPARE("Compare", "This period against the last one", Icons.Outlined.CompareArrows)
}

@Composable
fun MoreScreen(
    reviewCount: Int,
    onOpen: (MoreDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = screenContentPadding(),
        // Rows form one connected group, so they sit 2dp apart (not listItemSpacing).
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val entries = MoreDestination.values().toList()
        items(entries.size) { index ->
            val dest = entries[index]
            MoreRow(
                icon = dest.icon,
                title = dest.title,
                subtitle = dest.subtitle,
                badge = if (dest == MoreDestination.REVIEW && reviewCount > 0) reviewCount else null,
                isFirst = index == 0,
                isLast = index == entries.lastIndex,
                onClick = { onOpen(dest) }
            )
        }
        item {
            Box(Modifier.padding(top = Spacing.lg)) {
                MoreRow(
                    icon = Icons.Outlined.Settings,
                    title = "Settings",
                    subtitle = "Categories, rules, theme and data",
                    badge = null,
                    isFirst = true,
                    isLast = true,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: Int?,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    // Grouped list: theme `large` corners on the outer edges of the group, `extraSmall` inside.
    val outer = MaterialTheme.shapes.large
    val inner = MaterialTheme.shapes.extraSmall
    Surface(
        onClick = onClick,
        shape = outer.copy(
            topStart = if (isFirst) outer.topStart else inner.topStart,
            topEnd = if (isFirst) outer.topEnd else inner.topEnd,
            bottomEnd = if (isLast) outer.bottomEnd else inner.bottomEnd,
            bottomStart = if (isLast) outer.bottomStart else inner.bottomStart
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badge != null) {
                Badge { Text(badge.toString()) }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
