package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing

/*
 * App button set. All buttons use the default Material 3 pill shape, a 48dp minimum
 * height and the theme's `labelLarge` (no FontWeight override). Never pass a custom
 * shape or fontWeight to a button; pick the right component instead:
 *
 *   PrimaryButton          Save / Add / Confirm: the one main action of a screen or sheet
 *   SecondaryButton        Cancel / Back / less important alternative
 *   TonalButton            Medium emphasis (e.g. "Add money" inside a card)
 *   DestructiveButton      Confirming a delete/reset inside a confirm sheet or dialog
 *   DestructiveTextButton  Low-emphasis "Delete" link at the bottom of an edit sheet
 *   SheetActions           Cancel + Save row at the bottom of every sheet
 */

private fun Modifier.buttonSize(fullWidth: Boolean): Modifier {
    val sized = this.heightIn(min = ScreenPadding.buttonHeight)
    return if (fullWidth) sized.fillMaxWidth() else sized
}

private fun paddingFor(icon: ImageVector?): PaddingValues =
    if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding

@Composable
private fun RowScope.ButtonLabel(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Filled primary action. For add-new pass `leadingIcon = Icons.Filled.Add` and a label
 * like "Add payment" (never "+ Add …": the icon already is the plus).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.buttonSize(fullWidth),
        enabled = enabled,
        contentPadding = paddingFor(leadingIcon)
    ) { ButtonLabel(text, leadingIcon) }
}

/** Outlined secondary action (Cancel, Back, alternatives). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.buttonSize(fullWidth),
        enabled = enabled,
        contentPadding = paddingFor(leadingIcon)
    ) { ButtonLabel(text, leadingIcon) }
}

/** Filled-tonal medium-emphasis action. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.buttonSize(fullWidth),
        enabled = enabled,
        contentPadding = paddingFor(leadingIcon)
    ) { ButtonLabel(text, leadingIcon) }
}

/** Filled destructive action (errorContainer / onErrorContainer). Use to confirm a delete. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.buttonSize(fullWidth),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        contentPadding = paddingFor(leadingIcon)
    ) { ButtonLabel(text, leadingIcon) }
}

/**
 * Low-emphasis destructive action in error colour, e.g. a "Delete" link below an edit
 * sheet's [SheetActions] (pass `leadingIcon = Icons.Outlined.Delete`).
 */
@Composable
fun DestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ScreenPadding.buttonHeight),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) { ButtonLabel(text, leadingIcon) }
}

/**
 * Standard bottom row of every sheet / form: [SecondaryButton] (dismiss) on the left,
 * [PrimaryButton] (or [DestructiveButton] when [destructive]) on the right, equal widths,
 * 12dp apart.
 */
@Composable
fun SheetActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true,
    destructive: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryButton(
            text = dismissLabel,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        if (destructive) {
            DestructiveButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f)
            )
        } else {
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
