package com.spendtracker.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Budget progress bar with an optional thin vertical marker at [marker] (0..1), e.g.
 * how far through the month we are. Shared by the Home budget card and the Budget screen.
 * Pass `marker = null` to hide the marker.
 */
@Composable
internal fun ProgressWithMarker(
    progress: Float,
    marker: Float?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val markerColor: Color = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        if (marker != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val x: Float = size.width * marker.coerceIn(0f, 1f)
                        drawLine(
                            color = markerColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            )
        }
    }
}
