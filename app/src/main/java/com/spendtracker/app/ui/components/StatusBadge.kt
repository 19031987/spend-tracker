package com.spendtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.app.ui.util.ColorUtils
import com.spendtracker.app.ui.util.CurrencyUtils
import java.util.Locale
import kotlin.math.abs

/**
 * Saved / Deficit status pill used in Hero and summary cards.
 */
@Composable
fun StatusPill(
    isPositive: Boolean,
    amountMinor: Long,
    modifier: Modifier = Modifier
) {
    val pillBgColor = if (isPositive) ColorUtils.SurplusContainerLight else ColorUtils.DeficitContainerLight
    val pillTextColor = if (isPositive) ColorUtils.SurplusTextLight else ColorUtils.DeficitTextLight
    val pillLabel = if (isPositive) "Saved: +" else "Deficit: -"

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = pillBgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isPositive) "💰" else "⚠️",
                fontSize = 12.sp
            )
            Text(
                text = "$pillLabel${CurrencyUtils.formatMinor(amountMinor)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = pillTextColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * High-contrast trend badge for Analytics delta comparisons.
 */
@Composable
fun TrendBadge(
    deltaPercent: Double?,
    currentTotal: Long = 0L,
    isInitialPeriod: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isInitialPeriod) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "Building baseline" }
        ) {
            Text(
                "Baseline",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        return
    }
    val (text, container, content) = when {
        deltaPercent == null && currentTotal > 0 -> Triple(
            "New", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
        )
        deltaPercent == null || abs(deltaPercent) < 0.05 -> Triple(
            "0.0%", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant
        )
        deltaPercent > 0 -> Triple(
            "+%.1f%%".format(Locale.getDefault(), deltaPercent),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            "%.1f%%".format(Locale.getDefault(), deltaPercent),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Change versus previous period: $text" }
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * High-contrast comparison badge with trending icon and percentage change.
 */
@Composable
fun ComparisonBadge(
    percentageChange: Double,
    isIncrease: Boolean = percentageChange >= 0.0,
    referenceContext: String = "",
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isPositive = percentageChange >= 0.0
    val containerColor = if (isPositive) {
        if (isDark) ColorUtils.SurplusContainerDark else ColorUtils.SurplusContainerLight
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isPositive) {
        if (isDark) ColorUtils.SurplusTextDark else ColorUtils.SurplusTextLight
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            val formattedPercent = String.format(Locale.getDefault(), "%.1f", abs(percentageChange))
            val sign = if (isPositive) "+" else "-"
            Text(
                text = "$sign$formattedPercent%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            if (referenceContext.isNotEmpty()) {
                Text(
                    text = referenceContext,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Onboarding banner displayed when building historical baseline.
 */
@Composable
fun BaselineBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌱", fontSize = 13.sp)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
