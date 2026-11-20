package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.theme.ScreenPadding

/**
 * The one card used on every screen: `shapes.large` (24dp), `surfaceContainerLow`,
 * no elevation, 16dp inner padding, full width.
 *
 * @param onClick makes the whole card clickable (ripple) when non-null.
 * @param containerColor override only for emphasis (e.g. `secondaryContainer` banners).
 * @param contentColor defaults to the matching "on" colour of [containerColor].
 * @param contentPadding inner padding; pass `0.dp` when the content (e.g. a list of
 *   full-bleed rows) handles its own padding.
 * @param verticalSpacing gap between children of the inner Column (default 0dp).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = Color.Unspecified,
    contentPadding: Dp = ScreenPadding.cardPadding,
    verticalSpacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContent: Color = contentColor.takeOrElse {
        contentColorFor(containerColor).takeOrElse { MaterialTheme.colorScheme.onSurface }
    }
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = resolvedContent
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val shape = MaterialTheme.shapes.large
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = inner
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = inner
        )
    }
}
