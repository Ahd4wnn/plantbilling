package com.plantora.billing.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DonutSlice(val value: Float, val color: Color)

/**
 * Animated donut (ring) chart. Slices sweep in on first composition. The center
 * is a free content slot (e.g. net income). Empty data renders a soft track.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    strokeWidth: Dp = 22.dp,
    trackColor: Color = Color(0xFFE2E8F0),
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    var play by remember { mutableStateOf(false) }
    LaunchedEffect(slices) { play = true }
    val progress by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = tween(700),
        label = "donutSweep",
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )

            if (total > 0f) {
                var start = -90f
                slices.forEach { slice ->
                    val full = slice.value / total * 360f
                    val sweep = full * progress
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt),
                    )
                    start += full
                }
            }
        }
        center()
    }
}
