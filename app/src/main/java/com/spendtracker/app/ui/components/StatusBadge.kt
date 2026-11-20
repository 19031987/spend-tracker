package com.spendtracker.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Insights
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
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.util.Locale
import kotlin.math.abs

/**
 * Saved / Overspent status chip used in the hero summary.
 */
@Composable
fun StatusPill(
    isPositive: Boolean,
    amountMinor: Long,
    modifier: Modifier = Modifier
) {
    val finance = SpendTrackerTheme.finance
    val container = if (isPositive) finance.surplusContainer else finance.deficitContainer
    val content = if (isPositive) finance.onSurplusContainer else finance.onDeficitContainer
    val label = if (isPositive) "Left" else "Over"

    Surface(
        shape = CircleShape,
        color = container,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$label ${CurrencyUtils.formatMinor(amountMinor)}",
                style = MaterialTheme.typography.labelLarge,
                color = content,
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
    val finance = SpendTrackerTheme.finance
    val isPositive = percentageChange >= 0.0
    val containerColor = if (isPositive) finance.surplusContainer else finance.deficitContainer
    val contentColor = if (isPositive) finance.onSurplusContainer else finance.onDeficitContainer

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
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
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** Semantic tone of a [StatusChip]. Every tone has light and dark colours. */
enum class StatusTone { Positive, Negative, Warning, Info, Neutral }

/**
 * Small pill-shaped status label ("Paid", "Overdue", "Archived", "Ignored" …).
 * Colours come from [tone] so they work in dark mode:
 * - Positive: finance surplusContainer / onSurplusContainer (paid, on track)
 * - Negative: finance deficitContainer / onDeficitContainer (overdue, over budget)
 * - Warning: finance warningContainer / onWarningContainer (different amount, needs review)
 * - Info: secondaryContainer (upcoming, new)
 * - Neutral: surfaceVariant (archived, ignored)
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val finance = SpendTrackerTheme.finance
    val scheme = MaterialTheme.colorScheme
    val (container: Color, content: Color) = when (tone) {
        StatusTone.Positive -> finance.surplusContainer to finance.onSurplusContainer
        StatusTone.Negative -> finance.deficitContainer to finance.onDeficitContainer
        StatusTone.Warning -> finance.warningContainer to finance.onWarningContainer
        StatusTone.Info -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
