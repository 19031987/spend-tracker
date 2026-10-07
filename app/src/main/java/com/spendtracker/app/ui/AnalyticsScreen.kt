package com.spendtracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBar
import com.spendtracker.app.domain.Period
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.max

fun formatMoney(minor: Long): String =
    NumberFormat.getCurrencyInstance().format(BigDecimal.valueOf(minor).movePointLeft(2))

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsContent(state, viewModel::onPeriodSelected, contentPadding, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsContent(
    state: AnalyticsUiState,
    onPeriodSelected: (Period) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val periods = Period.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { onPeriodSelected(p) },
                        shape = SegmentedButtonDefaults.itemShape(index, periods.size)
                    ) { Text(p.label) }
                }
            }
        }

        item { SummaryCard(state) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This ${state.period.label.lowercase()} vs previous",
                        style = MaterialTheme.typography.titleMedium
                    )
                    SpendBarChart(bars = state.bars, modifier = Modifier.fillMaxWidth())
                    ChartLegend()
                }
            }
        }

        item { Text("Top categories", style = MaterialTheme.typography.titleMedium) }

        if (state.categories.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No spending recorded in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.categories, key = { it.category.name }) { CategoryRow(it) }
        }

        state.error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SummaryCard(state: AnalyticsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Total spend", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatMoney(state.currentTotalMinor),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1
                )
                Text(
                    "Previous: ${formatMoney(state.previousTotalMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DeltaBadge(state.deltaPercent, state.currentTotalMinor)
        }
    }
}

/** More spending is bad (error color), less spending is good (tertiary). */
@Composable
fun DeltaBadge(deltaPercent: Double?, currentTotal: Long, modifier: Modifier = Modifier) {
    val (text, container, content) = when {
        deltaPercent == null && currentTotal > 0 -> Triple(
            "New", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
        )
        deltaPercent == null || abs(deltaPercent) < 0.05 -> Triple(
            "0.0%", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant
        )
        deltaPercent > 0 -> Triple(
            "+%.1f%%".format(deltaPercent),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            "%.1f%%".format(deltaPercent),
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

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.colorScheme.primary, "Current")
        LegendDot(MaterialTheme.colorScheme.outlineVariant, "Previous")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SpendBarChart(bars: List<ChartBar>, modifier: Modifier = Modifier) {
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val maxValue = (bars.maxOfOrNull { max(it.current, it.previous) } ?: 0L).coerceAtLeast(1L)
    val description = "Bar chart comparing ${bars.size} periods. " +
        "Current total ${formatMoney(bars.sumOf { it.current })}, " +
        "previous total ${formatMoney(bars.sumOf { it.previous })}."

    Canvas(
        modifier = modifier
            .height(220.dp)
            .semantics { contentDescription = description }
    ) {
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - labelHeight

        repeat(5) { i ->
            val y = chartHeight * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (bars.isEmpty()) return@Canvas

        val slot = size.width / bars.size
        val barWidth = (slot * 0.32f).coerceAtMost(18.dp.toPx())
        val gap = 2.dp.toPx()
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        bars.forEachIndexed { i, bar ->
            val centerX = slot * i + slot / 2f
            val prevH = chartHeight * (bar.previous.toFloat() / maxValue)
            val curH = chartHeight * (bar.current.toFloat() / maxValue)

            drawRoundRect(
                color = previousColor,
                topLeft = Offset(centerX - barWidth - gap / 2f, chartHeight - prevH),
                size = Size(barWidth, prevH),
                cornerRadius = radius
            )
            drawRoundRect(
                color = currentColor,
                topLeft = Offset(centerX + gap / 2f, chartHeight - curH),
                size = Size(barWidth, curH),
                cornerRadius = radius
            )

            val layout = measurer.measure(bar.label, labelStyle, maxLines = 1)
            drawText(
                layout,
                topLeft = Offset(centerX - layout.size.width / 2f, chartHeight + 4.dp.toPx())
            )
        }
    }
}

@Composable
private fun CategoryRow(share: CategoryShare) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                share.category.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "${formatMoney(share.amountMinor)} · ${"%.0f".format(share.percent)}%",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
        LinearProgressIndicator(
            progress = { (share.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
