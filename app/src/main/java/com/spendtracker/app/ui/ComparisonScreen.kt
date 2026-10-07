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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Supported comparison time horizons for evaluating period-over-period spend.
 */
enum class ComparisonPeriod(
    val label: String,
    val referenceContext: String
) {
    WEEK(label = "Week", referenceContext = "vs last week"),
    MONTH(label = "Month", referenceContext = "vs last month"),
    YEAR(label = "Year", referenceContext = "vs last year")
}

/**
 * Represents a discrete sub-interval comparison point (e.g. Day, Week of Month, Month of Year).
 */
data class DataPoint(
    val label: String,
    val currentValue: Double,
    val previousValue: Double
) {
    val difference: Double
        get() = currentValue - previousValue

    val percentageChange: Double
        get() = when {
            previousValue > 0.0 -> ((currentValue - previousValue) / previousValue) * 100.0
            currentValue > 0.0 -> 100.0
            else -> 0.0
        }
}

/**
 * Complete UI state for the period comparison screen.
 */
data class ComparisonUiState(
    val selectedPeriod: ComparisonPeriod = ComparisonPeriod.WEEK,
    val currentPeriodTotal: Double = 0.0,
    val previousPeriodTotal: Double = 0.0,
    val percentageChange: Double = 0.0,
    val isIncrease: Boolean = false,
    val breakdownItems: List<DataPoint> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * ViewModel managing reactive calculation and period switching.
 */
class ComparisonViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState(isLoading = true))
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    init {
        selectPeriod(ComparisonPeriod.WEEK)
    }

    fun selectPeriod(period: ComparisonPeriod) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedPeriod = period) }

            // Compute data breakdown for the selected interval
            val breakdown = generateDataForPeriod(period)
            val currentTotal = breakdown.sumOf { it.currentValue }
            val previousTotal = breakdown.sumOf { it.previousValue }

            val pctChange = when {
                previousTotal > 0.0 -> ((currentTotal - previousTotal) / previousTotal) * 100.0
                currentTotal > 0.0 -> 100.0
                else -> 0.0
            }

            _uiState.update {
                it.copy(
                    selectedPeriod = period,
                    currentPeriodTotal = currentTotal,
                    previousPeriodTotal = previousTotal,
                    percentageChange = pctChange,
                    isIncrease = currentTotal >= previousTotal,
                    breakdownItems = breakdown,
                    isLoading = false
                )
            }
        }
    }

    private fun generateDataForPeriod(period: ComparisonPeriod): List<DataPoint> = when (period) {
        ComparisonPeriod.WEEK -> listOf(
            DataPoint(label = "Mon", currentValue = 42.50, previousValue = 35.00),
            DataPoint(label = "Tue", currentValue = 68.20, previousValue = 75.40),
            DataPoint(label = "Wed", currentValue = 115.00, previousValue = 92.10),
            DataPoint(label = "Thu", currentValue = 54.30, previousValue = 60.00),
            DataPoint(label = "Fri", currentValue = 145.80, previousValue = 130.50),
            DataPoint(label = "Sat", currentValue = 210.00, previousValue = 185.00),
            DataPoint(label = "Sun", currentValue = 89.20, previousValue = 110.00)
        )
        ComparisonPeriod.MONTH -> listOf(
            DataPoint(label = "Week 1", currentValue = 420.50, previousValue = 380.00),
            DataPoint(label = "Week 2", currentValue = 530.00, previousValue = 610.20),
            DataPoint(label = "Week 3", currentValue = 390.80, previousValue = 340.50),
            DataPoint(label = "Week 4", currentValue = 485.00, previousValue = 420.00)
        )
        ComparisonPeriod.YEAR -> listOf(
            DataPoint(label = "Jan", currentValue = 1850.00, previousValue = 1620.00),
            DataPoint(label = "Feb", currentValue = 1720.50, previousValue = 1800.00),
            DataPoint(label = "Mar", currentValue = 2100.00, previousValue = 1950.00),
            DataPoint(label = "Apr", currentValue = 1940.20, previousValue = 2050.00),
            DataPoint(label = "May", currentValue = 2250.00, previousValue = 1890.00),
            DataPoint(label = "Jun", currentValue = 2400.00, previousValue = 2100.00),
            DataPoint(label = "Jul", currentValue = 2150.00, previousValue = 2300.00),
            DataPoint(label = "Aug", currentValue = 1980.00, previousValue = 1850.00),
            DataPoint(label = "Sep", currentValue = 2050.00, previousValue = 1920.00),
            DataPoint(label = "Oct", currentValue = 2310.00, previousValue = 2180.00),
            DataPoint(label = "Nov", currentValue = 2500.00, previousValue = 2400.00),
            DataPoint(label = "Dec", currentValue = 3100.00, previousValue = 2850.00)
        )
    }
}

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
        // Section 1: Centered M3 SingleChoiceSegmentedButtonRow
        item {
            PeriodSelector(
                selected = state.selectedPeriod,
                onSelect = onSelectPeriod
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

            // Section 3: Interval Breakdown Header & Subtitle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interval Breakdown",
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

            // Section 4: Scrollable Interval Breakdown Items
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
                    )
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
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Period Label & Comparison Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.selectedPeriod.label} Overview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Comparison Pill / Badge
                ComparisonBadge(
                    percentageChange = state.percentageChange,
                    isIncrease = state.isIncrease,
                    referenceContext = state.selectedPeriod.referenceContext
                )
            }

            // Current Total Display
            Text(
                text = formatCurrency(state.currentPeriodTotal),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Previous Period Reference Metric
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
        }
    }
}

/**
 * High-contrast comparison pill badge with trending icon and percentage change.
 */
@Composable
fun ComparisonBadge(
    percentageChange: Double,
    isIncrease: Boolean,
    referenceContext: String,
    modifier: Modifier = Modifier
) {
    // In expense tracking: Spending increase is typically red/warning, decrease is green/saving
    // Following specification: arrow-up + green or arrow-down + red based on change value
    val isPositive = percentageChange >= 0.0
    val containerColor = if (isPositive) {
        successContainerColor()
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isPositive) {
        successTextColor()
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

            Text(
                text = referenceContext,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
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
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Unit Label & Monetary Values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatCurrency(item.currentValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatCurrency(item.previousValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

/**
 * Accessible green container color for success/positive metric indication.
 */
@Composable
private fun successContainerColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF1B3824) else Color(0xFFE8F5E9)

/**
 * Accessible green text color for success/positive metric indication.
 */
@Composable
private fun successTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

/**
 * Localized currency formatter.
 */
private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    return formatter.format(amount)
}

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
