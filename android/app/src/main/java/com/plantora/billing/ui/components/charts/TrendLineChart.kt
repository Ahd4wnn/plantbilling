package com.plantora.billing.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Smooth-ish sales trend line with a soft gradient fill. `values` are daily
 * totals; the line animates in by revealing left→right.
 */
@Composable
fun TrendLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = Color(0xFF2E7D46),
) {
    var play by remember { mutableStateOf(false) }
    LaunchedEffect(values) { play = true }
    val reveal by animateFloatAsState(if (play) 1f else 0f, tween(800), label = "trend")

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
        val padBottom = 6f
        val usableH = size.height - padBottom
        val stepX = size.width / (values.size - 1)
        val count = (1 + (values.size - 1) * reveal).toInt().coerceIn(1, values.size)

        fun pointAt(i: Int): Offset {
            val x = stepX * i
            val y = usableH - (values[i] / maxV) * (usableH - 4f) + 4f
            return Offset(x, y)
        }

        val line = Path().apply {
            moveTo(0f, pointAt(0).y)
            for (i in 1 until count) lineTo(pointAt(i).x, pointAt(i).y)
        }
        // Gradient fill under the line.
        val fill = Path().apply {
            addPath(line)
            lineTo(stepX * (count - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
            ),
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = 6f))
        // Dot at the latest revealed point.
        if (count >= 1) drawCircle(lineColor, radius = 7f, center = pointAt(count - 1))
    }
}
