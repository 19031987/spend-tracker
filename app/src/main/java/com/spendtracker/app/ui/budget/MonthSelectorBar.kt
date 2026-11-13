package com.spendtracker.app.ui.budget

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Month switcher used at the top of the Budget and Cash flow screens:
 * `< Nov 2026 >` with the label centred between the arrows, plus optional [trailing]
 * content (e.g. range chips) on the right.
 */
@Composable
internal fun MonthSelectorBar(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
        if (trailing != null) {
            trailing()
        }
    }
}
