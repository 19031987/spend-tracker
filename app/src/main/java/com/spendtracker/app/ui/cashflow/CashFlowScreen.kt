package com.spendtracker.app.ui.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.cashflow.BreakdownDimension
import com.spendtracker.app.domain.cashflow.BreakdownRow
import com.spendtracker.app.domain.cashflow.CashFlowMonth
import com.spendtracker.app.ui.components.CategoryEmojiBox
import com.spendtracker.app.ui.components.TransactionRowItem
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.CurrencyUtils
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun CashFlowRoute(
    viewModel: CashFlowViewModel,
    modifier: Modifier = Modifier,
    onOpenTransaction: (TransactionItem) -> Unit = {},
    onOpenCategory: (TransactionItem) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CashFlowScreen(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectMonth = viewModel::selectMonth,
        onRangeSelected = viewModel::setRange,
        onDimensionSelected = viewModel::setDimension,
        onShowIncomeChange = viewModel::setShowIncome,
        onOpenBreakdown = viewModel::openBreakdown,
        onCloseBreakdown = viewModel::closeBreakdown,
        onOpenTransaction = onOpenTransaction,
        onOpenCategory = onOpenCategory,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    state: CashFlowUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onRangeSelected: (Int) -> Unit,
    onDimensionSelected: (BreakdownDimension) -> Unit,
    onShowIncomeChange: (Boolean) -> Unit,
    onOpenBreakdown: (BreakdownRow) -> Unit,
    onCloseBreakdown: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit = {},
    onOpenCategory: (TransactionItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val report = state.report
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderRow(
                state = state,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onRangeSelected = onRangeSelected
            )
        }
        if (report == null || !state.hasData) {
            item { EmptyCard() }
        } else {
            item { StatTiles(report.selected) }
            item {
                ChartCard(
                    months = report.months,
                    selectedMonth = state.selectedMonth,
                    averageIncomeMinor = report.averageMonthlyIncomeMinor,
                    averageExpenseMinor = report.averageMonthlyExpenseMinor,
                    onSelectMonth = onSelectMonth
                )
            }
            item {
                BreakdownCard(
                    rows = if (state.showIncome) report.incomeBreakdown else report.expenseBreakdown,
                    dimension = state.dimension,
                    showIncome = state.showIncome,
                    onDimensionSelected = onDimensionSelected,
                    onShowIncomeChange = onShowIncomeChange,
                    onOpenBreakdown = onOpenBreakdown
                )
            }
        }
    }

    val drill = state.drillDown
    if (drill != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onCloseBreakdown, sheetState = sheetState) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CategoryEmojiBox(emoji = drill.emoji, colorHex = drill.colorHex)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = drill.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = monthTitle(state.selectedMonth) + " · " +
                            drill.transactions.size + if (drill.transactions.size == 1) " transaction" else " transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = CurrencyUtils.formatMinor(drill.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    color = if (state.showIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.onSurface
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(drill.transactions, key = { _, tx -> tx.id }) { index, tx ->
                    TransactionRowItem(
                        tx = tx,
                        onClick = { onOpenTransaction(tx) },
                        onCategoryClick = { onOpenCategory(tx) },
                        isFirst = index == 0,
                        isLast = index == drill.transactions.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    state: CashFlowUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRangeSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = monthTitle(state.selectedMonth),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        IconButton(onClick = onNextMonth, enabled = state.canGoNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 12).forEach { months ->
                FilterChip(
                    selected = state.rangeMonths == months,
                    onClick = { onRangeSelected(months) },
                    label = { Text("${months}M") }
                )
            }
        }
    }
}

