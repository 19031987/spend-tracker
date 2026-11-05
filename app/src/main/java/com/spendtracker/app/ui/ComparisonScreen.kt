package com.spendtracker.app.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.app.ui.components.BaselineBanner
import com.spendtracker.app.ui.components.ComparisonBadge
import com.spendtracker.app.ui.util.ColorUtils
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Top-level stateful composable adhering to MVVM with StateFlow collection.
 */
@Composable
fun ComparisonScreen(
    viewModel: ComparisonViewModel = viewModel(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ComparisonContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onSelectMode = viewModel::selectMode,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

/**
 * Stateless presentation composable rendering the Material 3 layout.
 */
@Composable
fun ComparisonContent(
    state: ComparisonUiState,
    onSelectPeriod: (ComparisonPeriod) -> Unit,
    onSelectMode: (ComparisonMode) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Period Selector (Week / Month / Year)
        item {
            PeriodSelector(
                selected = state.selectedPeriod,
                onSelect = onSelectPeriod
            )
        }

        // Section 1b: Mode Selector (By Category / By Time)
        item {
            ComparisonModeSelector(
                selected = state.comparisonMode,
                onSelect = onSelectMode
            )
        }

        // Loading Indicator Overlay or Content Cards
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            // Section 2: Overview Summary Comparison Card
            item {
                SummaryComparisonCard(state = state)
            }

            // Section 3: Breakdown Header & Subtitle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val headerTitle = if (state.comparisonMode == ComparisonMode.CATEGORIES) {
                        "Category Comparison"
                    } else {
                        "Interval Breakdown"
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Legend: Current vs Previous
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            label = "Current"
                        )
                        LegendIndicator(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            label = "Previous"
                        )
                    }
                }
            }

            // Section 4: Scrollable Category or Interval Breakdown Items
            items(state.breakdownItems, key = { it.label }) { item ->
                val maxVal = max(
                    1.0,
                    state.breakdownItems.maxOfOrNull { max(it.currentValue, it.previousValue) } ?: 1.0
                )
                ComparisonBreakdownCard(item = item, maxValue = maxVal)
            }
        }
    }
}

/**
 * Segmented button row providing mode switching between Category Comparison and Time Interval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonModeSelector(
    selected: ComparisonMode,
    onSelect: (ComparisonMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = ComparisonMode.values()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = modes.size
                    ),
                    icon = {}
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected == mode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Segmented button row providing instant switching between Week, Month, and Year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    selected: ComparisonPeriod,
    onSelect: (ComparisonPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val periods = ComparisonPeriod.values()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            periods.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = selected == period,
                    onClick = { onSelect(period) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = periods.size
                    ),
                    icon = {}
                ) {
                    Text(
                        text = period.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == period) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Overview card with elevated surface, bold metric typography, and contextual diff pill.
 */
