package com.spendtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.app.ui.util.ColorUtils

/**
 * Category avatar: the category emoji on a soft tint of its colour.
 * Circular by default to read as a Material list-item leading avatar.
 */
@Composable
fun CategoryEmojiBox(
    emoji: String,
    colorHex: String?,
    size: Dp = 44.dp,
    emojiSize: TextUnit = 20.sp,
    cornerRadius: Dp? = null,
    modifier: Modifier = Modifier
) {
    val shape = cornerRadius?.let { RoundedCornerShape(it) } ?: CircleShape
    val tint = ColorUtils.parseColorHex(colorHex ?: "#FED7AA")
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = emojiSize)
    }
}

/**
 * Compact, tappable category chip shown under a transaction's merchant name.
 */
@Composable
fun CategoryPill(
    categoryName: String,
    emoji: String,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
        modifier = modifier
            .height(24.dp)
            .clip(CircleShape)
            .then(clickableModifier)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = if (showChevron) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$emoji $categoryName",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (showChevron) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Change category",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