@Composable
private fun StatTiles(selected: CashFlowMonth) {
    val finance = SpendTrackerTheme.finance
    val savedColor = if (selected.savingsMinor >= 0) finance.income else MaterialTheme.colorScheme.error
    val rate = selected.savingsRate
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            title = "Income",
            value = CurrencyUtils.formatMinor(selected.incomeMinor),
            valueColor = finance.income,
            caption = null,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            title = "Expenses",
            value = CurrencyUtils.formatMinor(selected.expenseMinor),
            valueColor = MaterialTheme.colorScheme.onSurface,
            caption = null,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            title = "Saved",
            value = CurrencyUtils.formatSignedMinor(selected.savingsMinor),
            valueColor = savedColor,
            caption = if (rate != null) "${(rate * 100).roundToInt()}% of income" else "No income",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    valueColor: Color,
    caption: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption ?: " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartCard(
    months: List<CashFlowMonth>,
    selectedMonth: YearMonth,
    averageIncomeMinor: Long,
    averageExpenseMinor: Long,
    onSelectMonth: (YearMonth) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Income vs expenses",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                LegendDot(color = SpendTrackerTheme.finance.income, label = "Income")
                Spacer(Modifier.width(12.dp))
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Expenses")
            }
            CashFlowBarChart(
                months = months,
                selectedMonth = selectedMonth,
                onSelectMonth = onSelectMonth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )
            Text(
                text = "Monthly average: ${CurrencyUtils.formatMinor(averageIncomeMinor)} in · " +
                    "${CurrencyUtils.formatMinor(averageExpenseMinor)} out",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CashFlowBarChart(
    months: List<CashFlowMonth>,
    selectedMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val incomeColor = SpendTrackerTheme.finance.income
    val expenseColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val selectedLabelColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val selectedLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = selectedLabelColor,
        fontWeight = FontWeight.Bold
    )
    val textMeasurer = rememberTextMeasurer()
    val currencySymbol = remember(Locale.getDefault()) { currencySymbolOf() }
    val maxValue: Long = months.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 0L
    val axis = remember(maxValue) { niceAxis(maxValue) }
    val axisWidth = 44.dp
    val bottomLabelHeight = 22.dp
    val topPad = 8.dp

    Canvas(
        modifier = modifier.pointerInput(months) {
            detectTapGestures { offset ->
                val left = axisWidth.toPx()
                val plotW = size.width - left
                if (months.isNotEmpty() && plotW > 0f && offset.x >= left) {
                    val index = ((offset.x - left) / (plotW / months.size)).toInt()
                        .coerceIn(0, months.size - 1)
                    onSelectMonth(months[index].month)
                }
            }
        }
    ) {
        val left = axisWidth.toPx()
        val top = topPad.toPx()
        val plotH = size.height - top - bottomLabelHeight.toPx()
        val plotW = size.width - left
        if (plotH <= 0f || plotW <= 0f || months.isEmpty()) return@Canvas

        // Gridlines and y labels.
        val steps = axis.ticks
        for (i in 0..steps) {
            val value = axis.step * i
            val y = top + plotH - (plotH * (value / axis.top)).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                alpha = if (i == 0) 1f else 0.6f
            )
            val label = compactAmount(value, currencySymbol)
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    left - layout.size.width - 6.dp.toPx(),
                    y - layout.size.height / 2f
                ),
                style = labelStyle
            )
        }

        // Bars.
        val slotW = plotW / months.size
        val barW = min(slotW * 0.3f, 16.dp.toPx())
        val gap = 2.dp.toPx()
        val radius = CornerRadius(min(barW / 2f, 4.dp.toPx()))
        months.forEachIndexed { index, m ->
            val isSelected = m.month == selectedMonth
            val alpha = if (isSelected) 1f else 0.45f
            val slotLeft = left + slotW * index
            val centre = slotLeft + slotW / 2f
            if (isSelected) {
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(slotLeft + 2.dp.toPx(), top),
                    size = Size(slotW - 4.dp.toPx(), plotH),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }
            val incomeH = barHeight(m.incomeMinor, axis.top, plotH)
            val expenseH = barHeight(m.expenseMinor, axis.top, plotH)
            if (incomeH > 0f) {
                drawRoundRect(
                    color = incomeColor.copy(alpha = alpha),
                    topLeft = Offset(centre - gap / 2f - barW, top + plotH - incomeH),
                    size = Size(barW, incomeH),
                    cornerRadius = radius
                )
            }
            if (expenseH > 0f) {
                drawRoundRect(
                    color = expenseColor.copy(alpha = alpha),
                    topLeft = Offset(centre + gap / 2f, top + plotH - expenseH),
                    size = Size(barW, expenseH),
                    cornerRadius = radius
                )
            }
            val initial = m.month.month.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val style = if (isSelected) selectedLabelStyle else labelStyle
            val layout = textMeasurer.measure(initial, style)
            drawText(
                textMeasurer = textMeasurer,
                text = initial,
                topLeft = Offset(
                    centre - layout.size.width / 2f,
                    top + plotH + (bottomLabelHeight.toPx() - layout.size.height) / 2f + 2.dp.toPx()
                ),
                style = style
            )
        }
    }
}

