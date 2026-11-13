package com.spendtracker.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Use these instead of raw `.dp` literals for gaps and padding.
 * See `ui/components/README.md` for where each one is used.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

/** Layout constants shared by every full screen and bottom sheet. */
object ScreenPadding {
    /** Left/right padding of a screen's scrolling content. */
    val horizontal: Dp = 16.dp

    /** Padding above the first item of a screen's content. */
    val top: Dp = 16.dp

    /** Padding below the last item: leaves room for the FAB / bottom bar. */
    val bottom: Dp = 96.dp

    /** Vertical gap between items (cards) in a screen list: `Arrangement.spacedBy(listItemSpacing)`. */
    val listItemSpacing: Dp = 12.dp

    /** Inner padding of an [com.spendtracker.app.ui.components.AppCard]. */
    val cardPadding: Dp = 16.dp

    /** Left/right padding inside a ModalBottomSheet (see `SheetContainer`). */
    val sheetHorizontalPadding: Dp = 20.dp

    /** Bottom padding inside a ModalBottomSheet, below the action row. */
    val sheetBottomPadding: Dp = 24.dp

    /** Vertical gap between fields/rows inside a sheet. */
    val sheetItemSpacing: Dp = 16.dp

    /** Minimum height of every Primary/Secondary/Tonal/Destructive button. */
    val buttonHeight: Dp = 48.dp
}

/**
 * Standard `contentPadding` for a screen's LazyColumn / scrolling Column:
 * 16dp sides and top, 96dp bottom (FAB clearance).
 *
 * ```
 * LazyColumn(
 *     contentPadding = screenContentPadding(),
 *     verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
 * ) { ... }
 * ```
 */
fun screenContentPadding(): PaddingValues = PaddingValues(
    start = ScreenPadding.horizontal,
    end = ScreenPadding.horizontal,
    top = ScreenPadding.top,
    bottom = ScreenPadding.bottom
)
