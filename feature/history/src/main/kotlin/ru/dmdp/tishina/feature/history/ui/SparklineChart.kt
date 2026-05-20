package ru.dmdp.tishina.feature.history.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.designsystem.theme.LocalSplLevelPalette
import ru.dmdp.tishina.core.designsystem.theme.levelToSplColor

const val SparklineChartTestTag: String = "history_sparkline"

/** Visual range for the sparkline — matches the SPL gauge so colors stay coherent. */
private const val CHART_MIN_DB = 30f
private const val CHART_MAX_DB = 110f

/**
 * Compact ≤20-point polyline of dB values for the History card mini-chart.
 *
 * Differs from the live [ru.dmdp.tishina.feature.measure.ui.SplLineChart]:
 *  - inputs are raw `Float`s (no timestamp axis — equidistant on X);
 *  - no track line under the polyline;
 *  - line color follows the average dB so the card glances cohere with the
 *    "avg dB" digit shown next to it.
 *
 * Empty / single-point inputs draw nothing (keeps the card visually quiet when
 * the historical record happens to have no sparkline preview persisted).
 */
@Composable
fun SparklineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 32.dp,
) {
    val palette = LocalSplLevelPalette.current
    val color = if (points.isEmpty()) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        levelToSplColor(points.average().toFloat(), palette)
    }
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .testTag(SparklineChartTestTag),
    ) {
        if (points.size < 2) return@Canvas
        drawPolyline(points, color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolyline(
    points: List<Float>,
    color: Color,
) {
    val range = CHART_MAX_DB - CHART_MIN_DB
    val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
    val path = Path()
    points.forEachIndexed { index, value ->
        val clamped = value.coerceIn(CHART_MIN_DB, CHART_MAX_DB)
        val yRatio = (clamped - CHART_MIN_DB) / range
        val x = index * stepX
        // Y is inverted: high dB → top of canvas.
        val y = size.height - yRatio * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    // Final point indicator helps the eye latch onto the most recent value.
    val lastX = (points.size - 1) * stepX
    val lastClamped = points.last().coerceIn(CHART_MIN_DB, CHART_MAX_DB)
    val lastY = size.height - ((lastClamped - CHART_MIN_DB) / range) * size.height
    drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
}
