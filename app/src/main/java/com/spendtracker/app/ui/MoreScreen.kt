package com.spendtracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
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
            Box(Modifier.padding(top = 16.dp)) {
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
    val big = 20.dp
    val small = 4.dp
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (isFirst) big else small,
            topEnd = if (isFirst) big else small,
            bottomStart = if (isLast) big else small,
            bottomEnd = if (isLast) big else small
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
