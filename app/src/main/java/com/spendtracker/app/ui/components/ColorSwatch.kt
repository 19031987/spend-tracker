package com.spendtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.util.ColorUtils
import java.util.Locale

/**
 * A selectable colour circle for colour pickers. Announced to TalkBack as a radio option with
 * the colour's name and hex ("Pink, #F9A8D4") and its selected state; touch target is 48dp+.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSwatch(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val description = colorSwatchDescription(hex)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(ColorUtils.parseColorHex(hex))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            // selectable() exposes the selected state (TalkBack: "selected"/"not selected").
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {}
}

/** Spoken label for a colour swatch, e.g. "Pink, #F9A8D4". */
fun colorSwatchDescription(hex: String): String {
    val clean = hex.trim().removePrefix("#").uppercase(Locale.ROOT)
    return "${colorNameFor(hex)}, #$clean"
}

/** Rough human name for a colour (hue family, "Light"/"Dark" by lightness), e.g. "#F9A8D4" → "Pink". */
fun colorNameFor(hex: String): String {
    val clean = hex.trim().removePrefix("#")
    val rgb = clean.takeLast(6).toLongOrNull(16) ?: return "Colour"
    if (clean.length !in setOf(6, 8)) return "Colour"
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta < 0.08f) {
        return when {
            lightness > 0.9f -> "White"
            lightness > 0.6f -> "Light grey"
            lightness > 0.25f -> "Grey"
            else -> "Black"
        }
    }
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val family = when {
        hue < 15f || hue >= 345f -> "Red"
        hue < 45f -> "Orange"
        hue < 70f -> "Yellow"
        hue < 160f -> "Green"
        hue < 190f -> "Teal"
        hue < 250f -> "Blue"
        hue < 275f -> "Indigo"
        hue < 315f -> "Purple"
        else -> "Pink"
    }
    return when {
        lightness < 0.3f -> "Dark ${family.lowercase(Locale.ROOT)}"
        else -> family
    }
}