@Composable
fun SummaryComparisonCard(
    state: ComparisonUiState,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isInitialPeriod) {
                // Initial period onboarding banner replacing comparison badge
                OnboardingBaselineBanner(message = state.onboardingMessage)
            } else {
                // Header: Period Label & Comparison Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.selectedPeriod.label} Overview",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Comparison Pill / Badge
                    ComparisonBadge(
                        percentageChange = state.percentageChange,
                        isIncrease = state.isIncrease,
                        referenceContext = state.selectedPeriod.referenceContext
                    )
                }
            }

            // Current Total Display
            Text(
                text = formatCurrency(state.currentPeriodTotal),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Previous Period Reference Metric (guarded during initial period)
            if (!state.isInitialPeriod) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Previous ${state.selectedPeriod.label.lowercase()}: ${formatCurrency(state.previousPeriodTotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val netDifference = state.currentPeriodTotal - state.previousPeriodTotal
                    val prefix = if (netDifference >= 0) "+" else ""
                    Text(
                        text = "$prefix${formatCurrency(netDifference)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (state.isIncrease) {
                            MaterialTheme.colorScheme.error
                        } else {
                            successTextColor()
                        }
                    )
                }
            } else {
                Text(
                    text = "Historical reference will populate once your first week/month baseline is recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Onboarding baseline banner shown during initial run (< 7 or < 30 days) before historical comparisons are valid.
 */
@Composable
fun OnboardingBaselineBanner(
    message: String = "Building your baseline: comparisons will appear after your first week/month",
    modifier: Modifier = Modifier
) {
    BaselineBanner(message = message, modifier = modifier)
}

/**
 * High-contrast comparison pill badge with trending icon and percentage change.
 */
@Composable
fun ComparisonBadge(
    percentageChange: Double,
    isIncrease: Boolean = percentageChange >= 0.0,
    referenceContext: String = "",
    modifier: Modifier = Modifier
) {
    com.spendtracker.app.ui.components.ComparisonBadge(
        percentageChange = percentageChange,
        isIncrease = isIncrease,
        referenceContext = referenceContext,
        modifier = modifier
    )
}

/**
 * Renders an individual sub-unit comparison item with dual LinearProgressIndicators.
 */
@Composable
fun ComparisonBreakdownCard(
    item: DataPoint,
    maxValue: Double,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Unit Label & Monetary Values + Percentage Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(item.currentValue),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "prev: ${formatCurrency(item.previousValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Delta badge (fixed minimum width so currency column stays aligned across rows)
                    val diff = item.difference
                    val pct = item.percentageChange
                    val isUp = diff >= 0
                    val badgeBg = if (isUp) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else successContainerColor().copy(alpha = 0.7f)
                    val badgeColor = if (isUp) MaterialTheme.colorScheme.error else successTextColor()

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 52.dp)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${if (isUp) "+" else ""}${String.format(Locale.getDefault(), "%.0f", pct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Dual Progress Comparison Track
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Current Period Bar
                val currentFraction = (item.currentValue / maxValue).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { currentFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Previous Period Bar
                val previousFraction = (item.previousValue / maxValue).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { previousFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Small legend helper with color circle and label.
 */
@Composable
private fun LegendIndicator(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun successContainerColor(): Color = SpendTrackerTheme.finance.surplusContainer

@Composable
private fun successTextColor(): Color = SpendTrackerTheme.finance.onSurplusContainer

private fun formatCurrency(amount: Double): String =
    CurrencyUtils.formatMajor(amount)

// =============================================================================
// PREVIEWS
// =============================================================================

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun ComparisonScreenPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            ComparisonContent(
                state = ComparisonUiState(
                    selectedPeriod = ComparisonPeriod.WEEK,
                    currentPeriodTotal = 725.00,
                    previousPeriodTotal = 688.00,
                    percentageChange = 5.38,
                    isIncrease = true,
                    breakdownItems = listOf(
                        DataPoint("Mon", 42.50, 35.00),
                        DataPoint("Tue", 68.20, 75.40),
                        DataPoint("Wed", 115.00, 92.10),
                        DataPoint("Thu", 54.30, 60.00),
                        DataPoint("Fri", 145.80, 130.50),
                        DataPoint("Sat", 210.00, 185.00),
                        DataPoint("Sun", 89.20, 110.00)
                    ),
                    isLoading = false
                ),
                onSelectPeriod = {}
            )
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComparisonScreenPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface {
            ComparisonContent(
                state = ComparisonUiState(
                    selectedPeriod = ComparisonPeriod.MONTH,
                    currentPeriodTotal = 1826.30,
                    previousPeriodTotal = 1750.70,
                    percentageChange = 4.32,
                    isIncrease = true,
                    breakdownItems = listOf(
                        DataPoint("Week 1", 420.50, 380.00),
                        DataPoint("Week 2", 530.00, 610.20),
                        DataPoint("Week 3", 390.80, 340.50),
                        DataPoint("Week 4", 485.00, 420.00)
                    ),
                    isLoading = false
                ),
                onSelectPeriod = {}
            )
        }
    }
}
