package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spendtracker.app.ui.theme.ScreenPadding

/**
 * Body of every `ModalBottomSheet`: full width, IME + navigation-bar insets,
 * 20dp side padding, 24dp bottom padding, 16dp between children, scrollable by default.
 *
 * ```
 * ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
 *     SheetContainer {
 *         SheetHeader("Edit payment", onClose = onDismiss)
 *         OutlinedTextField(...)
 *         SheetActions(confirmLabel = "Save", onConfirm = ::save, onDismiss = onDismiss)
 *     }
 * }
 * ```
 *
 * Pass `scrollable = false` when the content contains its own LazyColumn (nested
 * vertical scrolling of a LazyColumn inside verticalScroll crashes).
 */
@Composable
fun SheetContainer(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .imePadding()
        .navigationBarsPadding()
    val scrolled = if (scrollable) base.verticalScroll(rememberScrollState()) else base
    Column(
        modifier = scrolled.padding(
            start = ScreenPadding.sheetHorizontalPadding,
            end = ScreenPadding.sheetHorizontalPadding,
            bottom = ScreenPadding.sheetBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(ScreenPadding.sheetItemSpacing),
        content = content
    )
}

/**
 * Title row of a sheet: `titleLarge` (theme weight, no overrides), optional `bodyMedium`
 * subtitle, optional close (X) icon button on the right.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
    }
}