private fun barHeight(valueMinor: Long, axisTop: Double, plotH: Float): Float {
    if (valueMinor <= 0L || axisTop <= 0.0) return 0f
    return max(2f, (plotH * (valueMinor / axisTop)).toFloat())
}

private class Axis(val top: Double, val step: Double, val ticks: Int)

/** Round axis maximum (minor units) with 2 to 5 evenly spaced gridline steps. */
private fun niceAxis(maxMinor: Long): Axis {
    if (maxMinor <= 0L) return Axis(top = 10_000.0, step = 2_500.0, ticks = 4)
    val raw = maxMinor / 4.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val factor = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = factor * magnitude
    val ticks = max(1, ceil(maxMinor / step).toInt())
    return Axis(top = step * ticks, step = step, ticks = ticks)
}

private fun currencySymbolOf(): String =
    CurrencyUtils.formatMinor(0L).replace(Regex("[0-9.,\\s\\u00A0\\u202F]"), "")

private fun compactAmount(minor: Double, symbol: String): String {
    val major = abs(minor) / 100.0
    return when {
        major <= 0.0 -> "0"
        major >= 1_000_000.0 -> symbol + trimmed(major / 1_000_000.0) + "m"
        major >= 1_000.0 -> symbol + trimmed(major / 1_000.0) + "k"
        else -> symbol + major.roundToInt().toString()
    }
}

private fun trimmed(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownCard(
    rows: List<BreakdownRow>,
    dimension: BreakdownDimension,
    showIncome: Boolean,
    onDimensionSelected: (BreakdownDimension) -> Unit,
    onShowIncomeChange: (Boolean) -> Unit,
    onOpenBreakdown: (BreakdownRow) -> Unit
) {
    val dimensions = BreakdownDimension.values()
    val barColor = if (showIncome) SpendTrackerTheme.finance.income else MaterialTheme.colorScheme.primary
    val maxShare = rows.maxOfOrNull { it.share } ?: 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Breakdown",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                dimensions.forEachIndexed { index, d ->
                    SegmentedButton(
                        selected = dimension == d,
                        onClick = { onDimensionSelected(d) },
                        shape = SegmentedButtonDefaults.itemShape(index, dimensions.size),
                        icon = {}
                    ) { Text(dimensionLabel(d)) }
                }
            }
            TabRow(
                selectedTabIndex = if (showIncome) 1 else 0,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = !showIncome,
                    onClick = { onShowIncomeChange(false) },
                    text = { Text("Expenses") }
                )
                Tab(
                    selected = showIncome,
                    onClick = { onShowIncomeChange(true) },
                    text = { Text("Income") }
                )
            }
            if (rows.isEmpty()) {
                Text(
                    text = if (showIncome) "No income this month." else "No spending this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                )
            } else {
                Column {
                    rows.forEach { row ->
                        BreakdownRowItem(
                            row = row,
                            relative = if (maxShare > 0f) row.share / maxShare else 0f,
                            barColor = barColor,
                            onClick = { onOpenBreakdown(row) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRowItem(
    row: BreakdownRow,
    relative: Float,
    barColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryEmojiBox(emoji = row.emoji, colorHex = row.colorHex, size = 40.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(relative.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyUtils.formatMinor(row.amountMinor),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "${(row.share * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "📊", style = MaterialTheme.typography.displaySmall)
            Text(
                text = "No cash flow yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Add income and expenses to see how money moves in and out each month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun dimensionLabel(dimension: BreakdownDimension): String = when (dimension) {
    BreakdownDimension.CATEGORY -> "Category"
    BreakdownDimension.GROUP -> "Group"
    BreakdownDimension.MERCHANT -> "Merchant"
}

private fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
